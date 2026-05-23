package com.archdox.document;

public interface DocumentEngine {
    DocumentGenerationResult generate(DocumentGenerationRequest request);
}
