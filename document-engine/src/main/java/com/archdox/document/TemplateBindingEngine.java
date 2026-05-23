package com.archdox.document;

import java.nio.file.Path;
import java.util.Map;

public interface TemplateBindingEngine {
    Path bind(TemplateSpec template, Map<String, Object> payload, Path workspace);
}
