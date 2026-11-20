package com.matrix.search.document.cleaner;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Jsoup-based HTML content cleaner and metadata extractor.
 * Strips script, style, navigation, footer, and boilerplate tags to extract
 * pure text content suitable for indexing and searching.
 */
@Component
@Slf4j
public class HtmlCleaner {

    private static final int MAX_TITLE_LENGTH = 512;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final int MAX_PREVIEW_DESC_LENGTH = 250;

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Tags that typically do not contain meaningful searchable content.
     */
    private static final String UNWANTED_TAGS_SELECTOR =
            "script, style, noscript, iframe, svg, header, footer, nav, aside, form, " +
            "button, input, select, textarea, audio, video, canvas, " +
            "[role=navigation], [role=banner], [role=contentinfo]";

    /**
     * Clean raw HTML and extract title, description, and clean content.
     *
     * @param rawHtml the raw HTML markup
     * @param baseUri base URI for resolving relative links if needed
     * @return cleaned document data
     */
    public CleanedDocumentData clean(String rawHtml, String baseUri) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return CleanedDocumentData.builder()
                    .title("")
                    .description("")
                    .cleanContent("")
                    .build();
        }

        Document doc;
        try {
            doc = (baseUri != null && !baseUri.isBlank())
                    ? Jsoup.parse(rawHtml, baseUri)
                    : Jsoup.parse(rawHtml);
        } catch (Exception e) {
            log.warn("Failed to parse HTML, fallback to text strip: {}", e.getMessage());
            String fallbackText = normalizeWhitespace(Jsoup.clean(rawHtml, org.jsoup.safety.Safelist.none()));
            return CleanedDocumentData.builder()
                    .title("")
                    .description(truncate(fallbackText, MAX_PREVIEW_DESC_LENGTH))
                    .cleanContent(fallbackText)
                    .build();
        }

        // 1. Extract title before removing tags
        String title = extractTitle(doc);

        // 2. Extract description from meta tags before stripping
        String description = extractDescription(doc);

        // 3. Remove noise / non-content elements
        doc.select(UNWANTED_TAGS_SELECTOR).remove();

        // 4. Extract clean text from body
        Element body = doc.body() != null ? doc.body() : doc;
        String rawText = body.text();
        String cleanContent = normalizeWhitespace(rawText);

        // If description is empty, fallback to the beginning of cleanContent
        if (description.isBlank() && !cleanContent.isBlank()) {
            description = truncate(cleanContent, MAX_PREVIEW_DESC_LENGTH);
        }

        return CleanedDocumentData.builder()
                .title(truncate(title, MAX_TITLE_LENGTH))
                .description(truncate(description, MAX_DESCRIPTION_LENGTH))
                .cleanContent(cleanContent)
                .build();
    }

    private String extractTitle(Document doc) {
        // Priority 1: <title> tag
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            return normalizeWhitespace(title);
        }

        // Priority 2: OpenGraph or Twitter title
        Element ogTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]");
        if (ogTitle != null && ogTitle.hasAttr("content") && !ogTitle.attr("content").isBlank()) {
            return normalizeWhitespace(ogTitle.attr("content"));
        }

        // Priority 3: <h1> heading
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return normalizeWhitespace(h1.text());
        }

        return "";
    }

    private String extractDescription(Document doc) {
        // Priority 1: meta description
        Element metaDesc = doc.selectFirst("meta[name=description], meta[name=Description]");
        if (metaDesc != null && metaDesc.hasAttr("content") && !metaDesc.attr("content").isBlank()) {
            return normalizeWhitespace(metaDesc.attr("content"));
        }

        // Priority 2: OpenGraph description
        Element ogDesc = doc.selectFirst("meta[property=og:description], meta[name=twitter:description]");
        if (ogDesc != null && ogDesc.hasAttr("content") && !ogDesc.attr("content").isBlank()) {
            return normalizeWhitespace(ogDesc.attr("content"));
        }

        return "";
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength).trim();
    }
}
