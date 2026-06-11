package com.archdox.cloud.legal.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.legal.sync")
public class LegalSyncProperties {
    private long workerIntervalMs = 250;
    private OpenApi openApi = new OpenApi();
    private Monitor monitor = new Monitor();

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public OpenApi getOpenApi() {
        return openApi;
    }

    public void setOpenApi(OpenApi openApi) {
        this.openApi = openApi == null ? new OpenApi() : openApi;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public void setMonitor(Monitor monitor) {
        this.monitor = monitor == null ? new Monitor() : monitor;
    }

    public long safeWorkerIntervalMs() {
        return Math.max(50, workerIntervalMs);
    }

    public static class Monitor {
        private boolean enabled = false;
        private String runTimes = "03:00,15:00";
        private String zoneId = "Asia/Seoul";
        private long checkIntervalMs = 60_000;
        private long catchUpGraceMinutes = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRunTimes() {
            return runTimes;
        }

        public void setRunTimes(String runTimes) {
            this.runTimes = runTimes == null || runTimes.isBlank() ? "03:00,15:00" : runTimes;
        }

        public String getZoneId() {
            return zoneId;
        }

        public void setZoneId(String zoneId) {
            this.zoneId = zoneId == null || zoneId.isBlank() ? "Asia/Seoul" : zoneId;
        }

        public long getCheckIntervalMs() {
            return checkIntervalMs;
        }

        public void setCheckIntervalMs(long checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
        }

        public long getCatchUpGraceMinutes() {
            return catchUpGraceMinutes;
        }

        public void setCatchUpGraceMinutes(long catchUpGraceMinutes) {
            this.catchUpGraceMinutes = catchUpGraceMinutes;
        }

        public long safeCheckIntervalMs() {
            return Math.max(1_000, checkIntervalMs);
        }

        public long safeCatchUpGraceMinutes() {
            return Math.max(0, catchUpGraceMinutes);
        }
    }

    public static class OpenApi {
        private boolean enabled = false;
        private String oc = "";
        private String baseUrl = "https://www.law.go.kr/DRF";
        private String sourceCode = LawOpenDataLegalSourceClient.SOURCE_CODE;
        private String userAgent = "Mozilla/5.0 ArchDox/1.0";
        private long requestTimeoutMs = 20000;
        private long requestIntervalMs = 800;
        private int maxAttempts = 3;
        private List<Target> targets = defaultTargets();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getOc() {
            return oc;
        }

        public void setOc(String oc) {
            this.oc = oc == null ? "" : oc;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://www.law.go.kr/DRF" : baseUrl;
        }

        public String getSourceCode() {
            return sourceCode;
        }

        public void setSourceCode(String sourceCode) {
            this.sourceCode = sourceCode == null || sourceCode.isBlank()
                    ? LawOpenDataLegalSourceClient.SOURCE_CODE
                    : sourceCode;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent == null || userAgent.isBlank()
                    ? "Mozilla/5.0 ArchDox/1.0"
                    : userAgent;
        }

        public long getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public long getRequestIntervalMs() {
            return requestIntervalMs;
        }

        public void setRequestIntervalMs(long requestIntervalMs) {
            this.requestIntervalMs = requestIntervalMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public List<Target> getTargets() {
            return targets;
        }

        public void setTargets(List<Target> targets) {
            this.targets = targets == null || targets.isEmpty() ? defaultTargets() : new ArrayList<>(targets);
        }

        private static List<Target> defaultTargets() {
            return List.of(
                    new Target(
                            "law",
                            "\uAC74\uCD95\uBC95",
                            "\uAC74\uCD95\uBC95",
                            "BUILDING_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uAC74\uCD95\uBC95 \uC2DC\uD589\uB839",
                            "\uAC74\uCD95\uBC95 \uC2DC\uD589\uB839",
                            "BUILDING_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uAC74\uCD95\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uAC74\uCD95\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "BUILDING_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900",
                            "\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900",
                            "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                            "ADMINISTRATIVE_RULE"));
        }
    }

    public static class Target {
        private String target = "law";
        private String query = "";
        private String expectedName = "";
        private String actCode = "";
        private String actType = "LAW";

        public Target() {
        }

        public Target(String target, String query, String expectedName, String actCode, String actType) {
            this.target = target;
            this.query = query;
            this.expectedName = expectedName;
            this.actCode = actCode;
            this.actType = actType;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getExpectedName() {
            return expectedName;
        }

        public void setExpectedName(String expectedName) {
            this.expectedName = expectedName;
        }

        public String getActCode() {
            return actCode;
        }

        public void setActCode(String actCode) {
            this.actCode = actCode;
        }

        public String getActType() {
            return actType;
        }

        public void setActType(String actType) {
            this.actType = actType;
        }
    }
}
