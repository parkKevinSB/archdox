package com.archdox.document;

public record PhotoAsset(
        String photoId,
        String checklistItemKey,
        String storageRef,
        String caption,
        PhotoLayoutSize layoutSize
) {
}
