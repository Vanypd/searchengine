package searchengine.repository.implementation;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Index;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Page;
import searchengine.repository.GenericRepository;

import java.util.List;

@Repository
public interface IndexRepository extends GenericRepository<Index> {
    List<Index> findAllByPageId(Page page);


    @Query("SELECT i FROM Index i WHERE i.pageId = ?1 AND i.lemmaId = ?2")
    Index findByPageIdAndLemmaId(Page page, Lemma lemma);


    @Modifying
    @Query("UPDATE Index i SET i.rank = i.rank + ?2 WHERE i.id = ?1")
    void incrementRank( Long indexId, int count);
}
