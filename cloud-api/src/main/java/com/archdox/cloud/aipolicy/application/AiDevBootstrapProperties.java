package com.archdox.cloud.aipolicy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai.dev-bootstrap")
public class AiDevBootstrapProperties {
    private boolean enabled;
    private boolean attachToExistingOffices = true;
    private boolean documentReviewAiEnabled = true;
    private boolean documentGenerationAiEnabled;
    private String providerCode = "fake-review";
    private String providerDisplayName = "Development Fake AI";
    private String defaultModel = "fake-review-model";
    private String opsProviderCode = "fake-ops";
    private String opsProviderDisplayName = "Development Fake Ops AI";
    private String opsDefaultModel = "fake-ops-model";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAttachToExistingOffices() {
        return attachToExistingOffices;
    }

    public void setAttachToExistingOffices(boolean attachToExistingOffices) {
        this.attachToExistingOffices = attachToExistingOffices;
    }

    public boolean isDocumentReviewAiEnabled() {
        return documentReviewAiEnabled;
    }

    public void setDocumentReviewAiEnabled(boolean documentReviewAiEnabled) {
        this.documentReviewAiEnabled = documentReviewAiEnabled;
    }

    public boolean isDocumentGenerationAiEnabled() {
        return documentGenerationAiEnabled;
    }

    public void setDocumentGenerationAiEnabled(boolean documentGenerationAiEnabled) {
        this.documentGenerationAiEnabled = documentGenerationAiEnabled;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderDisplayName() {
        return providerDisplayName;
    }

    public void setProviderDisplayName(String providerDisplayName) {
        this.providerDisplayName = providerDisplayName;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getOpsProviderCode() {
        return opsProviderCode;
    }

    public void setOpsProviderCode(String opsProviderCode) {
        this.opsProviderCode = opsProviderCode;
    }

    public String getOpsProviderDisplayName() {
        return opsProviderDisplayName;
    }

    public void setOpsProviderDisplayName(String opsProviderDisplayName) {
        this.opsProviderDisplayName = opsProviderDisplayName;
    }

    public String getOpsDefaultModel() {
        return opsDefaultModel;
    }

    public void setOpsDefaultModel(String opsDefaultModel) {
        this.opsDefaultModel = opsDefaultModel;
    }

    public String providerCode() {
        return required(providerCode, "fake-review");
    }

    public String providerDisplayName() {
        return required(providerDisplayName, "Development Fake AI");
    }

    public String defaultModel() {
        return required(defaultModel, "fake-review-model");
    }

    public String opsProviderCode() {
        return required(opsProviderCode, "fake-ops");
    }

    public String opsProviderDisplayName() {
        return required(opsProviderDisplayName, "Development Fake Ops AI");
    }

    public String opsDefaultModel() {
        return required(opsDefaultModel, "fake-ops-model");
    }

    private String required(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
