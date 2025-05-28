package cn.mono.aiproxy.repository;

import cn.mono.aiproxy.model.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Integer> {
    Optional<Token> findByKey(String key);
    Optional<Token> findByNameAndGroupId(String name, String groupId); // Assuming GroupEntity's ID is String
}
