package com.archdox.agent.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.documents.export")
public class DocumentExportProperties {
    private LibreOffice libreOffice = new LibreOffice();

    public LibreOffice getLibreOffice() {
        return libreOffice;
    }

    public void setLibreOffice(LibreOffice libreOffice) {
        this.libreOffice = libreOffice == null ? new LibreOffice() : libreOffice;
    }

    public static class LibreOffice {
        private boolean enabled;
        private String executablePath = "soffice";
        private long timeoutMs = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExecutablePath() {
            return executablePath;
        }

        public void setExecutablePath(String executablePath) {
            this.executablePath = executablePath;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
