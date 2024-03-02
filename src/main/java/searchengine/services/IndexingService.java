package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteProps;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.services.concurrency.ForkJoinPoolManager;
import searchengine.services.web.scraping.ContentExtractorAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IndexingService extends DefaultService {

    private final SitesList sitesList;
    private final ForkJoinPoolManager forkJoinPoolManager;
    private boolean isIndexing = false;


    // CONSTRUCTORS //


    @Autowired
    public IndexingService(SitesList sitesList, RepositoryManager repositoryManager,
                           ForkJoinPoolManager forkJoinPoolManager) {
        super(repositoryManager);
        this.sitesList = sitesList;
        this.forkJoinPoolManager = forkJoinPoolManager;
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
            siteRepository.save(siteEntity);
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
        siteRepository.updateStatusAndErrorByStatus(IndexStatus.INDEXING, IndexStatus.FAILED,
                "Индексация остановлена пользователем");
        return getSuccessResponse(new IndexingResponse(true));
    }


    public ResponseEntity<IndexingResponse> indexPage(String url) {
        logger.info("Вызвана индексация отдельной страницы: {}", url);

        Site siteEntity = getSiteEntityFromUrl(url);

        if (siteEntity == null) {
            String errorMessage = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
            return getFailedResponse(new IndexingErrorResponse(errorMessage));
        }


        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteRepository.save(siteEntity);

        ContentExtractorAction action = new ContentExtractorAction(repositoryManager, siteEntity.getUrl());

        if (hasErrorsDuringInvocation(action, siteEntity)) {
            return getFailedResponse(new IndexingErrorResponse("Ошибка во время индексации страницы сайта"));
        }

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
            if (url.contains(site.getUrl())) {
                siteEntity = siteRepository.findByUrl(site.getUrl());
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
