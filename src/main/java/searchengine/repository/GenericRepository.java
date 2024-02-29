package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.DefaultModel;

public interface GenericRepository<EntityType extends DefaultModel> extends JpaRepository<EntityType, Long> {
}
