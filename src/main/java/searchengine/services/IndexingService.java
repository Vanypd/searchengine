package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteProps;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.UrlDto;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.concurrency.implementation.ForkJoinPoolManager;
import searchengine.services.utils.URLParser;
import searchengine.services.web.html.HTMLManager;
import searchengine.services.web.html.Lemmatizator;
import searchengine.services.web.scraping.ContentExtractorAction;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IndexingService extends DefaultService {

    private final SitesList sitesList;
    private final ForkJoinPoolManager forkJoinPoolManager;
    private final Lemmatizator lemmatizator;
    private boolean isIndexing = false;


    // CONSTRUCTORS //


    @Autowired
    public IndexingService(SitesList sitesList, RepositoryManager repositoryManager,
                           ForkJoinPoolManager forkJoinPoolManager, Lemmatizator lemmatizator) {
        super(repositoryManager);
        this.sitesList = sitesList;
        this.forkJoinPoolManager = forkJoinPoolManager;
        this.lemmatizator = lemmatizator;
    }


    // API METHODS //


    public ResponseEntity<IndexingResponse> startIndexing() {
        logger.info("Вызван запуск полной индексации");

        if (isIndexing) {
            return getFailedResponse(new IndexingErrorResponse("Индексация уже запущена"));
        }

        isIndexing = true;
        siteRepository.deleteAll();
        pageRepository.deleteAll();

        AtomicInteger errorsCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();

        sitesList.getSites().forEach(site -> tasks.add(() -> {
            Site siteEntity = mapSiteEntityFromSiteList(site);
            ContentExtractorAction action = new ContentExtractorAction(repositoryManager, siteEntity.getUrl());

            if (hasErrorsDuringInvocation(action, siteEntity)) {
                errorsCount.getAndIncrement();
                return;
            }

            siteEntity.setIndexStatus(IndexStatus.INDEXED);
            repositoryManager.executeTransaction(() ->
                siteRepository.save(siteEntity)
            );
        }));

        forkJoinPoolManager.executeAwait(tasks);
        isIndexing = false;

        if (errorsCount.get() != 0) {
            return getFailedResponse(
                    new IndexingErrorResponse("Индексация закончена с ошибкой на " + errorsCount.get() + " сайтах")
            );
        }

        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> stopIndexing() {
        logger.info("Вызвана остановка индексации");

        if (!isIndexing) {
            return getFailedResponse(new IndexingErrorResponse("Индексация не запущена"));
        }

        ContentExtractorAction.stop();

        repositoryManager.executeTransaction(() -> {
            String errorMessage = "Индексация остановлена пользователем";
            siteRepository.updateStatusAndErrorByStatus(IndexStatus.INDEXING, IndexStatus.FAILED, errorMessage);
        });

        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> indexPage(UrlDto urlDto) {
        logger.info("Вызвана индексация отдельной страницы: {}", urlDto.getUrl());
        Site siteEntity = getSiteEntityFromUrl(urlDto.getUrl());

        if (siteEntity == null) {
            String errorMessage = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
            return getFailedResponse(new IndexingErrorResponse(errorMessage));
        }

        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteRepository.save(siteEntity);

        URL url = URLParser.mapStringToUrl(urlDto.getUrl());
        String path = URLParser.getPathFromUrl(url);
        Page pageEntity = pageRepository.findBySiteIdAndPath(siteEntity, path);
        Page newPageEntity = HTMLManager.getPageEntity(url, siteRepository);

        if (pageEntity != null) {
            pageEntity.setContent(newPageEntity.getContent());
            pageEntity.setCode(newPageEntity.getCode());
        } else {
            pageEntity = newPageEntity;
        }

        Page finalPageEntity = pageEntity;
        repositoryManager.executeTransaction(() -> {
            pageRepository.save(finalPageEntity);
            siteRepository.updateStatusTimeById(finalPageEntity.getId(), LocalDateTime.now());
        });

        lemmatizator.save(HTMLManager.getTextFromHTML(pageEntity.getContent()), url);
        siteEntity.setIndexStatus(IndexStatus.INDEXED);
        siteRepository.save(siteEntity);
        return getSuccessResponse(new IndexingResponse(true));
    }


    // UTILS METHODS //


    private Site mapSiteEntityFromSiteList(SiteProps site) {
        Site siteEntity = new Site();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteEntity.setLastError(null);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        return siteEntity;
    }


    private Site getSiteEntityFromUrl(String url) {
        Site siteEntity = null;

        for (SiteProps site : sitesList.getSites()) {

            if (url.startsWith(site.getUrl())) {
                Site foundedSite = siteRepository.findByUrl(site.getUrl());

                if (foundedSite == null) {
                    Site finalFoundedSite = mapSiteEntityFromSiteList(site);
                    repositoryManager.executeTransaction(() ->
                        siteRepository.save(finalFoundedSite)
                    );
                    foundedSite = finalFoundedSite;
                }

                siteEntity = foundedSite;
            }
        }

        return siteEntity;
    }


    private static boolean hasErrorsDuringInvocation(RecursiveAction action, Site siteEntity) {
        try {
            action.invoke();
            return false;
        } catch (Exception e) {
            logger.error("Ошибка во время индексации сайта {}: {}", siteEntity.getUrl(), e.getMessage(), e);
            siteEntity.setIndexStatus(IndexStatus.FAILED);
            return true;
        }
    }


    // RESPONSE GETTERS //


    private ResponseEntity<IndexingResponse> getSuccessResponse(IndexingResponse body) {
        return ResponseEntity.ok(body);
    }


    private ResponseEntity<IndexingResponse> getSuccessResponse(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }


    private ResponseEntity<IndexingResponse> getSuccessResponse(HttpStatus status, IndexingResponse body) {
        return ResponseEntity.status(status).body(body);
    }


    private ResponseEntity<IndexingResponse> getFailedResponse(IndexingResponse body) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
