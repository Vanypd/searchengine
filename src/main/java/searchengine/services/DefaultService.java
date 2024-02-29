package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.repository.RepositoryManager;

public abstract class DefaultService {
    protected final RepositoryManager repositoryManager;
    protected static final Logger logger = LoggerFactory.getLogger(DefaultService.class);

    @Autowired
    public DefaultService(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }
}
