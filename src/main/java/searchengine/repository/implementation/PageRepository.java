package searchengine.repository.implementation;

import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.GenericRepository;

@Repository
public interface PageRepository extends GenericRepository<Page> {
    Page findBySiteIdAndPath(Site site, String path);
}
