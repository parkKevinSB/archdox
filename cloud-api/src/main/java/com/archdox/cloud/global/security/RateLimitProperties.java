package com.archdox.cloud.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archdox.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean useForwardedHeaders = false;
    private int maxTrackedKeys = 50_000;
    private Rule login = new Rule(8, Duration.ofMinutes(1));
    private Rule signup = new Rule(4, Duration.ofMinutes(1));
    private Rule refresh = new Rule(30, Duration.ofMinutes(1));
    private Rule platformAdmin = new Rule(120, Duration.ofMinutes(1));
    private Rule officeOps = new Rule(180, Duration.ofMinutes(1));
    private Rule agentWebsocket = new Rule(20, Duration.ofMinutes(1));
    private Rule agentApi = new Rule(120, Duration.ofMinutes(1));
    private Rule upload = new Rule(60, Duration.ofMinutes(1));
    private Rule documentGeneration = new Rule(20, Duration.ofMinutes(1));
    private Rule api = new Rule(900, Duration.ofMinutes(1));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isUseForwardedHeaders() {
        return useForwardedHeaders;
    }

    public void setUseForwardedHeaders(boolean useForwardedHeaders) {
        this.useForwardedHeaders = useForwardedHeaders;
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getSignup() {
        return signup;
    }

    public void setSignup(Rule signup) {
        this.signup = signup;
    }

    public Rule getRefresh() {
        return refresh;
    }

    public void setRefresh(Rule refresh) {
        this.refresh = refresh;
    }

    public Rule getPlatformAdmin() {
        return platformAdmin;
    }

    public void setPlatformAdmin(Rule platformAdmin) {
        this.platformAdmin = platformAdmin;
    }

    public Rule getOfficeOps() {
        return officeOps;
    }

    public void setOfficeOps(Rule officeOps) {
        this.officeOps = officeOps;
    }

    public Rule getAgentWebsocket() {
        return agentWebsocket;
    }

    public void setAgentWebsocket(Rule agentWebsocket) {
        this.agentWebsocket = agentWebsocket;
    }

    public Rule getAgentApi() {
        return agentApi;
    }

    public void setAgentApi(Rule agentApi) {
        this.agentApi = agentApi;
    }

    public Rule getUpload() {
        return upload;
    }

    public void setUpload(Rule upload) {
        this.upload = upload;
    }

    public Rule getDocumentGeneration() {
        return documentGeneration;
    }

    public void setDocumentGeneration(Rule documentGeneration) {
        this.documentGeneration = documentGeneration;
    }

    public Rule getApi() {
        return api;
    }

    public void setApi(Rule api) {
        this.api = api;
    }

    public int safeMaxTrackedKeys() {
        return Math.max(1_000, maxTrackedKeys);
    }

    public record ResolvedRule(String name, int maxRequests, Duration window) {
        public long windowMillis() {
            return Math.max(1_000, window == null ? 60_000 : window.toMillis());
        }
    }

    public static class Rule {
        private boolean enabled = true;
        private int maxRequests;
        private Duration window;

        public Rule() {
        }

        public Rule(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public ResolvedRule resolve(String name) {
            if (!enabled) {
                return null;
            }
            return new ResolvedRule(
                    name,
                    Math.max(1, maxRequests),
                    window == null ? Duration.ofMinutes(1) : window);
        }
    }
}
