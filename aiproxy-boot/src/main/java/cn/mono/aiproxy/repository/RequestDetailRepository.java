package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.RequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestDetailRepository extends JpaRepository<RequestDetail, Integer> {
    Optional<RequestDetail> findByLog_IdAndLog_GroupId(Integer logId, String groupId);
    Optional<RequestDetail> findByLog_Id(Integer logId);
}
