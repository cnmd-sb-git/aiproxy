package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, String> {
    // Add custom query methods if needed later, e.g., findByIdAndStatus(String id, Integer status)
}
