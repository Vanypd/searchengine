package searchengine.services.web.scraping;

import searchengine.model.implementation.Page;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;
import searchengine.services.web.html.HTMLManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RecursiveAction;

public class ContentExtractorAction extends RecursiveAction {
    private final RepositoryManager repositoryManager;
    private final String domain;
    private final String link;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private static volatile boolean isStopped;


    public ContentExtractorAction(RepositoryManager repositoryManager, String domain) {
        this.repositoryManager = repositoryManager;
        this.domain = domain;
        this.link = domain;
        this.pageRepository = repositoryManager.getPageRepository();
        this.siteRepository = repositoryManager.getSiteRepository();

        isStopped = false;
        Page page = HTMLManager.getPageEntity(domain, siteRepository);

        synchronized (this) {
            pageRepository.save(page);
            siteRepository.updateStatusTimeById(page.getId(), LocalDateTime.now());
        }
    }


    public ContentExtractorAction(RepositoryManager repositoryManager, String domain, String link) {
        this.repositoryManager = repositoryManager;
        this.domain = domain;
        this.link = link;
        this.pageRepository = repositoryManager.getPageRepository();
        this.siteRepository = repositoryManager.getSiteRepository();
    }


    @Override
    public void compute() {
        List<String> uniqueLinks = getUniqueLinks(link);

        for (String path : uniqueLinks) {

            if (isStopped) {
                System.out.println("Подзадача завершается из-за остановки... ");
                return;
            }

            executeDelay(200);
            ContentExtractorAction task =
                    new ContentExtractorAction(repositoryManager, domain, path);
            task.fork();

            Page page = HTMLManager.getPageEntity(domain, path, siteRepository);

            synchronized (this) {
                pageRepository.save(page);
                siteRepository.updateStatusTimeById(page.getId(), LocalDateTime.now());
            }
        }
    }


    public static void stop() {
        System.out.println("Вызвана остановка");
        isStopped = true;
    }


    public static void executeDelay(int delayInMillis) {
        try {
            Thread.sleep(delayInMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> getUniqueLinks(String link) {
        List<String> pathsList = HTMLManager.getPagePaths(domain, link);
        List<String> existPaths;

        synchronized (pathsList) {
            existPaths = pageRepository.getAllPaths();
        }

        return pathsList.stream().filter(path -> !existPaths.contains(path)).toList();
    }
}
