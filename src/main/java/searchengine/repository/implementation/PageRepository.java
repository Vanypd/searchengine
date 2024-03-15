package searchengine.repository.implementation;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.GenericRepository;

@Repository
public interface PageRepository extends GenericRepository<Page> {
    Page findBySiteIdAndPath(Site site, String path);

    @Query("SELECT COUNT(p) FROM Page p WHERE p.siteId.url = ?1")
    long countPagesBySiteUrl(String url);
}
