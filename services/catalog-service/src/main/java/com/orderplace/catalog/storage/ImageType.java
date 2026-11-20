package com.orderplace.catalog.storage;

import java.util.Optional;

/** Image types we accept. Detected from the first bytes of the file, not from the (spoofable) client header. */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mime;
    private final String extension;

    ImageType(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() { return mime; }

    public String extension() { return extension; }

    public static Optional<ImageType> detect(byte[] d) {
        if (d == null || d.length < 12) return Optional.empty();
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return Optional.of(JPEG);
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return Optional.of(PNG);
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return Optional.of(WEBP);
        return Optional.empty();
    }
}
