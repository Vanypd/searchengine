package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import searchengine.model.DefaultModel;

@NoRepositoryBean
public interface GenericRepository<EntityType extends DefaultModel> extends JpaRepository<EntityType, Long> {
}
