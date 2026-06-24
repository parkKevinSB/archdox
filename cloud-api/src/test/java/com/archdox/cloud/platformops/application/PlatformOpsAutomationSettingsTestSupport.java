package com.archdox.cloud.platformops.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.dto.PlatformOpsAutomationSettingsResponse;

public final class PlatformOpsAutomationSettingsTestSupport {
    private PlatformOpsAutomationSettingsTestSupport() {
    }

    public static PlatformOpsAutomationSettingsService service() {
        return service(
                new PlatformOpsDetectionProperties(),
                new PlatformOpsDailyReportProperties(),
                new PlatformOpsRetentionProperties());
    }

    public static PlatformOpsAutomationSettingsService service(
            PlatformOpsDetectionProperties detectionProperties,
            PlatformOpsDailyReportProperties dailyReportProperties,
            PlatformOpsRetentionProperties retentionProperties
    ) {
        var service = mock(PlatformOpsAutomationSettingsService.class);
        when(service.settings()).thenAnswer(invocation -> PlatformOpsAutomationSettingsResponse.fromDefaults(
                detectionProperties,
                dailyReportProperties,
                retentionProperties));
        return service;
    }
}
