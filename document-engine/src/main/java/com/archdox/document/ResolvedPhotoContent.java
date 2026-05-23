package com.archdox.document;

public record ResolvedPhotoContent(
        byte[] content,
        String mimeType
) {
}
