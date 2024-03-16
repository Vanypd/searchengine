package searchengine.repository.implementation;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.GenericRepository;

import java.util.List;

@Repository
public interface PageRepository extends GenericRepository<Page> {
    Page findBySiteIdAndPath(Site site, String path);


    @Query("SELECT COUNT(p) FROM Page p WHERE p.siteId.url = ?1")
    long countPagesBySiteUrl(String url);


    @Query("SELECT p FROM Page p " +
            "JOIN Index i ON p.id = i.pageId.id " +
            "JOIN Lemma l ON i.lemmaId.id = l.id " +
            "WHERE l.lemma = ?1")
    List<Page> findPagesByLemma(String lemma);


    @Query("SELECT SUM(i.rank) FROM Index i WHERE i.pageId = ?1")
    Long getRankSumForPage(Page page);
}
