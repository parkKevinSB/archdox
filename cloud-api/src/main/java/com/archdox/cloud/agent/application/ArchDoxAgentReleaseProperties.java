package com.archdox.cloud.agent.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.agent.release")
public class ArchDoxAgentReleaseProperties {
    private String publicBaseUrl;
    private String objectPrefix = "releases";
    private String runtimePathTemplate =
            "agent-runtime/{channel}/{platform}/{version}/archdox-agent-runtime-{platform}-{version}.zip";
    private String launcherPathTemplate =
            "agent-launcher/{channel}/{platform}/{version}/archdox-agent-launcher-{platform}-{version}.zip";
    private String launcherPackageDownloadUrl;
    private String launcherPackageSha256;
    private String launcherPackageSignatureUrl;
    private String launcherReleaseNotesUrl;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    public String getRuntimePathTemplate() {
        return runtimePathTemplate;
    }

    public void setRuntimePathTemplate(String runtimePathTemplate) {
        this.runtimePathTemplate = runtimePathTemplate;
    }

    public String getLauncherPathTemplate() {
        return launcherPathTemplate;
    }

    public void setLauncherPathTemplate(String launcherPathTemplate) {
        this.launcherPathTemplate = launcherPathTemplate;
    }

    public String getLauncherPackageDownloadUrl() {
        return launcherPackageDownloadUrl;
    }

    public void setLauncherPackageDownloadUrl(String launcherPackageDownloadUrl) {
        this.launcherPackageDownloadUrl = launcherPackageDownloadUrl;
    }

    public String getLauncherPackageSha256() {
        return launcherPackageSha256;
    }

    public void setLauncherPackageSha256(String launcherPackageSha256) {
        this.launcherPackageSha256 = launcherPackageSha256;
    }

    public String getLauncherPackageSignatureUrl() {
        return launcherPackageSignatureUrl;
    }

    public void setLauncherPackageSignatureUrl(String launcherPackageSignatureUrl) {
        this.launcherPackageSignatureUrl = launcherPackageSignatureUrl;
    }

    public String getLauncherReleaseNotesUrl() {
        return launcherReleaseNotesUrl;
    }

    public void setLauncherReleaseNotesUrl(String launcherReleaseNotesUrl) {
        this.launcherReleaseNotesUrl = launcherReleaseNotesUrl;
    }

    public String optionalPublicBaseUrl() {
        return blankToNull(publicBaseUrl);
    }

    public String safeObjectPrefix() {
        return trimSlashes(nonBlank(objectPrefix, "releases"));
    }

    public String safeRuntimePathTemplate() {
        return nonBlank(runtimePathTemplate,
                "agent-runtime/{channel}/{platform}/{version}/archdox-agent-runtime-{platform}-{version}.zip");
    }

    public String safeLauncherPathTemplate() {
        return nonBlank(launcherPathTemplate,
                "agent-launcher/{channel}/{platform}/{version}/archdox-agent-launcher-{platform}-{version}.zip");
    }

    public String optionalLauncherPackageDownloadUrl() {
        return blankToNull(launcherPackageDownloadUrl);
    }

    public String optionalLauncherPackageSha256() {
        return blankToNull(launcherPackageSha256);
    }

    public String optionalLauncherPackageSignatureUrl() {
        return blankToNull(launcherPackageSignatureUrl);
    }

    public String optionalLauncherReleaseNotesUrl() {
        return blankToNull(launcherReleaseNotesUrl);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimSlashes(String value) {
        var result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
