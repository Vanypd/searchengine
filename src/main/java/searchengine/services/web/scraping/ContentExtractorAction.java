package searchengine.services.web.scraping;

import org.springframework.http.HttpStatus;
import searchengine.concurrency.implementation.ThreadPoolManager;
import searchengine.model.implementation.Page;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;
import searchengine.services.utils.URLParser;
import searchengine.services.web.html.HTMLManager;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

public class ContentExtractorAction extends RecursiveAction {

    private final URL baseUrl;
    private final String path;

    protected RepositoryManager repositoryManager;
    protected PageRepository pageRepository;
    protected SiteRepository siteRepository;
    protected ConcurrentHashMap<String, String> setOfUrl;

    private static volatile boolean isStopped = false;


    // CONSTRUCTORS //


    public ContentExtractorAction(RepositoryManager repositoryManager, String baseUrl) {
        this.repositoryManager = repositoryManager;
        this.pageRepository = repositoryManager.getPageRepository();
        this.siteRepository = repositoryManager.getSiteRepository();
        this.baseUrl = URLParser.mapStringToUrl(baseUrl);
        this.path = "/";
        setOfUrl = new ConcurrentHashMap<>();
    }


    public ContentExtractorAction(URL baseUrl, String path) {
        this.baseUrl = baseUrl;
        this.path = path;
    }


    // METHODS //


    @Override
    public void compute() {

        if (isStopped) {
            return;
        }

        URL url = URLParser.concatBaseUrlWithPath(baseUrl.toString(), path);
        Page pageEntity = HTMLManager.getPageEntity(url, siteRepository);
        List<ContentExtractorAction> taskList = new ArrayList<>();

        repositoryManager.executeTransaction(() -> {
            pageRepository.save(pageEntity);
            siteRepository.updateStatusTimeById(pageEntity.getId(), LocalDateTime.now());
        });

        HttpStatus pageStatus = HttpStatus.valueOf(pageEntity.getCode());

        if (pageStatus.is4xxClientError()) {
            return;
        }

        Set<String> paths = HTMLManager.getPagePaths(pageEntity);

        for (String path : paths) {

            if (path.equals(this.path) || setOfUrl.containsKey(path)) {
                continue;
            } else {
                setOfUrl.put(path, path);
            }

            ThreadPoolManager.executeDelay(200);
            ContentExtractorAction task = new ContentExtractorAction(baseUrl, path);
            task.repositoryManager = this.repositoryManager;
            task.pageRepository = this.pageRepository;
            task.siteRepository = this.siteRepository;
            task.setOfUrl = this.setOfUrl;
            task.fork();
            taskList.add(task);
        }

        for (ContentExtractorAction task : taskList) {
            task.join();
        }
    }


    public static void stop() {
        isStopped = true;
    }


    public static boolean isStopped() {
        return isStopped;
    }
}
