package com.archdox.cloud.photo.infra;

import com.archdox.cloud.photo.application.PhotoStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

@Component
public class PhotoLocalObjectStore {
    private final Path root;

    public PhotoLocalObjectStore(PhotoStorageProperties properties) {
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
    }

    public void write(String storageRef, InputStream input) throws IOException {
        var target = resolve(storageRef);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean exists(String storageRef) {
        return Files.exists(resolve(storageRef));
    }

    public InputStream open(String storageRef) throws IOException {
        return Files.newInputStream(resolve(storageRef));
    }

    public long size(String storageRef) throws IOException {
        return Files.size(resolve(storageRef));
    }

    public void copyTo(String storageRef, OutputStream output) throws IOException {
        try (var input = open(storageRef)) {
            input.transferTo(output);
        }
    }

    public void deleteIfExists(String storageRef) throws IOException {
        Files.deleteIfExists(resolve(storageRef));
    }

    private Path resolve(String storageRef) {
        var target = root.resolve(storageRef).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage reference");
        }
        return target;
    }
}
