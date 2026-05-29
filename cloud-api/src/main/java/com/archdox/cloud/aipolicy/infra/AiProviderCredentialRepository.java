package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderCredentialRepository extends JpaRepository<AiProviderCredential, Long> {
    Optional<AiProviderCredential> findByProviderCode(String providerCode);

    List<AiProviderCredential> findAllByOrderByProviderCodeAsc();
}
