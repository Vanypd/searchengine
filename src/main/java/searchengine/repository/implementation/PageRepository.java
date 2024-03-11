package searchengine.repository.implementation;

import org.springframework.stereotype.Repository;
import searchengine.model.implementation.Page;
import searchengine.repository.GenericRepository;

@Repository
public interface PageRepository extends GenericRepository<Page> {
    Page findByPath(String path);
}
