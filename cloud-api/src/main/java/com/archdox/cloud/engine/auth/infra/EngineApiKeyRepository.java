package com.archdox.cloud.engine.auth.infra;

import com.archdox.cloud.engine.auth.domain.EngineApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineApiKeyRepository extends JpaRepository<EngineApiKey, Long> {
    Optional<EngineApiKey> findByKeyId(String keyId);

    List<EngineApiKey> findByOwnerUserId(Long ownerUserId, Sort sort);
}
