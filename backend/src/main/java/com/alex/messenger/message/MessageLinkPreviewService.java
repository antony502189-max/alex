package com.alex.messenger.message;

import com.alex.messenger.message.dto.LinkPreviewResponse;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageLinkPreviewService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"']+");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_PATTERN = Pattern.compile(
            "(?is)<meta[^>]+(?:property|name)=[\"']([^\"']+)[\"'][^>]+content=[\"']([^\"']*)[\"'][^>]*>"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration SUCCESS_TTL = Duration.ofHours(6);

    private final LinkPreviewCacheRepository linkPreviewCacheRepository;

    public LinkPreviewResponse resolvePreview(MessageTextContent content) {
        if (content == null || content.disableLinkPreview()) {
            return null;
        }
        String url = extractFirstUrl(content);
        if (url == null) {
            return null;
        }

        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null || !isSafeUrl(normalizedUrl)) {
            return null;
        }

        Instant now = Instant.now();
        LinkPreviewCacheEntity cached = linkPreviewCacheRepository.findById(normalizedUrl).orElse(null);
        if (cached != null && cached.getExpiresAt() != null && cached.getExpiresAt().isAfter(now)) {
            return toResponse(cached);
        }

        LinkPreviewResponse fetched = fetchPreview(normalizedUrl);
        if (fetched == null) {
            return cached != null ? toResponse(cached) : null;
        }

        LinkPreviewCacheEntity entity = cached != null ? cached : new LinkPreviewCacheEntity();
        entity.setNormalizedUrl(normalizedUrl);
        entity.setCanonicalUrl(fetched.canonicalUrl());
        entity.setTitle(trimToNull(fetched.title()));
        entity.setDescription(trimToNull(fetched.description()));
        entity.setSiteName(trimToNull(fetched.siteName()));
        entity.setImageUrl(trimToNull(fetched.imageUrl()));
        entity.setSuccess(true);
        entity.setFetchedAt(fetched.fetchedAt());
        entity.setExpiresAt(fetched.expiresAt());
        linkPreviewCacheRepository.save(entity);
        return fetched;
    }

    private LinkPreviewResponse fetchPreview(String normalizedUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(FETCH_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedUrl))
                    .timeout(FETCH_TIMEOUT)
                    .header("User-Agent", "alex-messenger-link-preview/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return null;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            if (body.length() > 131072) {
                body = body.substring(0, 131072);
            }

            String title = extractTitle(body);
            String description = extractMeta(body, "description", "og:description", "twitter:description");
            String siteName = extractMeta(body, "og:site_name", "application-name");
            String imageUrl = normalizeUrl(extractMeta(body, "og:image", "twitter:image"));
            Instant fetchedAt = Instant.now();
            return new LinkPreviewResponse(
                    normalizedUrl,
                    normalizedUrl,
                    title,
                    description,
                    siteName,
                    imageUrl,
                    fetchedAt,
                    fetchedAt.plus(SUCCESS_TTL)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private LinkPreviewResponse toResponse(LinkPreviewCacheEntity entity) {
        return new LinkPreviewResponse(
                entity.getNormalizedUrl(),
                entity.getCanonicalUrl(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getSiteName(),
                entity.getImageUrl(),
                entity.getFetchedAt(),
                entity.getExpiresAt()
        );
    }

    private String extractFirstUrl(MessageTextContent content) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (content.entities() != null) {
            for (MessageTextEntityPayload entity : content.entities()) {
                if (entity == null) {
                    continue;
                }
                String type = entity.type() != null ? entity.type().trim().toUpperCase(Locale.ROOT) : "";
                if ("TEXT_LINK".equals(type) && entity.value() != null) {
                    candidates.add(entity.value());
                }
            }
        }
        candidates.addAll(extractUrls(content.text()));
        if (content.caption() != null && !content.caption().equals(content.text())) {
            candidates.addAll(extractUrls(content.caption()));
        }
        return candidates.stream()
                .map(this::normalizeUrl)
                .filter(this::isSafeUrl)
                .findFirst()
                .orElse(null);
    }

    private List<String> extractUrls(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(value);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    private String normalizeUrl(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String normalizedHost = IDN.toASCII(host.toLowerCase(Locale.ROOT));
            int port = uri.getPort();
            String path = uri.getRawPath() != null && !uri.getRawPath().isBlank() ? uri.getRawPath() : "/";
            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    normalizedHost,
                    port,
                    path,
                    uri.getRawQuery(),
                    null
            );
            return normalized.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isSafeUrl(String normalizedUrl) {
        if (normalizedUrl == null) {
            return false;
        }
        try {
            URI uri = URI.create(normalizedUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".local")) {
                return false;
            }
            InetAddress address = InetAddress.getByName(normalizedHost);
            return !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress()
                    && !address.isSiteLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractTitle(String body) {
        Matcher matcher = TITLE_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return normalizeHtmlText(matcher.group(1));
    }

    private String extractMeta(String body, String... names) {
        Set<String> accepted = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null) {
                accepted.add(name.toLowerCase(Locale.ROOT));
            }
        }
        Matcher matcher = META_PATTERN.matcher(body);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1).trim().toLowerCase(Locale.ROOT) : "";
            if (!accepted.contains(name)) {
                continue;
            }
            String value = normalizeHtmlText(matcher.group(2));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeHtmlText(String value) {
        String stripped = value != null ? TAG_PATTERN.matcher(value).replaceAll(" ") : null;
        if (stripped == null) {
            return null;
        }
        return trimToNull(stripped.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
