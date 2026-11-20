package com.matrix.search.search.service;

import com.matrix.search.common.LuceneFields;
import com.matrix.search.search.dto.SearchHitDto;
import com.matrix.search.search.dto.SearchRequest;
import com.matrix.search.search.dto.SearchResponse;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apache Lucene {@link SearchService} implementation using {@link BM25Similarity},
 * multi-field query boosting, optional subdomain filtering, and HTML snippet highlighting.
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private static final String[] SEARCH_FIELDS = {
            LuceneFields.TITLE,
            LuceneFields.DESCRIPTION,
            LuceneFields.CONTENT
    };

    private static final Map<String, Float> FIELD_BOOSTS = Map.of(
            LuceneFields.TITLE, 3.0f,
            LuceneFields.DESCRIPTION, 1.5f,
            LuceneFields.CONTENT, 1.0f
    );

    private static final float BM25_K1 = 1.2f;
    private static final float BM25_B = 0.75f;

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIPPET_FRAGMENT_SIZE = 160;
    private static final int SNIPPET_MAX_FRAGMENTS = 2;
    private static final String SNIPPET_SEPARATOR = " ... ";
    private static final String HIGHLIGHT_PRE_TAG = "<mark>";
    private static final String HIGHLIGHT_POST_TAG = "</mark>";

    private final Directory directory;
    private final Analyzer vietnameseAnalyzer;

    public SearchServiceImpl(Directory directory, Analyzer vietnameseAnalyzer) {
        this.directory = directory;
        this.vietnameseAnalyzer = vietnameseAnalyzer;
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        long startNanos = System.nanoTime();

        if (request == null) {
            throw new IllegalArgumentException("Search request must not be null.");
        }

        String rawQuery = request.getQ() != null ? request.getQ().trim() : "";
        String normalizedSubdomain = normalizeSubdomain(request.getSubdomain());
        int page = validatePage(request.getPage());
        int size = validateSize(request.getSize());

        if (rawQuery.isEmpty()) {
            return emptyResponse(rawQuery, normalizedSubdomain, page, size, startNanos);
        }

        try {
            if (!DirectoryReader.indexExists(directory)) {
                return emptyResponse(rawQuery, normalizedSubdomain, page, size, startNanos);
            }

            Query textQuery = buildTextQuery(rawQuery);
            if (textQuery == null) {
                return emptyResponse(rawQuery, normalizedSubdomain, page, size, startNanos);
            }

            Query finalQuery = applySubdomainFilter(textQuery, normalizedSubdomain);

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(new BM25Similarity(BM25_K1, BM25_B));

                long offsetLong = (long) page * size;
                int topN = (int) Math.min(offsetLong + size, Integer.MAX_VALUE);

                TopDocs topDocs = searcher.search(finalQuery, Math.max(1, topN));
                long totalHits = topDocs.totalHits.value();
                ScoreDoc[] scoreDocs = topDocs.scoreDocs;

                List<SearchHitDto> hits = new ArrayList<>();
                if (offsetLong < scoreDocs.length) {
                    int start = (int) offsetLong;
                    int end = Math.min(start + size, scoreDocs.length);
                    StoredFields storedFields = searcher.storedFields();
                    Highlighter highlighter = createHighlighter(textQuery);

                    for (int i = start; i < end; i++) {
                        ScoreDoc scoreDoc = scoreDocs[i];
                        Document doc = storedFields.document(scoreDoc.doc);
                        hits.add(toSearchHitDto(doc, scoreDoc.score, highlighter));
                    }
                }

                SearchResponse response = new SearchResponse();
                response.setQuery(rawQuery);
                response.setSubdomain(normalizedSubdomain);
                response.setTotalHits(totalHits);
                response.setPage(page);
                response.setSize(size);
                response.setTookMs(elapsedMillis(startNanos));
                response.setHits(hits);
                return response;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to execute Lucene search query", e);
        }
    }

    private Query buildTextQuery(String rawQuery) {
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                SEARCH_FIELDS,
                vietnameseAnalyzer,
                FIELD_BOOSTS);
        try {
            return parser.parse(rawQuery);
        } catch (ParseException e) {
            String escaped = QueryParserBase.escape(rawQuery);
            if (escaped.isBlank()) {
                return null;
            }
            try {
                return parser.parse(escaped);
            } catch (ParseException ex) {
                log.debug("Query could not be parsed even after escaping: '{}'", rawQuery, ex);
                return null;
            }
        }
    }

    private Query applySubdomainFilter(Query textQuery, String subdomain) {
        if (subdomain == null) {
            return textQuery;
        }
        return new BooleanQuery.Builder()
                .add(textQuery, BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(LuceneFields.SUBDOMAIN, subdomain)), BooleanClause.Occur.FILTER)
                .build();
    }

    private Highlighter createHighlighter(Query textQuery) {
        QueryScorer scorer = new QueryScorer(textQuery);
        Highlighter highlighter = new Highlighter(
                new SimpleHTMLFormatter(HIGHLIGHT_PRE_TAG, HIGHLIGHT_POST_TAG),
                scorer);
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, SNIPPET_FRAGMENT_SIZE));
        return highlighter;
    }

    private SearchHitDto toSearchHitDto(Document doc, float score, Highlighter highlighter) throws IOException {
        String title = doc.get(LuceneFields.TITLE);
        String description = doc.get(LuceneFields.DESCRIPTION);
        String content = doc.get(LuceneFields.CONTENT);
        String snippet = buildSnippet(highlighter, content, description, title);

        return new SearchHitDto(
                doc.get(LuceneFields.URL),
                doc.get(LuceneFields.SUBDOMAIN),
                title,
                description,
                snippet,
                score,
                parseInstant(doc.get(LuceneFields.CRAWLED_AT)));
    }

    private String buildSnippet(Highlighter highlighter, String content, String description, String title)
            throws IOException {
        String highlightedContent = highlightField(highlighter, LuceneFields.CONTENT, content);
        if (highlightedContent != null && !highlightedContent.isBlank()) {
            return highlightedContent;
        }

        String highlightedDescription = highlightField(highlighter, LuceneFields.DESCRIPTION, description);
        if (highlightedDescription != null && !highlightedDescription.isBlank()) {
            return highlightedDescription;
        }

        String highlightedTitle = highlightField(highlighter, LuceneFields.TITLE, title);
        if (highlightedTitle != null && !highlightedTitle.isBlank()) {
            return highlightedTitle;
        }

        if (content != null && !content.isBlank()) {
            return truncatePlain(content, SNIPPET_FRAGMENT_SIZE * SNIPPET_MAX_FRAGMENTS);
        }
        if (description != null && !description.isBlank()) {
            return truncatePlain(description, SNIPPET_FRAGMENT_SIZE * SNIPPET_MAX_FRAGMENTS);
        }
        return title != null ? title : "";
    }

    private String highlightField(Highlighter highlighter, String fieldName, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return null;
        }
        try (TokenStream tokenStream = vietnameseAnalyzer.tokenStream(fieldName, text)) {
            return highlighter.getBestFragments(tokenStream, text, SNIPPET_MAX_FRAGMENTS, SNIPPET_SEPARATOR);
        } catch (InvalidTokenOffsetsException e) {
            log.debug("Invalid token offsets while highlighting field {}", fieldName, e);
            return null;
        }
    }

    private String truncatePlain(String text, int maxLength) {
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        int cut = normalized.lastIndexOf(' ', maxLength);
        if (cut < maxLength / 2) {
            cut = maxLength;
        }
        return normalized.substring(0, cut).stripTrailing() + "...";
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizeSubdomain(String subdomain) {
        if (subdomain == null) {
            return null;
        }
        String trimmed = subdomain.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or a positive integer.");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return size;
    }

    private SearchResponse emptyResponse(String query, String subdomain, int page, int size, long startNanos) {
        return new SearchResponse(
                query,
                subdomain,
                0L,
                page,
                size > 0 ? size : DEFAULT_PAGE_SIZE,
                elapsedMillis(startNanos),
                List.of());
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
