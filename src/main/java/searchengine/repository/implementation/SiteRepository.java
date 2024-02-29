package searchengine.repository.implementation;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.implementation.IndexStatus;
import searchengine.model.implementation.Site;
import searchengine.repository.GenericRepository;

import java.time.LocalDateTime;

public interface SiteRepository extends GenericRepository<Site> {
    Site findByUrl(String url);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.statusTime = ?2 WHERE s.id = ?1")
    void updateStatusTimeById(Long siteId, LocalDateTime newStatusTime);


    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.indexStatus = ?2, lastError = ?3 WHERE s.indexStatus = ?1")
    void updateStatusAndErrorByStatus(IndexStatus oldIndexStatus,
                                                    IndexStatus newIndexStatus,
                                                    String errorMessage);
}
