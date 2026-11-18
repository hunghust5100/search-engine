package com.matrix.search.nlp.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.CharArraySet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import vn.pipeline.Annotation;
import vn.pipeline.VnCoreNLP;
import vn.pipeline.Word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VnCoreNLP-backed implementation of {@link VietnameseNlpService}.
 */
@Service
@RequiredArgsConstructor
public class VietnameseNlpServiceImpl implements VietnameseNlpService {

    private static final String STOPWORDS_RESOURCE = "nlp/vietnamese-stopwords.txt";
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern LINE_PATTERN = Pattern.compile("[^\\r\\n]+");

    private final VnCoreNLP vnCoreNLP;

    private Set<String> stopwords = Collections.emptySet();
    private CharArraySet luceneStopwords = CharArraySet.EMPTY_SET;

    /**
     * Load Vietnamese stopwords from classpath after bean construction.
     */
    @PostConstruct
    public void init() throws IOException {
        Set<String> loaded = new LinkedHashSet<>();
        ClassPathResource resource = new ClassPathResource(STOPWORDS_RESOURCE);
        if (resource.exists()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = Normalizer.normalize(line.trim(), Normalizer.Form.NFC)
                            .toLowerCase(Locale.ROOT);
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        loaded.add(trimmed);
                    }
                }
            }
        }
        this.stopwords = Collections.unmodifiableSet(loaded);
        this.luceneStopwords = new CharArraySet(loaded, true);
    }

    @Override
    public String segment(String text) {
        List<SegmentedToken> tokens = segmentWithOffsets(text);
        if (tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i).term());
        }
        return sb.toString();
    }

    @Override
    public List<String> tokenize(String text) {
        List<SegmentedToken> segmented = segmentWithOffsets(text);
        if (segmented.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(segmented.size());
        for (SegmentedToken token : segmented) {
            String lower = token.term().toLowerCase(Locale.ROOT);
            if (!isMeaningfulToken(lower) || stopwords.contains(lower)) {
                continue;
            }
            result.add(lower);
        }
        return result;
    }

    @Override
    public String removeAccents(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }

    @Override
    public Set<String> getStopwords() {
        return stopwords;
    }

    /**
     * Return Lucene-compatible stopword set for {@link org.apache.lucene.analysis.StopFilter}.
     */
    public CharArraySet getLuceneStopwords() {
        return luceneStopwords;
    }

    /**
     * Segment text line-by-line with VnCoreNLP and compute character offsets relative to {@code rawText}.
     */
    public List<SegmentedToken> segmentWithOffsets(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<SegmentedToken> tokens = new ArrayList<>();
        Matcher lineMatcher = LINE_PATTERN.matcher(rawText);

        while (lineMatcher.find()) {
            int lineStart = lineMatcher.start();
            int lineEnd = lineMatcher.end();
            String rawLine = lineMatcher.group();
            String cleanedLine = cleanForNlp(rawLine);
            if (cleanedLine.isBlank()) {
                continue;
            }

            List<Word> words = runVnCoreNlp(cleanedLine);
            int localCursor = 0;
            int lineLen = rawLine.length();

            for (Word word : words) {
                String form = word.getForm();
                if (form == null || form.isBlank() || !isMeaningfulToken(form)) {
                    continue;
                }
                OffsetRange localRange = locateTokenOffsets(rawLine, form, localCursor, lineLen);
                localCursor = localRange.end();
                tokens.add(new SegmentedToken(
                        form,
                        lineStart + localRange.start(),
                        lineStart + localRange.end()));
            }
        }
        return tokens;
    }

    private List<Word> runVnCoreNlp(String text) {
        try {
            Annotation annotation = new Annotation(text);
            synchronized (vnCoreNLP) {
                vnCoreNLP.annotate(annotation);
            }
            return annotation.getWords() != null ? annotation.getWords() : List.of();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to annotate Vietnamese text with VnCoreNLP", e);
        }
    }

    private String cleanForNlp(String rawText) {
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFC);
        return CONTROL_CHARS.matcher(normalized).replaceAll(" ");
    }

    private boolean isMeaningfulToken(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isLetterOrDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private OffsetRange locateTokenOffsets(String rawText, String form, int cursor, int maxLen) {
        String[] parts = form.split("_");
        int start = -1;
        int end = cursor;
        int searchFrom = Math.min(cursor, maxLen);

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int found = findPartMatch(rawText, part, searchFrom);
            if (found >= 0) {
                if (start < 0) {
                    start = found;
                }
                end = found + part.length();
                searchFrom = end;
            }
        }

        if (start < 0 || end <= start || end > maxLen) {
            while (cursor < maxLen && Character.isWhitespace(rawText.charAt(cursor))) {
                cursor++;
            }
            int fallbackStart = Math.min(cursor, maxLen);
            int fallbackEnd = Math.min(fallbackStart + form.length(), maxLen);
            return new OffsetRange(fallbackStart, fallbackEnd);
        }
        return new OffsetRange(start, end);
    }

    private int findPartMatch(String haystack, String needle, int fromIndex) {
        int exact = indexOfIgnoreCase(haystack, needle, fromIndex);
        if (exact >= 0) {
            return exact;
        }
        // Fallback when VnCoreNLP changes tone placement (e.g. Thủy -> Thuỷ, Hòa -> Hoà)
        String unaccentedNeedle = removeAccents(needle).toLowerCase(Locale.ROOT);
        int needleLen = needle.length();
        int limit = haystack.length() - needleLen;
        for (int i = Math.max(0, fromIndex); i <= limit; i++) {
            String candidate = haystack.substring(i, i + needleLen);
            if (removeAccents(candidate).toLowerCase(Locale.ROOT).equals(unaccentedNeedle)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        int needleLen = needle.length();
        int limit = haystack.length() - needleLen;
        for (int i = Math.max(0, fromIndex); i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needleLen)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Segmented token paired with its character offsets in the source text.
     */
    public record SegmentedToken(String term, int startOffset, int endOffset) {
    }

    private record OffsetRange(int start, int end) {
    }
}
