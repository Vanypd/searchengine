package searchengine.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;

@Service
@Getter
@AllArgsConstructor

public class RepositoryManager {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
}
