package com.archdox.cloud.platformadmin.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-admin")
public class PlatformAdminProperties {
    private List<String> bootstrapSuperAdminEmails = new ArrayList<>();

    public List<String> getBootstrapSuperAdminEmails() {
        return bootstrapSuperAdminEmails;
    }

    public void setBootstrapSuperAdminEmails(List<String> bootstrapSuperAdminEmails) {
        this.bootstrapSuperAdminEmails = bootstrapSuperAdminEmails == null ? new ArrayList<>() : bootstrapSuperAdminEmails;
    }
}
