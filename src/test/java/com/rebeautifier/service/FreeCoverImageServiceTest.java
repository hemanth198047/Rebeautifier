package com.rebeautifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebeautifier.domain.FreeImageSource;
import com.rebeautifier.web.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreeCoverImageServiceTest {

    private FreeCoverImageService service;

    @BeforeEach
    void setUp() {
        service = new FreeCoverImageService(
                new ObjectMapper(),
                HttpClient.newHttpClient(),
                "https://api.openverse.org",
                "https://picsum.photos",
                "https://commons.wikimedia.org/w/api.php"
        );
    }

    @Test
    void allowsKnownFreeImageHosts() {
        assertThat(FreeCoverImageService.isAllowedHost("api.openverse.org")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("live.staticflickr.com")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("picsum.photos")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("fastly.picsum.photos")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("upload.wikimedia.org")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("thumb.wikimedia.org")).isTrue();
        assertThat(FreeCoverImageService.isAllowedHost("images.unsplash.com")).isTrue();
    }

    @Test
    void rejectsUnknownAndLocalHosts() {
        assertThat(FreeCoverImageService.isAllowedHost("evil.example")).isFalse();
        assertThat(FreeCoverImageService.isAllowedHost("localhost")).isFalse();
        assertThat(FreeCoverImageService.isAllowedHost("127.0.0.1")).isFalse();
        assertThatThrownBy(() -> service.assertAllowedImageUrl("http://picsum.photos/id/1/200/300"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> service.assertAllowedImageUrl("https://evil.example/a.jpg"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void buildsPicsumDownloadUrl() {
        String url = service.resolveDownloadUrl(FreeImageSource.PICSUM, "42", null);
        assertThat(url).isEqualTo("https://picsum.photos/id/42/800/1200.jpg");
    }

    @Test
    void sniffsCommonImageMimeTypes() {
        assertThat(service.sniffMime(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isEqualTo("image/jpeg");
        assertThat(service.sniffMime(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}))
                .isEqualTo("image/png");
        assertThat(service.sniffMime(new byte[] {'G', 'I', 'F', '8', '9', 'a'})).isEqualTo("image/gif");
    }
}
