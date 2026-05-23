package com.archdox.document;

import java.nio.file.Path;

public interface PdfConverter {
    Path convert(Path docxPath, Path outputDirectory);
}
