package com.matrix.search.crawler.filter;

import com.matrix.search.config.CrawlerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Canonicalizes and filters URLs before they enter the crawl frontier.
 *
 * <p>A URL is accepted only when it is {@code http(s)}, its host is the allowed domain
 * itself or one of its subdomains, and its path does not point to a non-HTML resource.
 * Accepted URLs are canonicalized (lowercase scheme/host, no fragment, no default port,
 * no trailing slash, tracking parameters stripped) so that equivalent URLs deduplicate
 * to a single frontier entry.</p>
 */
@Component
@RequiredArgsConstructor
public class UrlFilter {

    /** File extensions that are never useful for full-text indexing. */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            // Documents
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            // Images
            "jpg", "jpeg", "png", "gif", "svg", "webp", "ico", "bmp", "tif", "tiff",
            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2",
            // Media
            "mp3", "mp4", "avi", "wmv", "mov", "flv", "webm",
            // Executables
            "exe", "msi", "apk", "iso", "jar", "war");

    /** Exact query parameter names that only carry tracking data. */
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "fbclid", "gclid", "yclid", "msclkid", "mc_cid", "mc_eid");

    private static final String UTM_PARAM_PREFIX = "utm_";

    private final CrawlerConfig config;

    /**
     * Resolves {@code rawUrl} against {@code baseUrl} (or treats it as absolute when the
     * base is {@code null}), canonicalizes it and checks crawl eligibility.
     *
     * @return the canonical absolute URL, or empty when the URL must not be crawled
     */
    public Optional<String> normalizeAndFilter(String rawUrl, String baseUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String trimmed = rawUrl.trim();
            URI resolved = baseUrl != null && !baseUrl.isBlank()
                    ? URI.create(baseUrl.trim()).resolve(trimmed)
                    : URI.create(trimmed);
            return canonicalize(resolved);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks whether the host is the allowed domain itself or one of its subdomains.
     */
    public boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        String suffix = config.getAllowedDomainSuffix().toLowerCase(Locale.ROOT);
        return normalized.equals(suffix) || normalized.endsWith("." + suffix);
    }

    /**
     * Extracts the subdomain label of the allowed domain from a host name.
     * For the bare domain (e.g. {@code hust.edu.vn}) the host itself is returned.
     */
    public String extractSubdomain(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        String suffix = config.getAllowedDomainSuffix().toLowerCase(Locale.ROOT);
        if (normalized.equals(suffix)) {
            return normalized;
        }
        return normalized.substring(0, normalized.length() - suffix.length() - 1);
    }

    private Optional<String> canonicalize(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.empty();
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }

        String host = uri.getHost();
        if (host == null || !isAllowedHost(host)) {
            return Optional.empty();
        }

        // Only default ports are crawled so port variants cannot duplicate content.
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            return Optional.empty();
        }

        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (hasBlockedExtension(path)) {
            return Optional.empty();
        }

        String query = cleanQuery(uri.getRawQuery());

        String canonical = scheme + "://" + host.toLowerCase(Locale.ROOT) + path;
        if (query != null) {
            canonical = canonical + "?" + query;
        }
        return Optional.of(canonical);
    }

    private boolean hasBlockedExtension(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        int lastSlash = lowerPath.lastIndexOf('/');
        int lastDot = lowerPath.lastIndexOf('.');
        return lastDot > lastSlash && BLOCKED_EXTENSIONS.contains(lowerPath.substring(lastDot + 1));
    }

    /**
     * Removes known tracking parameters; returns {@code null} when nothing remains.
     */
    private String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String param : rawQuery.split("&")) {
            if (param.isEmpty()) {
                continue;
            }
            String name = param.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (name.startsWith(UTM_PARAM_PREFIX) || TRACKING_PARAMS.contains(name)) {
                continue;
            }
            kept.add(param);
        }
        return kept.isEmpty() ? null : String.join("&", kept);
    }
}
