package searchengine.services.web.scraping;

import searchengine.model.implementation.Page;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;
import searchengine.services.concurrency.ForkJoinPoolManager;
import searchengine.services.web.html.HTMLManager;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

public class ContentExtractorAction extends RecursiveAction {

    private final URL baseUrl;
    private final String path;

    protected RepositoryManager repositoryManager;
    protected PageRepository pageRepository;
    protected SiteRepository siteRepository;
    protected Set<String> setOfUrl;

    private static volatile boolean isStopped = false;

    // CONSTRUCTORS //


    public ContentExtractorAction(RepositoryManager repositoryManager, String baseUrl) {
        this.repositoryManager = repositoryManager;
        this.pageRepository = repositoryManager.getPageRepository();
        this.siteRepository = repositoryManager.getSiteRepository();
        this.baseUrl = HTMLManager.mapStringToUrl(baseUrl);
        this.path = "/";
        setOfUrl = new HashSet<>();
    }


    public ContentExtractorAction(URL baseUrl, String path) {
        this.baseUrl = baseUrl;
        this.path = path;
    }


    // METHODS //


    @Override
    public void compute() {

        URL url = HTMLManager.concatBaseUrlWithPath(baseUrl.toString(), path);
        Set<String> paths = HTMLManager.getPagePaths(url);
        Page pageEntity = HTMLManager.getPageEntity(url, siteRepository);

        for (String path : paths) {

            if (isStopped) {
                System.out.println("Подзадача завершается из-за остановки... ");
                return;
            }

            if (setOfUrl.contains(path)) {
                continue;
            }

            setOfUrl.add(path);

            synchronized (this) {
                pageRepository.save(pageEntity);
                siteRepository.updateStatusTimeById(pageEntity.getId(), LocalDateTime.now());
            }

            ForkJoinPoolManager.executeDelay(200);
            ContentExtractorAction task = new ContentExtractorAction(baseUrl, path);
            task.repositoryManager = this.repositoryManager;
            task.pageRepository = this.pageRepository;
            task.siteRepository = this.siteRepository;
            task.setOfUrl = this.setOfUrl;
            task.fork();
        }
    }


    public static void stop() {
        System.out.println("Вызвана остановка");
        isStopped = true;
    }
}
