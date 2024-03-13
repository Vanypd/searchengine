package searchengine.repository;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.concurrency.implementation.ThreadPoolManager;
import searchengine.repository.implementation.IndexRepository;
import searchengine.repository.implementation.LemmaRepository;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;

@Service
@Getter
@AllArgsConstructor
public class RepositoryManager {
    private final ThreadPoolManager threadPoolManager;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    @Transactional
    public void executeTransaction(Runnable task) {
        task.run();
    }
}
