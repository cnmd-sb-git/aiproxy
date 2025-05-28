package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {
    // Add custom query methods if needed later
}
