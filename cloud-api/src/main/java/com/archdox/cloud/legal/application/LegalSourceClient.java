package com.archdox.cloud.legal.application;

public interface LegalSourceClient {
    boolean supports(String sourceCode);

    LegalSourceSnapshot fetch(String sourceCode);
}
