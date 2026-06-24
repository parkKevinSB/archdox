package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsLogProjectionCursor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOpsLogProjectionCursorRepository extends JpaRepository<PlatformOpsLogProjectionCursor, String> {
}
