package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsAutomationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOpsAutomationSettingsRepository extends JpaRepository<PlatformOpsAutomationSettings, String> {
}
