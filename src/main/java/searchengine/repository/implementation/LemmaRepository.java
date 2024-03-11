package searchengine.repository.implementation;

import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Lemma;
import searchengine.repository.GenericRepository;

@Repository
public interface LemmaRepository extends GenericRepository<Lemma> {
    Lemma findByLemma(String lemma);
}
