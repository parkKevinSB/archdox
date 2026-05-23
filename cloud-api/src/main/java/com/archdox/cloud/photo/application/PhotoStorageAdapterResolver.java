package com.archdox.cloud.photo.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PhotoStorageAdapterResolver {
    private final PhotoStorageProperties properties;
    private final List<PhotoStorageAdapter> adapters;

    public PhotoStorageAdapterResolver(PhotoStorageProperties properties, List<PhotoStorageAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters;
    }

    public PhotoUploadTarget configuredUploadTarget() {
        return properties.getUploadTarget();
    }

    public PhotoStorageAdapter forConfiguredTarget() {
        return forTarget(properties.getUploadTarget());
    }

    public PhotoStorageAdapter forTarget(PhotoUploadTarget target) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(target))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Photo upload target is not supported: " + target));
    }

    public PhotoStorageAdapter forStorageKind(PhotoStorageKind storageKind) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(storageKind))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Photo storage kind is not supported: " + storageKind));
    }
}
