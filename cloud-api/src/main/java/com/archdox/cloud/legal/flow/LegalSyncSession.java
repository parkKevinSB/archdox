package com.archdox.cloud.legal.flow;

import com.archdox.cloud.legal.application.LegalSourceSnapshot;
import com.archdox.cloud.legal.application.LegalSyncResult;

public final class LegalSyncSession {
    private LegalSourceSnapshot snapshot;
    private LegalSyncResult result;

    public LegalSourceSnapshot snapshot() {
        return snapshot;
    }

    public void snapshot(LegalSourceSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public LegalSyncResult result() {
        return result;
    }

    public void result(LegalSyncResult result) {
        this.result = result;
    }
}
