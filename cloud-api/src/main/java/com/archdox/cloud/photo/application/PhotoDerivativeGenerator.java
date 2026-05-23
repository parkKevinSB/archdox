package com.archdox.cloud.photo.application;

import com.archdox.cloud.global.api.BadRequestException;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class PhotoDerivativeGenerator {
    public static final int WORKING_LONG_EDGE = 2048;
    public static final int THUMBNAIL_LONG_EDGE = 512;

    public GeneratedImage createWorking(InputStream source, String sourceMimeType) throws IOException {
        var image = read(source);
        var resized = resize(image, WORKING_LONG_EDGE, outputHasAlpha(sourceMimeType));
        var mimeType = normalizeWorkingMime(sourceMimeType);
        return write(resized, mimeType);
    }

    public GeneratedImage createThumbnail(InputStream source) throws IOException {
        var image = read(source);
        var resized = resize(image, THUMBNAIL_LONG_EDGE, false);
        return write(resized, "image/webp");
    }

    private BufferedImage read(InputStream source) throws IOException {
        var image = ImageIO.read(source);
        if (image == null) {
            throw new BadRequestException("Unsupported photo image format");
        }
        return image;
    }

    private BufferedImage resize(BufferedImage source, int maxLongEdge, boolean preserveAlpha) {
        var width = source.getWidth();
        var height = source.getHeight();
        var scale = Math.min(1.0d, (double) maxLongEdge / Math.max(width, height));
        var targetWidth = Math.max(1, (int) Math.round(width * scale));
        var targetHeight = Math.max(1, (int) Math.round(height * scale));
        var type = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var target = new BufferedImage(targetWidth, targetHeight, type);
        var graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!preserveAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private GeneratedImage write(BufferedImage image, String mimeType) throws IOException {
        var format = formatName(mimeType);
        var output = new ByteArrayOutputStream();
        var written = ImageIO.write(image, format, output);
        if (!written) {
            throw new BadRequestException("No ImageIO writer for " + mimeType);
        }
        var bytes = output.toByteArray();
        return new GeneratedImage(
                bytes,
                mimeType,
                bytes.length,
                image.getWidth(),
                image.getHeight(),
                "sha256:" + sha256(bytes));
    }

    private String normalizeWorkingMime(String sourceMimeType) {
        var mimeType = sourceMimeType == null ? "" : sourceMimeType.toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "image/png" -> "image/png";
            case "image/webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private boolean outputHasAlpha(String mimeType) {
        return "image/png".equalsIgnoreCase(mimeType) || "image/webp".equalsIgnoreCase(mimeType);
    }

    private String formatName(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpeg";
        };
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    public ByteArrayInputStream asInputStream(GeneratedImage image) {
        return new ByteArrayInputStream(image.bytes());
    }

    public record GeneratedImage(
            byte[] bytes,
            String mimeType,
            long bytesLength,
            int width,
            int height,
            String hashSha256
    ) {
    }
}
