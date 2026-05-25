package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;
import com.archdox.cloud.platformadmin.infra.PlatformAdminRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final PlatformAdminProperties properties;
    private final UserAccountRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;

    public PlatformAdminBootstrap(
            PlatformAdminProperties properties,
            UserAccountRepository userRepository,
            PlatformAdminRepository platformAdminRepository
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var now = OffsetDateTime.now();
        for (var email : properties.getBootstrapSuperAdminEmails()) {
            if (email == null || email.isBlank()) {
                continue;
            }
            userRepository.findByEmailIgnoreCase(email.trim()).ifPresentOrElse(user -> {
                if (!platformAdminRepository.existsByUserId(user.id())) {
                    platformAdminRepository.save(new PlatformAdmin(user.id(), PlatformAdminRole.SUPER_ADMIN, now));
                    log.info("Bootstrapped platform SUPER_ADMIN for userId={}", user.id());
                }
            }, () -> log.warn("Platform admin bootstrap email did not match an existing user: {}", email));
        }
    }
}
