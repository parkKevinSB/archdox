package com.archdox.cloud.legal.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.legal.sync")
public class LegalSyncProperties {
    private long workerIntervalMs = 250;
    private int fetchExecutorThreads = 1;
    private int fetchExecutorQueueCapacity = 10;
    private OpenApi openApi = new OpenApi();
    private Monitor monitor = new Monitor();

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public int getFetchExecutorThreads() {
        return fetchExecutorThreads;
    }

    public void setFetchExecutorThreads(int fetchExecutorThreads) {
        this.fetchExecutorThreads = fetchExecutorThreads;
    }

    public int getFetchExecutorQueueCapacity() {
        return fetchExecutorQueueCapacity;
    }

    public void setFetchExecutorQueueCapacity(int fetchExecutorQueueCapacity) {
        this.fetchExecutorQueueCapacity = fetchExecutorQueueCapacity;
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

    public int safeFetchExecutorThreads() {
        return Math.max(1, fetchExecutorThreads);
    }

    public int safeFetchExecutorQueueCapacity() {
        return Math.max(1, fetchExecutorQueueCapacity);
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
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95",
                            "GREEN_BUILDING_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95 \uC2DC\uD589\uB839",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95 \uC2DC\uD589\uB839",
                            "GREEN_BUILDING_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uB179\uC0C9\uAC74\uCD95\uBB3C \uC870\uC131 \uC9C0\uC6D0\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "GREEN_BUILDING_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "\uAC74\uCD95\uBB3C\uC758 \uC5D0\uB108\uC9C0\uC808\uC57D\uC124\uACC4\uAE30\uC900",
                            "\uAC74\uCD95\uBB3C\uC758 \uC5D0\uB108\uC9C0\uC808\uC57D\uC124\uACC4\uAE30\uC900",
                            "BUILDING_ENERGY_SAVING_DESIGN_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95",
                            "ELECTRICAL_SAFETY_MANAGEMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "ELECTRICAL_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC804\uAE30\uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "ELECTRICAL_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "\uC804\uAE30\uC124\uBE44\uAE30\uC220\uAE30\uC900",
                            "\uC804\uAE30\uC124\uBE44\uAE30\uC220\uAE30\uC900",
                            "ELECTRICAL_EQUIPMENT_TECHNICAL_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "\uC804\uAE30\uC124\uBE44 \uAC80\uC0AC \uBC0F \uC810\uAC80\uC758 \uBC29\uBC95\u00B7\uC808\uCC28 \uB4F1\uC5D0 \uAD00\uD55C \uACE0\uC2DC",
                            "\uC804\uAE30\uC124\uBE44 \uAC80\uC0AC \uBC0F \uC810\uAC80\uC758 \uBC29\uBC95\u00B7\uC808\uCC28 \uB4F1\uC5D0 \uAD00\uD55C \uACE0\uC2DC",
                            "ELECTRICAL_EQUIPMENT_INSPECTION_AND_CHECK_NOTICE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "한국전기설비규정",
                            "한국전기설비규정",
                            "KOREA_ELECTRICAL_CODE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "설비(전기) 설계기준(KDS 31 00 00) 및 설비(전기) 표준시방서(KCS 31 00 00)",
                            "설비(전기) 설계기준(KDS 31 00 00) 및 설비(전기) 표준시방서(KCS 31 00 00)",
                            "ELECTRICAL_FACILITY_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "예비전원설비 설계기준(KDS 31 60 20)",
                            "예비전원설비 설계기준(KDS 31 60 20)",
                            "EMERGENCY_POWER_FACILITY_DESIGN_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uAE30\uACC4\uC124\uBE44\uBC95",
                            "\uAE30\uACC4\uC124\uBE44\uBC95",
                            "MECHANICAL_EQUIPMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uAE30\uACC4\uC124\uBE44\uBC95 \uC2DC\uD589\uB839",
                            "\uAE30\uACC4\uC124\uBE44\uBC95 \uC2DC\uD589\uB839",
                            "MECHANICAL_EQUIPMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uAE30\uACC4\uC124\uBE44\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uAE30\uACC4\uC124\uBE44\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "MECHANICAL_EQUIPMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "\uAE30\uACC4\uC124\uBE44 \uAE30\uC220\uAE30\uC900",
                            "\uAE30\uACC4\uC124\uBE44 \uAE30\uC220\uAE30\uC900",
                            "MECHANICAL_EQUIPMENT_TECHNICAL_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uAC74\uCD95\uBB3C\uC758 \uC124\uBE44\uAE30\uC900 \uB4F1\uC5D0 \uAD00\uD55C \uADDC\uCE59",
                            "\uAC74\uCD95\uBB3C\uC758 \uC124\uBE44\uAE30\uC900 \uB4F1\uC5D0 \uAD00\uD55C \uADDC\uCE59",
                            "BUILDING_EQUIPMENT_STANDARD_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "admrul",
                            "\uAE30\uACC4\uC124\uBE44 \uC720\uC9C0\uAD00\uB9AC\uAE30\uC900",
                            "\uAE30\uACC4\uC124\uBE44 \uC720\uC9C0\uAD00\uB9AC\uAE30\uC900",
                            "MECHANICAL_EQUIPMENT_MAINTENANCE_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "설비(기계) 설계기준(KDS 31 00 00) 및 설비(기계) 표준시방서(KCS 31 00 00)",
                            "설비(기계) 설계기준(KDS 31 00 00) 및 설비(기계) 표준시방서(KCS 31 00 00)",
                            "MECHANICAL_FACILITY_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95",
                            "ELEVATOR_SAFETY_MANAGEMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "ELEVATOR_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC2B9\uAC15\uAE30 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "ELEVATOR_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "\uC2B9\uAC15\uAE30\uC548\uC804\uBD80\uD488 \uC548\uC804\uAE30\uC900 \uBC0F \uC2B9\uAC15\uAE30 \uC548\uC804\uAE30\uC900",
                            "\uC2B9\uAC15\uAE30\uC548\uC804\uBD80\uD488 \uC548\uC804\uAE30\uC900 \uBC0F \uC2B9\uAC15\uAE30 \uC548\uC804\uAE30\uC900",
                            "ELEVATOR_SAFETY_PARTS_AND_ELEVATOR_SAFETY_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "\uC2B9\uAC15\uAE30 \uC124\uCE58\uAC80\uC0AC \uBC0F \uC548\uC804\uAC80\uC0AC\uC5D0 \uAD00\uD55C \uC6B4\uC601\uADDC\uC815",
                            "\uC2B9\uAC15\uAE30 \uC124\uCE58\uAC80\uC0AC \uBC0F \uC548\uC804\uAC80\uC0AC\uC5D0 \uAD00\uD55C \uC6B4\uC601\uADDC\uC815",
                            "ELEVATOR_INSTALLATION_AND_SAFETY_INSPECTION_OPERATION_RULE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "\uAE30\uACC4\uC2DD\uC8FC\uCC28\uC7A5\uCE58\uC758 \uC548\uC804\uAE30\uC900 \uBC0F \uAC80\uC0AC\uAE30\uC900 \uB4F1\uC5D0 \uAD00\uD55C \uADDC\uC815",
                            "\uAE30\uACC4\uC2DD\uC8FC\uCC28\uC7A5\uCE58\uC758 \uC548\uC804\uAE30\uC900 \uBC0F \uAC80\uC0AC\uAE30\uC900 \uB4F1\uC5D0 \uAD00\uD55C \uADDC\uC815",
                            "MECHANICAL_PARKING_DEVICE_SAFETY_AND_INSPECTION_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95",
                            "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC804\uAE30\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "ELECTRICAL_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95",
                            "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC804\uB825\uAE30\uC220\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "ELECTRIC_POWER_TECHNOLOGY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95",
                            "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC815\uBCF4\uD1B5\uC2E0\uACF5\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "INFORMATION_COMMUNICATIONS_CONSTRUCTION_BUSINESS_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uBC29\uC1A1\uD1B5\uC2E0\uC124\uBE44\uC758 \uAE30\uC220\uAE30\uC900\uC5D0 \uAD00\uD55C \uADDC\uC815",
                            "\uBC29\uC1A1\uD1B5\uC2E0\uC124\uBE44\uC758 \uAE30\uC220\uAE30\uC900\uC5D0 \uAD00\uD55C \uADDC\uC815",
                            "BROADCASTING_COMMUNICATIONS_FACILITY_TECHNICAL_STANDARD_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "admrul",
                            "\uC811\uC9C0\uC124\uBE44\u318D\uAD6C\uB0B4\uD1B5\uC2E0\uC124\uBE44\u318D\uC120\uB85C\uC124\uBE44 \uBC0F \uD1B5\uC2E0\uACF5\uB3D9\uAD6C\uB4F1\uC5D0 \uB300\uD55C \uAE30\uC220\uAE30\uC900",
                            "\uC811\uC9C0\uC124\uBE44\u318D\uAD6C\uB0B4\uD1B5\uC2E0\uC124\uBE44\u318D\uC120\uB85C\uC124\uBE44 \uBC0F \uD1B5\uC2E0\uACF5\uB3D9\uAD6C\uB4F1\uC5D0 \uB300\uD55C \uAE30\uC220\uAE30\uC900",
                            "GROUNDING_AND_IN_BUILDING_COMMUNICATION_FACILITY_TECHNICAL_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "\uBC29\uC1A1 \uACF5\uB3D9\uC218\uC2E0\uC124\uBE44\uC758 \uC124\uCE58\uAE30\uC900\uC5D0 \uAD00\uD55C \uACE0\uC2DC",
                            "\uBC29\uC1A1 \uACF5\uB3D9\uC218\uC2E0\uC124\uBE44\uC758 \uC124\uCE58\uAE30\uC900\uC5D0 \uAD00\uD55C \uACE0\uC2DC",
                            "BROADCASTING_COMMUNAL_RECEPTION_FACILITY_INSTALLATION_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95",
                            "CITY_GAS_BUSINESS_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "CITY_GAS_BUSINESS_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uB3C4\uC2DC\uAC00\uC2A4\uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "CITY_GAS_BUSINESS_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95",
                            "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95 \uC2DC\uD589\uB839",
                            "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC561\uD654\uC11D\uC720\uAC00\uC2A4\uC758 \uC548\uC804\uAD00\uB9AC \uBC0F \uC0AC\uC5C5\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "LIQUEFIED_PETROLEUM_GAS_SAFETY_BUSINESS_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95",
                            "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uB839",
                            "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uACE0\uC555\uAC00\uC2A4 \uC548\uC804\uAD00\uB9AC\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "HIGH_PRESSURE_GAS_SAFETY_CONTROL_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95",
                            "NEW_RENEWABLE_ENERGY_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95 \uC2DC\uD589\uB839",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95 \uC2DC\uD589\uB839",
                            "NEW_RENEWABLE_ENERGY_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "\uC2E0\uC5D0\uB108\uC9C0 \uBC0F \uC7AC\uC0DD\uC5D0\uB108\uC9C0 \uAC1C\uBC1C\u318D\uC774\uC6A9\u318D\uBCF4\uAE09 \uCD09\uC9C4\uBC95 \uC2DC\uD589\uADDC\uCE59",
                            "NEW_RENEWABLE_ENERGY_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "소방시설 설치 및 관리에 관한 법률",
                            "소방시설 설치 및 관리에 관한 법률",
                            "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "소방시설 설치 및 관리에 관한 법률 시행령",
                            "소방시설 설치 및 관리에 관한 법률 시행령",
                            "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "소방시설 설치 및 관리에 관한 법률 시행규칙",
                            "소방시설 설치 및 관리에 관한 법률 시행규칙",
                            "FIRE_FACILITIES_INSTALLATION_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "화재의 예방 및 안전관리에 관한 법률",
                            "화재의 예방 및 안전관리에 관한 법률",
                            "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "화재의 예방 및 안전관리에 관한 법률 시행령",
                            "화재의 예방 및 안전관리에 관한 법률 시행령",
                            "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "화재의 예방 및 안전관리에 관한 법률 시행규칙",
                            "화재의 예방 및 안전관리에 관한 법률 시행규칙",
                            "FIRE_PREVENTION_SAFETY_MANAGEMENT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "건축물의 피난ㆍ방화구조 등의 기준에 관한 규칙",
                            "건축물의 피난ㆍ방화구조 등의 기준에 관한 규칙",
                            "BUILDING_EVACUATION_FIREPROOF_STRUCTURE_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "admrul",
                            "건축자재등 품질인정 및 관리기준",
                            "건축자재등 품질인정 및 관리기준",
                            "BUILDING_MATERIAL_QUALITY_RECOGNITION_MANAGEMENT_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "내화구조의 인정 및 관리기준",
                            "내화구조의 인정 및 관리기준",
                            "FIRE_RESISTANT_STRUCTURE_RECOGNITION_MANAGEMENT_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "자동방화셔터, 방화문 및 방화댐퍼의 기준",
                            "자동방화셔터, 방화문 및 방화댐퍼의 기준",
                            "AUTOMATIC_FIRE_SHUTTER_AND_FIRE_DOOR_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "주차장법",
                            "주차장법",
                            "PARKING_LOT_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "주차장법 시행령",
                            "주차장법 시행령",
                            "PARKING_LOT_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "주차장법 시행규칙",
                            "주차장법 시행규칙",
                            "PARKING_LOT_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률",
                            "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률 시행령",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률 시행령",
                            "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률 시행규칙",
                            "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률 시행규칙",
                            "DISABLED_ELDERLY_PREGNANT_ACCESSIBILITY_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "건축물 에너지효율등급 인증 및 제로에너지건축물 인증에 관한 규칙",
                            "건축물 에너지효율등급 인증 및 제로에너지건축물 인증에 관한 규칙",
                            "BUILDING_ENERGY_EFFICIENCY_ZERO_ENERGY_CERTIFICATION_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "law",
                            "녹색건축 인증에 관한 규칙",
                            "녹색건축 인증에 관한 규칙",
                            "GREEN_BUILDING_CERTIFICATION_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "admrul",
                            "녹색건축 인증 기준",
                            "녹색건축 인증 기준",
                            "GREEN_BUILDING_CERTIFICATION_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "건축물 에너지효율등급 인증 및 제로에너지건축물 인증 기준",
                            "건축물 에너지효율등급 인증 및 제로에너지건축물 인증 기준",
                            "BUILDING_ENERGY_EFFICIENCY_ZERO_ENERGY_CERTIFICATION_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "건설기술 진흥법",
                            "건설기술 진흥법",
                            "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "건설기술 진흥법 시행령",
                            "건설기술 진흥법 시행령",
                            "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "건설기술 진흥법 시행규칙",
                            "건설기술 진흥법 시행규칙",
                            "CONSTRUCTION_TECHNOLOGY_PROMOTION_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "건설공사 품질관리 업무지침",
                            "건설공사 품질관리 업무지침",
                            "CONSTRUCTION_WORK_QUALITY_MANAGEMENT_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "건설공사 안전관리 업무수행 지침",
                            "건설공사 안전관리 업무수행 지침",
                            "CONSTRUCTION_WORK_SAFETY_MANAGEMENT_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "건설공사 사업관리방식 검토기준 및 업무수행지침",
                            "건설공사 사업관리방식 검토기준 및 업무수행지침",
                            "CONSTRUCTION_PROJECT_MANAGEMENT_METHOD_REVIEW_AND_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "건축물의 구조기준 등에 관한 규칙",
                            "건축물의 구조기준 등에 관한 규칙",
                            "BUILDING_STRUCTURAL_STANDARD_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "admrul",
                            "건축구조기준",
                            "건축구조기준",
                            "BUILDING_STRUCTURAL_DESIGN_STANDARD",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "건축공사표준시방서",
                            "건축공사표준시방서",
                            "ARCHITECTURAL_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "강구조공사 표준시방서(KCS 14 31 00)",
                            "강구조공사 표준시방서(KCS 14 31 00)",
                            "STEEL_STRUCTURE_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "KDS 14 20 00(콘크리트구조 설계기준), KCS 14 20 00(콘크리트공사 표준시방서)",
                            "KDS 14 20 00(콘크리트구조 설계기준), KCS 14 20 00(콘크리트공사 표준시방서)",
                            "CONCRETE_STRUCTURE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "가시설 설계기준(KDS 21 00 00) 및 가설공사 표준시방서(KCS 21 00 00)",
                            "가시설 설계기준(KDS 21 00 00) 및 가설공사 표준시방서(KCS 21 00 00)",
                            "TEMPORARY_FACILITY_DESIGN_AND_TEMPORARY_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "지반 설계기준(KDS 11 00 00) 및 지반공사 표준시방서(KCS 11 00 00)",
                            "지반 설계기준(KDS 11 00 00) 및 지반공사 표준시방서(KCS 11 00 00)",
                            "GEOTECHNICAL_DESIGN_AND_GROUND_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "조경설계기준(KDS 34 00 00) 및 조경공사 표준시방서(KCS 34 00 00)",
                            "조경설계기준(KDS 34 00 00) 및 조경공사 표준시방서(KCS 34 00 00)",
                            "LANDSCAPE_DESIGN_AND_WORK_STANDARD_SPECIFICATION",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "산업안전보건법",
                            "산업안전보건법",
                            "OCCUPATIONAL_SAFETY_HEALTH_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "산업안전보건법 시행령",
                            "산업안전보건법 시행령",
                            "OCCUPATIONAL_SAFETY_HEALTH_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "산업안전보건법 시행규칙",
                            "산업안전보건법 시행규칙",
                            "OCCUPATIONAL_SAFETY_HEALTH_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "law",
                            "산업안전보건기준에 관한 규칙",
                            "산업안전보건기준에 관한 규칙",
                            "OCCUPATIONAL_SAFETY_HEALTH_STANDARDS_RULE",
                            "MINISTERIAL_RULE"),
                    new Target(
                            "admrul",
                            "굴착공사 표준안전 작업지침",
                            "굴착공사 표준안전 작업지침",
                            "EXCAVATION_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "철골공사표준안전작업지침",
                            "철골공사표준안전작업지침",
                            "STEEL_FRAME_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "콘크리트공사 표준안전 작업지침",
                            "콘크리트공사 표준안전 작업지침",
                            "CONCRETE_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "가설공사 표준안전 작업지침",
                            "가설공사 표준안전 작업지침",
                            "TEMPORARY_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "admrul",
                            "해체공사표준안전작업지침",
                            "해체공사표준안전작업지침",
                            "DEMOLITION_WORK_STANDARD_SAFETY_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"),
                    new Target(
                            "law",
                            "지하안전관리에 관한 특별법",
                            "지하안전관리에 관한 특별법",
                            "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT",
                            "LAW"),
                    new Target(
                            "law",
                            "지하안전관리에 관한 특별법 시행령",
                            "지하안전관리에 관한 특별법 시행령",
                            "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT_ENFORCEMENT_DECREE",
                            "ENFORCEMENT_DECREE"),
                    new Target(
                            "law",
                            "지하안전관리에 관한 특별법 시행규칙",
                            "지하안전관리에 관한 특별법 시행규칙",
                            "UNDERGROUND_SAFETY_MANAGEMENT_SPECIAL_ACT_ENFORCEMENT_RULE",
                            "ENFORCEMENT_RULE"),
                    new Target(
                            "admrul",
                            "지하안전관리 업무지침",
                            "지하안전관리 업무지침",
                            "UNDERGROUND_SAFETY_MANAGEMENT_WORK_GUIDELINE",
                            "ADMINISTRATIVE_RULE"));
        }
    }

    public static class Target {
        private String target = "law";
        private String query = "";
        private String expectedName = "";
        private String actCode = "";
        private String actType = "LAW";
        private String org = "";
        private String sborg = "";
        private String knd = "";

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

        public String getOrg() {
            return org;
        }

        public void setOrg(String org) {
            this.org = org == null ? "" : org;
        }

        public String getSborg() {
            return sborg;
        }

        public void setSborg(String sborg) {
            this.sborg = sborg == null ? "" : sborg;
        }

        public String getKnd() {
            return knd;
        }

        public void setKnd(String knd) {
            this.knd = knd == null ? "" : knd;
        }
    }
}
