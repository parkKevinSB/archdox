package com.archdox.cloud.storage.application;

import com.archdox.cloud.global.api.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StorageServiceResolver {
    private final StorageProperties properties;
    private final List<StorageService> services;

    public StorageServiceResolver(StorageProperties properties, List<StorageService> services) {
        this.properties = properties;
        this.services = services;
    }

    public StorageService active() {
        return forType(properties.getActive());
    }

    public StorageService forType(StorageType storageType) {
        return services.stream()
                .filter(service -> service.supports(storageType))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Storage type is not supported: " + storageType));
    }
}
