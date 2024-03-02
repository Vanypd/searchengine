package searchengine.repository.implementation;

import org.springframework.data.jpa.repository.Query;
import searchengine.model.implementation.Page;
import searchengine.repository.GenericRepository;

import java.util.List;

public interface PageRepository extends GenericRepository<Page> {

    @Query("SELECT p.path FROM Page p WHERE p.siteId.url = :url")
    List<String> findAllPathsBySiteUrl(String url);

    @Query("SELECT p.path FROM Page p")
    List<String> getAllPaths();
}
