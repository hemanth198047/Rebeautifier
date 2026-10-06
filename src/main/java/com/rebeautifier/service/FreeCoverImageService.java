package com.rebeautifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebeautifier.domain.FreeImageSource;
import com.rebeautifier.web.dto.FreeCoverImageHit;
import com.rebeautifier.web.dto.FreeCoverImageSearchResponse;
import com.rebeautifier.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FreeCoverImageService {

    private static final String USER_AGENT =
            "Rebeautifier/0.7 (self-hosted book notes; cover picker; https://github.com/hemanth198047/Rebeautifier)";

    private static final Set<String> ALLOWED_HOST_SUFFIXES = Set.of(
            "api.openverse.org",
            "openverse.org",
            "staticflickr.com",
            "flickr.com",
            "picsum.photos",
            "fastly.picsum.photos",
            "wikimedia.org",
            "wikipedia.org",
            "upload.wikimedia.org",
            "thumb.wikimedia.org",
            "commons.wikimedia.org",
            "images.unsplash.com",
            "plus.unsplash.com",
            "images.pexels.com",
            "cdn.pixabay.com",
            "pixabay.com",
            "rawpixel.com",
            "nasa.gov",
            "metmuseum.org",
            "nypl.org",
            "archive.org",
            "wp.com",
            "wordpress.com"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String openverseBaseUrl;
    private final String picsumBaseUrl;
    private final String wikimediaApiUrl;

    public FreeCoverImageService(
            ObjectMapper objectMapper,
            @Value("${rebeautifier.free-covers.openverse-base-url:https://api.openverse.org}") String openverseBaseUrl,
            @Value("${rebeautifier.free-covers.picsum-base-url:https://picsum.photos}") String picsumBaseUrl,
            @Value("${rebeautifier.free-covers.wikimedia-api-url:https://commons.wikimedia.org/w/api.php}") String wikimediaApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.openverseBaseUrl = trimSlash(openverseBaseUrl);
        this.picsumBaseUrl = trimSlash(picsumBaseUrl);
        this.wikimediaApiUrl = wikimediaApiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Test seam. */
    FreeCoverImageService(ObjectMapper objectMapper, HttpClient httpClient,
                          String openverseBaseUrl, String picsumBaseUrl, String wikimediaApiUrl) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.openverseBaseUrl = trimSlash(openverseBaseUrl);
        this.picsumBaseUrl = trimSlash(picsumBaseUrl);
        this.wikimediaApiUrl = wikimediaApiUrl;
    }

    public FreeCoverImageSearchResponse search(FreeImageSource source, String query, int page, int pageSize) {
        FreeImageSource src = source == null ? FreeImageSource.OPENVERSE : source;
        int safePage = Math.max(1, page);
        int safeSize = Math.min(30, Math.max(1, pageSize));
        String q = query == null ? "" : query.trim();
        return switch (src) {
            case OPENVERSE -> searchOpenverse(q, safePage, safeSize);
            case PICSUM -> searchPicsum(q, safePage, safeSize);
            case WIKIMEDIA -> searchWikimedia(q, safePage, safeSize);
        };
    }

    public DownloadedImage downloadForCover(FreeImageSource source, String imageId, String imageUrlHint) {
        if (source == null || imageId == null || imageId.isBlank()) {
            throw ApiException.badRequest("source and imageId are required");
        }
        String downloadUrl = resolveDownloadUrl(source, imageId.trim(), imageUrlHint);
        return fetchImage(downloadUrl);
    }

    public record DownloadedImage(byte[] bytes, String mimeType) {
    }

    String resolveDownloadUrl(FreeImageSource source, String imageId, String imageUrlHint) {
        return switch (source) {
            case PICSUM -> picsumBaseUrl + "/id/" + sanitizeId(imageId) + "/800/1200.jpg";
            case OPENVERSE -> {
                if (imageUrlHint != null && !imageUrlHint.isBlank()) {
                    assertAllowedImageUrl(imageUrlHint);
                    yield imageUrlHint.trim();
                }
                yield fetchOpenverseFullUrl(imageId);
            }
            case WIKIMEDIA -> {
                if (imageUrlHint != null && !imageUrlHint.isBlank()) {
                    assertAllowedImageUrl(imageUrlHint);
                    yield preferWikimediaThumb(imageUrlHint.trim());
                }
                yield fetchWikimediaUrl(imageId);
            }
        };
    }

    private FreeCoverImageSearchResponse searchOpenverse(String query, int page, int pageSize) {
        String q = query.isBlank() ? "book cover library" : query;
        URI uri = URI.create(openverseBaseUrl + "/v1/images/?q=" + encode(q)
                + "&page=" + page + "&page_size=" + pageSize + "&mature=false");
        JsonNode root = getJson(uri);
        List<FreeCoverImageHit> hits = new ArrayList<>();
        JsonNode results = root.path("results");
        if (results.isArray()) {
            for (JsonNode item : results) {
                String id = text(item, "id");
                String url = text(item, "url");
                String thumb = text(item, "thumbnail");
                if (id == null || url == null) continue;
                hits.add(new FreeCoverImageHit(
                        FreeImageSource.OPENVERSE,
                        id,
                        text(item, "title"),
                        thumb != null ? thumb : url,
                        url,
                        text(item, "creator"),
                        licenseLabel(item),
                        text(item, "attribution"),
                        text(item, "foreign_landing_url"),
                        intOrNull(item, "width"),
                        intOrNull(item, "height")
                ));
            }
        }
        Integer count = root.path("result_count").isNumber() ? root.path("result_count").asInt() : null;
        return new FreeCoverImageSearchResponse(q, FreeImageSource.OPENVERSE.name(), page, pageSize, count, hits);
    }

    private FreeCoverImageSearchResponse searchPicsum(String query, int page, int pageSize) {
        URI uri = URI.create(picsumBaseUrl + "/v2/list?page=" + page + "&limit=" + pageSize);
        JsonNode root = getJson(uri);
        List<FreeCoverImageHit> hits = new ArrayList<>();
        if (root.isArray()) {
            String needle = query.toLowerCase(Locale.ROOT);
            for (JsonNode item : root) {
                String id = text(item, "id");
                String author = text(item, "author");
                if (id == null) continue;
                if (!needle.isBlank() && author != null && !author.toLowerCase(Locale.ROOT).contains(needle)
                        && !id.contains(needle)) {
                    // Picsum has no keyword search — soft-filter by author/id when a query is present.
                    continue;
                }
                String full = picsumBaseUrl + "/id/" + id + "/800/1200.jpg";
                String thumb = picsumBaseUrl + "/id/" + id + "/240/320.jpg";
                hits.add(new FreeCoverImageHit(
                        FreeImageSource.PICSUM,
                        id,
                        "Photo " + id,
                        thumb,
                        full,
                        author,
                        "Unsplash License (via Lorem Picsum)",
                        author == null ? "Photo via Lorem Picsum" : "Photo by " + author + " via Lorem Picsum",
                        text(item, "url"),
                        intOrNull(item, "width"),
                        intOrNull(item, "height")
                ));
            }
        }
        // If query filtered everything out, return the unfiltered page so the picker still works.
        if (hits.isEmpty() && !query.isBlank() && root.isArray()) {
            return searchPicsum("", page, pageSize);
        }
        return new FreeCoverImageSearchResponse(
                query.isBlank() ? "curated" : query,
                FreeImageSource.PICSUM.name(),
                page,
                pageSize,
                null,
                hits
        );
    }

    private FreeCoverImageSearchResponse searchWikimedia(String query, int page, int pageSize) {
        String q = query.isBlank() ? "library books" : query;
        int offset = (page - 1) * pageSize;
        URI uri = URI.create(wikimediaApiUrl
                + "?action=query&format=json&origin=*"
                + "&generator=search&gsrnamespace=6&gsrsearch=" + encode(q)
                + "&gsrlimit=" + pageSize + "&gsroffset=" + offset
                + "&prop=imageinfo&iiprop=url|mime|size|extmetadata|user&iiurlwidth=400");
        JsonNode root = getJson(uri);
        List<FreeCoverImageHit> hits = new ArrayList<>();
        JsonNode pages = root.path("query").path("pages");
        if (pages.isObject()) {
            pages.fields().forEachRemaining(entry -> {
                JsonNode pageNode = entry.getValue();
                JsonNode infoArr = pageNode.path("imageinfo");
                if (!infoArr.isArray() || infoArr.isEmpty()) return;
                JsonNode info = infoArr.get(0);
                String mime = text(info, "mime");
                if (mime != null && !mime.startsWith("image/")) return;
                String title = text(pageNode, "title");
                String pageId = pageNode.path("pageid").asText(null);
                String full = stripTracking(text(info, "url"));
                String thumb = stripTracking(text(info, "thumburl"));
                if (pageId == null || full == null) return;
                String artist = extractExtMeta(info, "Artist");
                if (artist == null) artist = text(info, "user");
                String license = extractExtMeta(info, "LicenseShortName");
                hits.add(new FreeCoverImageHit(
                        FreeImageSource.WIKIMEDIA,
                        pageId,
                        title == null ? "Wikimedia image" : title.replaceFirst("^File:", ""),
                        thumb != null ? thumb : full,
                        full,
                        artist == null ? null : artist.replaceAll("<[^>]+>", "").trim(),
                        license,
                        attribution(title, artist, license),
                        text(info, "descriptionurl"),
                        intOrNull(info, "width"),
                        intOrNull(info, "height")
                ));
            });
        }
        return new FreeCoverImageSearchResponse(q, FreeImageSource.WIKIMEDIA.name(), page, pageSize, null, hits);
    }

    private String fetchOpenverseFullUrl(String imageId) {
        URI uri = URI.create(openverseBaseUrl + "/v1/images/" + encode(sanitizeId(imageId)) + "/");
        JsonNode root = getJson(uri);
        String url = text(root, "url");
        if (url == null || url.isBlank()) {
            throw new ApiException("COVER_FETCH_FAILED", "Openverse image URL not found", HttpStatus.BAD_GATEWAY);
        }
        assertAllowedImageUrl(url);
        return url;
    }

    private String fetchWikimediaUrl(String pageId) {
        URI uri = URI.create(wikimediaApiUrl
                + "?action=query&format=json&origin=*&pageids=" + encode(sanitizeId(pageId))
                + "&prop=imageinfo&iiprop=url|mime&iiurlwidth=900");
        JsonNode root = getJson(uri);
        JsonNode pages = root.path("query").path("pages");
        if (!pages.isObject() || pages.isEmpty()) {
            throw new ApiException("COVER_FETCH_FAILED", "Wikimedia image not found", HttpStatus.BAD_GATEWAY);
        }
        JsonNode page = pages.elements().next();
        JsonNode infoArr = page.path("imageinfo");
        if (!infoArr.isArray() || infoArr.isEmpty()) {
            throw new ApiException("COVER_FETCH_FAILED", "Wikimedia image not found", HttpStatus.BAD_GATEWAY);
        }
        JsonNode info = infoArr.get(0);
        String thumb = stripTracking(text(info, "thumburl"));
        String full = stripTracking(text(info, "url"));
        String chosen = thumb != null ? thumb : full;
        if (chosen == null) {
            throw new ApiException("COVER_FETCH_FAILED", "Wikimedia image URL missing", HttpStatus.BAD_GATEWAY);
        }
        assertAllowedImageUrl(chosen);
        return chosen;
    }

    DownloadedImage fetchImage(String url) {
        assertAllowedImageUrl(url);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/*,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("COVER_FETCH_FAILED",
                        "Could not download image (HTTP " + response.statusCode() + ")",
                        HttpStatus.BAD_GATEWAY);
            }
            // Re-check final URI after redirects
            assertAllowedImageUrl(response.uri().toString());
            String contentType = response.headers().firstValue("Content-Type").orElse("").split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!contentType.isBlank() && !contentType.startsWith("image/")) {
                throw ApiException.badRequest("Remote URL did not return an image");
            }
            byte[] bytes;
            try (InputStream in = response.body()) {
                bytes = in.readNBytes((int) CoverStorageService.MAX_BYTES + 1);
            }
            if (bytes.length == 0) {
                throw ApiException.badRequest("Downloaded image was empty");
            }
            if (bytes.length > CoverStorageService.MAX_BYTES) {
                throw ApiException.badRequest("Cover must be 5MB or smaller");
            }
            if (contentType.isBlank() || !CoverStorageService.isAllowedMime(contentType)) {
                contentType = sniffMime(bytes);
            }
            if (!CoverStorageService.isAllowedMime(contentType)) {
                throw ApiException.badRequest("Cover must be JPEG, PNG, WebP, or GIF");
            }
            return new DownloadedImage(bytes, contentType);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("COVER_FETCH_FAILED", "Could not download free cover image", HttpStatus.BAD_GATEWAY);
        }
    }

    public String sniffMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    void assertAllowedImageUrl(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            throw ApiException.badRequest("Invalid image URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw ApiException.badRequest("Only HTTPS image URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw ApiException.badRequest("Image URL host is missing");
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (!isAllowedHost(lower)) {
            throw ApiException.badRequest("Image host is not on the free-cover allowlist: " + host);
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw ApiException.badRequest("Image host resolves to a private address");
                }
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException("COVER_FETCH_FAILED", "Could not resolve image host", HttpStatus.BAD_GATEWAY);
        }
    }

    static boolean isAllowedHost(String hostLower) {
        for (String suffix : ALLOWED_HOST_SUFFIXES) {
            if (hostLower.equals(suffix) || hostLower.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode getJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException("COVER_SEARCH_FAILED",
                        "Free image search failed (HTTP " + response.statusCode() + ")",
                        HttpStatus.BAD_GATEWAY);
            }
            return objectMapper.readTree(response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("COVER_SEARCH_FAILED", "Free image search is unavailable right now", HttpStatus.BAD_GATEWAY);
        }
    }

    private static String preferWikimediaThumb(String url) {
        // Prefer an 800px commons thumb when a full upload URL is provided.
        if (url.contains("/wikipedia/commons/") && !url.contains("/thumb/")) {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                // /wikipedia/commons/a/ab/File.jpg → /wikipedia/commons/thumb/a/ab/File.jpg/800px-File.jpg
                int last = path.lastIndexOf('/');
                if (last > 0) {
                    String file = path.substring(last + 1);
                    String prefix = path.substring(0, last);
                    String thumbPath = prefix.replace("/wikipedia/commons/", "/wikipedia/commons/thumb/")
                            + "/" + file + "/800px-" + file;
                    return "https://upload.wikimedia.org" + thumbPath;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return url;
    }

    private static String licenseLabel(JsonNode item) {
        String license = text(item, "license");
        String version = text(item, "license_version");
        if (license == null) return null;
        return version == null ? "CC " + license : "CC " + license + " " + version;
    }

    private static String extractExtMeta(JsonNode info, String key) {
        JsonNode node = info.path("extmetadata").path(key).path("value");
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String attribution(String title, String artist, String license) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title.replaceFirst("^File:", ""));
        if (artist != null && !artist.isBlank()) {
            if (!sb.isEmpty()) sb.append(" — ");
            sb.append(artist.replaceAll("<[^>]+>", "").trim());
        }
        if (license != null && !license.isBlank()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(license);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String sanitizeId(String id) {
        if (!id.matches("[A-Za-z0-9._\\-]+")) {
            throw ApiException.badRequest("Invalid image id");
        }
        return id;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripTracking(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) return value;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
