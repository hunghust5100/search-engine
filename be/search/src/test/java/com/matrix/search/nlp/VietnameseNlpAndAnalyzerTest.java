package com.matrix.search.nlp;

import com.matrix.search.common.LuceneFields;
import com.matrix.search.nlp.analyzer.VietnameseAnalyzer;
import com.matrix.search.nlp.service.VietnameseNlpService;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VietnameseNlpAndAnalyzerTest {

    @Autowired
    private VietnameseNlpService nlpService;

    @Autowired
    private VietnameseAnalyzer vietnameseAnalyzer;

    @Test
    void segmentShouldJoinVietnameseCompoundWords() {
        String input = "Trường Đại học Bách khoa Hà Nội tuyển sinh công nghệ thông tin năm 2026.";
        String segmented = nlpService.segment(input);

        assertTrue(segmented.contains("Đại_học"), "Expected 'Đại_học' in: " + segmented);
        assertTrue(segmented.contains("Bách_khoa"), "Expected 'Bách_khoa' in: " + segmented);
        assertTrue(segmented.contains("Hà_Nội"), "Expected 'Hà_Nội' in: " + segmented);
        assertTrue(segmented.contains("tuyển_sinh"), "Expected 'tuyển_sinh' in: " + segmented);
    }

    @Test
    void removeAccentsShouldStripDiacriticsAndConvertD() {
        String input = "Trường Đại học Bách khoa Hà Nội - ĐHBKHN";
        String unaccented = nlpService.removeAccents(input);

        assertEquals("Truong Dai hoc Bach khoa Ha Noi - DHBKHN", unaccented);
    }

    @Test
    void tokenizeShouldFilterStopwordsAndPunctuation() {
        String input = "Đây là những thông báo của Trường Đại học Bách khoa Hà Nội, và các viện đào tạo!";
        List<String> tokens = nlpService.tokenize(input);

        assertFalse(tokens.contains("đây"));
        assertFalse(tokens.contains("là"));
        assertFalse(tokens.contains("những"));
        assertFalse(tokens.contains("của"));
        assertFalse(tokens.contains("và"));
        assertFalse(tokens.contains("các"));
        assertFalse(tokens.contains(","));
        assertFalse(tokens.contains("!"));
        assertTrue(tokens.contains("đại_học"));
        assertTrue(tokens.contains("bách_khoa"));
    }

    @Test
    void analyzerShouldEmitAccentedUnaccentedAndSyllableTokensWithValidOffsets() throws IOException {
        String text = "Đại học Bách khoa Hà Nội";
        List<String> terms = new ArrayList<>();

        try (TokenStream ts = vietnameseAnalyzer.tokenStream(LuceneFields.CONTENT, text)) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAttr = ts.addAttribute(OffsetAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                terms.add(termAttr.toString());
                assertTrue(offsetAttr.startOffset() >= 0);
                assertTrue(offsetAttr.endOffset() >= offsetAttr.startOffset());
                assertTrue(offsetAttr.endOffset() <= text.length());
            }
            ts.end();
        }

        assertTrue(terms.contains("đại_học"));
        assertTrue(terms.contains("dai_hoc"));
        assertTrue(terms.contains("đại"));
        assertTrue(terms.contains("dai"));
        assertTrue(terms.contains("học"));
        assertTrue(terms.contains("hoc"));
        assertTrue(terms.contains("bách_khoa"));
        assertTrue(terms.contains("bach_khoa"));
    }

    @Test
    void analyzerShouldBeReusableAcrossMultipleInputs() throws IOException {
        List<String> first = analyzeTerms("Tuyển sinh đại học");
        List<String> second = analyzeTerms("Học bổng sinh viên");

        assertTrue(first.contains("tuyển_sinh"));
        assertFalse(first.contains("học_bổng"));
        assertTrue(second.contains("học_bổng"));
        assertTrue(second.contains("sinh_viên"));
        assertFalse(second.contains("tuyển_sinh"));
    }

    @Test
    void luceneIndexAndSearchShouldSupportBothAccentedAndUnaccentedQueriesWithHighlighting() throws Exception {
        String content = "Thông báo tuyển sinh Đại học Bách khoa Hà Nội ngành Khoa học máy tính năm 2026.";

        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig(vietnameseAnalyzer);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                Document doc = new Document();
                doc.add(new TextField(LuceneFields.CONTENT, content, Field.Store.YES));
                writer.addDocument(doc);
                writer.commit();
            }

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                QueryParser parser = new QueryParser(LuceneFields.CONTENT, vietnameseAnalyzer);

                // 1. Accented query + Highlighter check
                Query accentedQuery = parser.parse("tuyển sinh Đại học Bách khoa");
                TopDocs accentedHits = searcher.search(accentedQuery, 10);
                assertEquals(1, accentedHits.totalHits.value());

                Highlighter highlighter = new Highlighter(
                        new SimpleHTMLFormatter("<mark>", "</mark>"),
                        new QueryScorer(accentedQuery));
                String snippet = highlighter.getBestFragment(vietnameseAnalyzer, LuceneFields.CONTENT, content);
                assertNotNull(snippet);
                assertTrue(snippet.contains("<mark>"), "Expected highlight markup in snippet: " + snippet);

                // 2. Unaccented query
                Query unaccentedQuery = parser.parse("tuyen sinh dai hoc bach khoa");
                TopDocs unaccentedHits = searcher.search(unaccentedQuery, 10);
                assertEquals(1, unaccentedHits.totalHits.value());

                // 3. Unaccented partial phrase query
                Query csQuery = parser.parse("khoa hoc may tinh");
                TopDocs csHits = searcher.search(csQuery, 10);
                assertEquals(1, csHits.totalHits.value());
            }
        }
    }

    @Test
    void segmentShouldNotMergeAcrossNewlinesAndShouldHandleToneVariants() {
        String multiline = "Thông báo tuyển sinh\nĐại học Thủy lợi và Đại học Bách khoa";
        String segmented = nlpService.segment(multiline);

        assertFalse(segmented.contains("tuyển_sinh_Đại_học"));
        assertTrue(segmented.contains("Đại_học"));
        assertTrue(segmented.contains("Bách_khoa"));

        List<String> terms;
        try {
            terms = analyzeTerms(multiline);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertTrue(terms.contains("thuy"));
        assertTrue(terms.contains("loi"));
    }

    private List<String> analyzeTerms(String text) throws IOException {
        List<String> terms = new ArrayList<>();
        try (TokenStream ts = vietnameseAnalyzer.tokenStream(LuceneFields.CONTENT, text)) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                terms.add(termAttr.toString());
            }
            ts.end();
        }
        return terms;
    }
}
