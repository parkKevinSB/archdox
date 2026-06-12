package com.archdox.cloud.monitoring.infra;

import com.archdox.cloud.monitoring.domain.ServerRuntimeHealthSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRuntimeHealthSettingsRepository extends JpaRepository<ServerRuntimeHealthSettings, String> {
}
