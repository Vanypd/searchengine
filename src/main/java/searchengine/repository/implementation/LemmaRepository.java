package searchengine.repository.implementation;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Site;
import searchengine.repository.GenericRepository;

@Repository
public interface LemmaRepository extends GenericRepository<Lemma> {
    Lemma findByLemma(String lemma);
    Lemma findByLemmaAndSiteId(String lemma, Site site);
    boolean existsByLemma(String lemma);


    @Query("SELECT MAX(l.frequency) FROM Lemma l")
    Long findMaxFrequency();


    @Modifying
    @Query("UPDATE Lemma l SET l.frequency = l.frequency + 1 WHERE l.id = ?1")
    void incrementFrequency(Long lemmaId);


    @Query("SELECT SUM(i.rank) FROM Index i JOIN i.pageId p JOIN p.siteId s WHERE s.url = ?1")
    long countLemmasBySiteUrl(String url);
}
