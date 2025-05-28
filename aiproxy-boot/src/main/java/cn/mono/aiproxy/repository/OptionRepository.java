package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.OptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionRepository extends JpaRepository<OptionEntity, String> {
    List<OptionEntity> findByKeyIn(List<String> keys);
}
