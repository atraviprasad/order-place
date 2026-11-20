package com.orderplace.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(ImageType.detect(png)).contains(ImageType.PNG);
    }

    @Test
    void detectsJpeg() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(ImageType.detect(jpg)).contains(ImageType.JPEG);
    }

    @Test
    void detectsWebp() {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(ImageType.detect(webp)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsTextAndTooShortData() {
        assertThat(ImageType.detect("this is not an image".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageType.detect(new byte[] {1, 2, 3})).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}
