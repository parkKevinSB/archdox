package com.archdox.cloud.photo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoDerivativeGeneratorTest {
    private final PhotoDerivativeGenerator generator = new PhotoDerivativeGenerator();

    @Test
    void createsResizedWorkingImage() throws Exception {
        var source = jpeg(3000, 1500);

        var working = generator.createWorking(new ByteArrayInputStream(source), "image/jpeg");

        assertEquals("image/jpeg", working.mimeType());
        assertEquals(PhotoDerivativeGenerator.WORKING_LONG_EDGE, Math.max(working.width(), working.height()));
        assertTrue(working.bytesLength() > 0);
        assertTrue(working.hashSha256().startsWith("sha256:"));
    }

    @Test
    void createsWebpThumbnail() throws Exception {
        var source = jpeg(1600, 1200);

        var thumbnail = generator.createThumbnail(new ByteArrayInputStream(source));

        assertEquals("image/webp", thumbnail.mimeType());
        assertTrue(Math.max(thumbnail.width(), thumbnail.height()) <= PhotoDerivativeGenerator.THUMBNAIL_LONG_EDGE);
        assertTrue(thumbnail.bytesLength() > 0);
        assertTrue(thumbnail.hashSha256().startsWith("sha256:"));
    }

    private byte[] jpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
