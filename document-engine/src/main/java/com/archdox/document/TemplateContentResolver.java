package com.archdox.document;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface TemplateContentResolver {
    Optional<byte[]> resolve(TemplateSpec template) throws IOException;
}
