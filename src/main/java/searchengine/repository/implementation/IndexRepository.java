package searchengine.repository.implementation;

import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Index;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Page;
import searchengine.repository.GenericRepository;

@Repository
public interface IndexRepository extends GenericRepository<Index> {
    Index findByPageIdAndLemmaId(Page page, Lemma lemma);
}
