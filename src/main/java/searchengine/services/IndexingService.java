package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;
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
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private boolean isIndexing = false;


    // CONSTRUCTORS //


    @Autowired
    public IndexingService(SitesList sitesList,
                           RepositoryManager repositoryManager,
                           ForkJoinPoolManager forkJoinPoolManager) {

        super(repositoryManager);
        this.sitesList = sitesList;
        this.forkJoinPoolManager = forkJoinPoolManager;
        siteRepository = repositoryManager.getSiteRepository();
        pageRepository = repositoryManager.getPageRepository();
    }


    // API METHODS //


    public ResponseEntity<IndexingResponse> startIndexing() {
        logger.info("Вызван запуск полной индексации");

        if (isIndexing) {
            return getFailedResponse(new IndexingErrorResponse("Индексация уже запущена"));
        }

        isIndexing = true;
        AtomicInteger errorsCount = new AtomicInteger();
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        List<Runnable> tasks = new ArrayList<>();

        sitesList.getSites().forEach(site ->
            tasks.add(() -> {
                Site siteEntity = new Site();
                siteEntity.setUrl(site.getUrl());
                siteEntity.setName(site.getName());
                siteEntity.setIndexStatus(IndexStatus.INDEXING);
                siteEntity.setLastError(null);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);

                ContentExtractorAction action = new ContentExtractorAction(repositoryManager, site.getUrl());

                if (!invokeAction(action, siteEntity)) {
                    errorsCount.getAndIncrement();
                    return;
                }

                siteEntity.setIndexStatus(IndexStatus.INDEXED);
                siteRepository.save(siteEntity);
            })
        );

        forkJoinPoolManager.executeAwait(tasks);
        isIndexing = false;

        if (errorsCount.get() != 0) {
            return getFailedResponse(
                    new IndexingErrorResponse("Ошибка индексации на " + errorsCount.get() + " сайтах")
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
        boolean urlInBounds = false;
        Site siteEntity = null;

        for (Site site : siteRepository.findAll()) {
            if (url.contains(site.getUrl())) {
                urlInBounds = true;
                siteEntity = site;
                break;
            }
        }

        if (!urlInBounds) {
            return getFailedResponse(new IndexingErrorResponse(
                    "Данная страница находится за пределами сайтов," +
                            "указанных в конфигурационном файле"
            ));
        }

        siteEntity.setIndexStatus(IndexStatus.INDEXING);
        siteRepository.save(siteEntity);

        ContentExtractorAction action = new ContentExtractorAction(repositoryManager, siteEntity.getUrl());

        try {
            forkJoinPoolManager.executeAwait(action::invoke);
        } catch (Exception e) {
            logger.error("Ошибка во время индексации сайта {}: {}", siteEntity.getUrl(), e.getMessage(), e);
            siteEntity.setIndexStatus(IndexStatus.FAILED);
            return getFailedResponse(
                    new IndexingErrorResponse("Ошибка во время индексации сайта: " + e.getMessage())
            );
        }

        siteEntity.setIndexStatus(IndexStatus.INDEXED);
        siteRepository.save(siteEntity);
        return getSuccessResponse(new IndexingResponse(true));
    }


    // UTILS METHODS //


    private boolean invokeAction(RecursiveAction action, Site siteEntity) {
        try {
            action.invoke();
            return true;
        } catch (Exception e) {
            logger.error("Ошибка во время индексации сайта {}: {}", siteEntity.getUrl(), e.getMessage(), e);
            siteEntity.setIndexStatus(IndexStatus.FAILED);
            return false;
        }
    }



    // RESPONSE GETTERS //


    protected ResponseEntity<IndexingResponse> getSuccessResponse(IndexingResponse body) {
        return ResponseEntity.ok(body);
    }


    protected ResponseEntity<IndexingResponse> getSuccessResponse(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }


    protected ResponseEntity<IndexingResponse> getSuccessResponse(HttpStatus status, IndexingResponse body) {
        return ResponseEntity.status(status).body(body);
    }


    protected ResponseEntity<IndexingResponse> getFailedResponse(IndexingResponse body) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
