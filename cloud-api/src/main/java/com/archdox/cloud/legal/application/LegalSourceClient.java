package com.archdox.cloud.legal.application;

import java.util.concurrent.CompletableFuture;

public interface LegalSourceClient {
    boolean supports(String sourceCode);

    LegalSourceSnapshot fetch(String sourceCode);

    default boolean nativeAsyncFetchSupported() {
        return false;
    }

    default CompletableFuture<LegalSourceSnapshot> fetchAsync(String sourceCode) {
        try {
            return CompletableFuture.completedFuture(fetch(sourceCode));
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
}
