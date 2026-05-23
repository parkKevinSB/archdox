package com.archdox.cloud.photo.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PhotoStorageRefFactory {
    PhotoStorageRefs create(Long officeId, Long projectId, Long reportId, String mimeType) {
        var parent = reportId == null ? "projects/" + projectId : "reports/" + reportId;
        var photoKey = UUID.randomUUID().toString();
        var extension = extensionFor(mimeType);
        var base = "offices/%d/%s/photos/%s".formatted(officeId, parent, photoKey);
        return new PhotoStorageRefs(
                base + "/original." + extension,
                base + "/working." + extension,
                base + "/thumbnail.webp");
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }
}
