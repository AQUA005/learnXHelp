package com.ustc.learnx.common;

import java.util.Base64;
import java.util.Map;

/**
 * Decodes the {@code data:} URLs that images are uploaded as.
 *
 * <p>Avatars, university logos and the platform's own logo all arrive this way,
 * and all need the same three guarantees: that the string really is a base64
 * data URL, that its declared type is one we are willing to serve back, and
 * that it is not large enough to fill the disk. Getting any of those wrong on
 * one upload path and right on another is the failure this exists to prevent.
 *
 * <p>SVG is deliberately absent. {@code FileStorageService} accepts it for
 * study material, but an SVG is a script carrier and these images are rendered
 * on pages that anyone can visit.
 */
public final class ImageDataUrl {

    /** Image types accepted, mapped to the extension to store them under. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    private ImageDataUrl() {
    }

    /** The decoded bytes and the extension they should be stored under. */
    public record DecodedImage(byte[] bytes, String extension) {
    }

    /**
     * Decodes {@code dataUrl}, or explains why it will not be accepted.
     *
     * @param subject   what the image is, used to word the rejection ("Logo must be…")
     * @param maxBytes  the largest image to accept
     * @throws ValidationException if the string is not an acceptable image
     */
    public static DecodedImage decode(String dataUrl, String subject, int maxBytes) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            throw new ValidationException(subject + " must be an uploaded image");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new ValidationException(subject + " is not a valid image");
        }

        String header = dataUrl.substring(5, comma).toLowerCase();
        if (!header.contains(";base64")) {
            throw new ValidationException(subject + " must be a base64 encoded image");
        }
        String mimeType = header.substring(0, header.indexOf(';'));
        String extension = ALLOWED_TYPES.get(mimeType);
        if (extension == null) {
            throw new ValidationException(subject + " must be a PNG, JPEG, GIF or WebP image");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(subject + " is not valid base64 data");
        }
        if (decoded.length > maxBytes) {
            throw new ValidationException(
                    subject + " must be smaller than " + (maxBytes / (1024 * 1024)) + " MB");
        }

        return new DecodedImage(decoded, extension);
    }

    /** The media type to serve a stored image back as, inferred from its key. */
    public static String contentTypeFor(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
