package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LogRepository extends JpaRepository<Log, Integer>, JpaSpecificationExecutor<Log> { // Added JpaSpecificationExecutor
    Optional<Log> findByRequestId(String requestId);
    Optional<Log> findTopByGroupIdOrderByRequestAtDesc(String groupId);
    Optional<Log> findTopByTokenNameAndGroupIdOrderByRequestAtDesc(String tokenName, String groupId);

    @Query("SELECT DISTINCT l.model FROM Log l WHERE (:groupId IS NULL OR l.groupId = :groupId) AND (:startTime IS NULL OR l.createdAt >= :startTime) AND (:endTime IS NULL OR l.createdAt <= :endTime)")
    List<String> findDistinctModelByCriteria(@Param("groupId") String groupId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT DISTINCT l.tokenName FROM Log l WHERE (:groupId IS NULL OR l.groupId = :groupId) AND (:startTime IS NULL OR l.createdAt >= :startTime) AND (:endTime IS NULL OR l.createdAt <= :endTime) AND l.tokenName IS NOT NULL AND l.tokenName <> ''")
    List<String> findDistinctTokenNameByCriteria(@Param("groupId") String groupId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    void deleteByCreatedAtBefore(LocalDateTime timestamp);
}
