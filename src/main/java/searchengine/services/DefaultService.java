package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.IndexRepository;
import searchengine.repository.implementation.LemmaRepository;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;

public abstract class DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);
    protected final RepositoryManager repositoryManager;
    protected final SiteRepository siteRepository;
    protected final PageRepository pageRepository;
    protected final LemmaRepository lemmaRepository;
    protected final IndexRepository indexRepository;


    @Autowired
    public DefaultService(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
        siteRepository = repositoryManager.getSiteRepository();
        pageRepository = repositoryManager.getPageRepository();
        lemmaRepository = repositoryManager.getLemmaRepository();
        indexRepository = repositoryManager.getIndexRepository();
    }
}
