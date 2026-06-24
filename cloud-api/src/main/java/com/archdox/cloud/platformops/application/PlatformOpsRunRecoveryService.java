package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsRunRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PlatformOpsRunRecoveryService.class);
    private static final String FAILURE_CODE = "FLOW_INTERRUPTED_BY_RESTART";

    private final PlatformOpsRunRepository runRepository;

    public PlatformOpsRunRecoveryService(PlatformOpsRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional
    public int failInterruptedRuns(OffsetDateTime now) {
        var runningRuns = runRepository.findByStatus(PlatformOpsRunStatus.RUNNING);
        runningRuns.forEach(run -> run.fail(FAILURE_CODE, now));
        if (!runningRuns.isEmpty()) {
            log.warn("Recovered interrupted platform ops runs: count={}, failureCode={}",
                    runningRuns.size(),
                    FAILURE_CODE);
        }
        return runningRuns.size();
    }
}
