package com.archdox.document;

public record PhotoAsset(
        String photoId,
        String checklistItemKey,
        String storageRef,
        String caption,
        PhotoLayoutSize layoutSize,
        String mimeType,
        String downloadUrl
) {
    public PhotoAsset(
            String photoId,
            String checklistItemKey,
            String storageRef,
            String caption,
            PhotoLayoutSize layoutSize
    ) {
        this(photoId, checklistItemKey, storageRef, caption, layoutSize, null, null);
    }
}
