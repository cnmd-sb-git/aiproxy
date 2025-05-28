package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.GroupModelConfigEntity;
import cn.mono.aiproxy.model.embeddable.GroupModelConfigId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GroupModelConfigRepository extends JpaRepository<GroupModelConfigEntity, GroupModelConfigId> {

    List<GroupModelConfigEntity> findByGroupId(String groupId);

    @Transactional // Needed for delete operations if not part of a larger transaction
    void deleteByGroupIdAndModel(String groupId, String model);
}
