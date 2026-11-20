package com.matrix.search.search;

import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.service.DocumentService;
import com.matrix.search.indexer.service.IndexerService;
import com.matrix.search.indexer.service.IndexerServiceImpl;
import com.matrix.search.nlp.analyzer.VietnameseAnalyzer;
import com.matrix.search.search.controller.SearchController;
import com.matrix.search.search.dto.SearchHitDto;
import com.matrix.search.search.dto.SearchRequest;
import com.matrix.search.search.dto.SearchResponse;
import com.matrix.search.search.service.SearchService;
import com.matrix.search.search.service.SearchServiceImpl;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SearchServiceAndControllerTest {

    private static final Instant CRAWLED_AT = Instant.parse("2026-11-20T12:00:00Z");

    @TempDir
    Path tempIndexDir;

    @Autowired
    private VietnameseAnalyzer vietnameseAnalyzer;

    private Directory directory;
    private IndexerService indexerService;
    private SearchService searchService;
    private SearchController searchController;

    @BeforeEach
    void setUp() throws IOException {
        directory = FSDirectory.open(tempIndexDir);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        indexerService = new IndexerServiceImpl(
                directory,
                vietnameseAnalyzer,
                beanFactory.getBeanProvider(DocumentService.class));
        searchService = new SearchServiceImpl(directory, vietnameseAnalyzer);
        searchController = new SearchController(searchService);
    }

    @AfterEach
    void tearDown() throws IOException {
        directory.close();
    }

    @Test
    void returnsEmptyResponseWhenIndexDoesNotExistOrQueryIsBlank() {
        SearchResponse uninitialized = searchService.search(new SearchRequest("Bách khoa", null, 0, 10));
        assertEquals(0L, uninitialized.getTotalHits());
        assertTrue(uninitialized.getHits().isEmpty());

        indexSampleDocuments();

        SearchResponse blankQuery = searchService.search(new SearchRequest("   ", null, 0, 10));
        assertEquals(0L, blankQuery.getTotalHits());
        assertTrue(blankQuery.getHits().isEmpty());
    }

    @Test
    void ranksTitleBoostedDocumentHigherWithBM25AndHighlightsSnippet() {
        WebDocument contentOnlyMatch = createDoc(
                1L,
                "https://hust.edu.vn/tin-tuc/1",
                "hust.edu.vn",
                "Thông báo lịch học kỳ mới",
                "Kế hoạch giảng dạy chung",
                "Sinh viên trường Đại học Bách khoa Hà Nội xem thông tin tuyển sinh đại học chính quy năm 2026.");

        WebDocument titleMatch = createDoc(
                2L,
                "https://ts.hust.edu.vn/tuyen-sinh-2026",
                "ts.hust.edu.vn",
                "Tuyển sinh đại học chính quy năm 2026",
                "Đề án tuyển sinh Đại học Bách khoa Hà Nội",
                "Chi tiết chỉ tiêu và phương thức xét tuyển tài năng, điểm thi đánh giá tư duy.");

        indexerService.indexBatch(List.of(contentOnlyMatch, titleMatch));

        SearchResponse response = searchService.search(new SearchRequest("tuyển sinh đại học", null, 0, 10));

        assertEquals(2L, response.getTotalHits());
        assertEquals(2, response.getHits().size());

        SearchHitDto topHit = response.getHits().get(0);
        SearchHitDto secondHit = response.getHits().get(1);
        assertEquals("https://ts.hust.edu.vn/tuyen-sinh-2026", topHit.getUrl());
        assertTrue(topHit.getScore() > secondHit.getScore(),
                "Title-matched document should have a higher BM25 score than content-only match");
        assertNotNull(secondHit.getSnippet());
        assertTrue(secondHit.getSnippet().contains("<mark>"),
                "Expected highlighted <mark> tag in snippet: " + secondHit.getSnippet());
    }

    @Test
    void supportsUnaccentedVietnameseQueriesAndSubdomainFiltering() {
        indexSampleDocuments();

        SearchResponse unaccented = searchService.search(new SearchRequest("tri tue nhan tao", null, 0, 10));
        assertEquals(1L, unaccented.getTotalHits());
        assertEquals("https://soict.hust.edu.vn/ai-research", unaccented.getHits().get(0).getUrl());

        SearchResponse filteredHit = searchService.search(
                new SearchRequest("Bách khoa Hà Nội", "SOICT.HUST.EDU.VN", 0, 10));
        assertEquals(1L, filteredHit.getTotalHits());
        assertEquals("soict.hust.edu.vn", filteredHit.getHits().get(0).getSubdomain());

        SearchResponse filteredMiss = searchService.search(
                new SearchRequest("trí tuệ nhân tạo", "ts.hust.edu.vn", 0, 10));
        assertEquals(0L, filteredMiss.getTotalHits());
        assertTrue(filteredMiss.getHits().isEmpty());
    }

    @Test
    void paginatesResultsAndHandlesSpecialQueryCharactersGracefully() {
        indexSampleDocuments();

        SearchResponse page0 = searchService.search(new SearchRequest("Bách khoa", null, 0, 1));
        SearchResponse page1 = searchService.search(new SearchRequest("Bách khoa", null, 1, 1));

        assertEquals(2L, page0.getTotalHits());
        assertEquals(1, page0.getHits().size());
        assertEquals(1, page1.getHits().size());
        assertTrue(!page0.getHits().get(0).getUrl().equals(page1.getHits().get(0).getUrl()));

        SearchResponse escapedQuery = searchService.search(
                new SearchRequest("Bách khoa: [2026] (Hà Nội) + * ?", null, 0, 10));
        assertTrue(escapedQuery.getTotalHits() >= 1L);
    }

    @Test
    void validatesPaginationAndExposesRestEndpoints() {
        indexSampleDocuments();

        assertThrows(IllegalArgumentException.class, () ->
                searchService.search(new SearchRequest("Bách khoa", null, -1, 10)));
        assertThrows(IllegalArgumentException.class, () ->
                searchService.search(new SearchRequest("Bách khoa", null, 0, 0)));

        ResponseEntity<?> okResponse = searchController.search("tuyen sinh", null, 0, 10);
        assertEquals(HttpStatus.OK, okResponse.getStatusCode());
        SearchResponse body = (SearchResponse) okResponse.getBody();
        assertNotNull(body);
        assertEquals(1L, body.getTotalHits());

        ResponseEntity<?> badResponse = searchController.search("tuyen sinh", null, -1, 10);
        assertEquals(HttpStatus.BAD_REQUEST, badResponse.getStatusCode());
    }

    private void indexSampleDocuments() {
        WebDocument doc1 = createDoc(
                10L,
                "https://ts.hust.edu.vn/tuyen-sinh",
                "ts.hust.edu.vn",
                "Thông tin tuyển sinh Đại học Bách khoa Hà Nội",
                "Cổng thông tin tuyển sinh đại học chính quy",
                "Đại học Bách khoa Hà Nội công bố phương thức tuyển sinh đại học năm 2026.");

        WebDocument doc2 = createDoc(
                20L,
                "https://soict.hust.edu.vn/ai-research",
                "soict.hust.edu.vn",
                "Nghiên cứu Trí tuệ nhân tạo tại Trường CNTT&TT",
                "Trường Công nghệ Thông tin và Truyền thông - Đại học Bách khoa Hà Nội",
                "Các nhóm nghiên cứu về trí tuệ nhân tạo, học máy và khoa học dữ liệu tại Bách khoa Hà Nội.");

        indexerService.indexBatch(List.of(doc1, doc2));
    }

    private static WebDocument createDoc(
            Long id,
            String url,
            String subdomain,
            String title,
            String description,
            String cleanContent) {
        WebDocument doc = new WebDocument();
        doc.setId(id);
        doc.setUrl(url);
        doc.setSubdomain(subdomain);
        doc.setTitle(title);
        doc.setDescription(description);
        doc.setCleanContent(cleanContent);
        doc.setCrawledAt(CRAWLED_AT);
        doc.setUpdatedAt(CRAWLED_AT);
        return doc;
    }
}
