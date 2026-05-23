package com.archdox.document;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface PhotoContentResolver {
    Optional<ResolvedPhotoContent> resolve(PhotoAsset photo) throws IOException;
}
