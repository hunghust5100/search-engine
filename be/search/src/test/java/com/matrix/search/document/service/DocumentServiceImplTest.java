package com.matrix.search.document.service;

import com.matrix.search.crawler.dto.RawCrawledPage;
import com.matrix.search.document.cleaner.CleanedDocumentData;
import com.matrix.search.document.cleaner.HtmlCleaner;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.repository.WebDocumentRepository;
import com.matrix.search.document.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceImplTest {

    private WebDocumentRepository repository;
    private HtmlCleaner htmlCleaner;
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        repository = mock(WebDocumentRepository.class);
        htmlCleaner = mock(HtmlCleaner.class);
        documentService = new DocumentServiceImpl(repository, htmlCleaner);
    }

    @Test
    void saveOrUpdateRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> documentService.saveOrUpdateFromCrawl(null));

        RawCrawledPage emptyUrl = RawCrawledPage.builder().url("  ").rawHtml("<html></html>").build();
        assertThrows(IllegalArgumentException.class, () -> documentService.saveOrUpdateFromCrawl(emptyUrl));
    }

    @Test
    void saveOrUpdateCreatesNewDocumentWithIndexedFalse() {
        String url = "https://hust.edu.vn/tin-tuc";
        String html = "<html><head><title>Tin tức</title></head><body>Nội dung tin tức mới</body></html>";
        Instant crawlTime = Instant.parse("2026-11-20T10:00:00Z");

        RawCrawledPage rawPage = RawCrawledPage.builder()
                .url(url)
                .subdomain("hust.edu.vn")
                .rawHtml(html)
                .httpStatus(200)
                .crawledAt(crawlTime)
                .build();

        CleanedDocumentData cleaned = CleanedDocumentData.builder()
                .title("Tin tức HUST")
                .description("Mô tả tin tức")
                .cleanContent("Nội dung tin tức mới")
                .build();

        when(htmlCleaner.clean(html, url)).thenReturn(cleaned);
        when(repository.findByUrl(url)).thenReturn(Optional.empty());
        when(repository.save(any(WebDocument.class))).thenAnswer(invocation -> {
            WebDocument doc = invocation.getArgument(0);
            doc.setId(100L);
            return doc;
        });

        WebDocument saved = documentService.saveOrUpdateFromCrawl(rawPage);

        assertNotNull(saved);
        assertEquals(100L, saved.getId());
        assertEquals(url, saved.getUrl());
        assertEquals("hust.edu.vn", saved.getSubdomain());
        assertEquals("Tin tức HUST", saved.getTitle());
        assertEquals("Mô tả tin tức", saved.getDescription());
        assertEquals("Nội dung tin tức mới", saved.getCleanContent());
        assertEquals(HashUtils.sha256Hex("Nội dung tin tức mới"), saved.getContentHash());
        assertFalse(saved.isIndexed());
        assertEquals(crawlTime, saved.getCrawledAt());

        verify(repository).save(any(WebDocument.class));
    }

    @Test
    void saveOrUpdateDetectsContentChangeAndResetsIndexedFalse() {
        String url = "https://hust.edu.vn/tuyen-sinh";
        String oldContent = "Nội dung cũ";
        String oldHash = HashUtils.sha256Hex(oldContent);

        WebDocument existing = WebDocument.builder()
                .id(50L)
                .url(url)
                .subdomain("hust.edu.vn")
                .title("Tuyển sinh cũ")
                .description("Mô tả cũ")
                .cleanContent(oldContent)
                .contentHash(oldHash)
                .indexed(true) // Was already indexed
                .crawledAt(Instant.parse("2026-11-15T00:00:00Z"))
                .updatedAt(Instant.parse("2026-11-15T00:00:00Z"))
                .build();

        String newHtml = "<html><body>Nội dung cập nhật mới hoàn toàn</body></html>";
        String newCleanText = "Nội dung cập nhật mới hoàn toàn";
        CleanedDocumentData cleaned = CleanedDocumentData.builder()
                .title("Tuyển sinh mới")
                .description("Mô tả mới")
                .cleanContent(newCleanText)
                .build();

        RawCrawledPage rawPage = RawCrawledPage.builder()
                .url(url)
                .rawHtml(newHtml)
                .httpStatus(200)
                .crawledAt(Instant.now())
                .build();

        when(htmlCleaner.clean(newHtml, url)).thenReturn(cleaned);
        when(repository.findByUrl(url)).thenReturn(Optional.of(existing));
        when(repository.save(any(WebDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebDocument updated = documentService.saveOrUpdateFromCrawl(rawPage);

        assertEquals("Tuyển sinh mới", updated.getTitle());
        assertEquals(newCleanText, updated.getCleanContent());
        assertEquals(HashUtils.sha256Hex(newCleanText), updated.getContentHash());
        // Since content changed, indexed must be reset to false so Lucene will re-index it!
        assertFalse(updated.isIndexed());

        verify(repository).save(existing);
    }

    @Test
    void saveOrUpdateRetainsIndexedStatusWhenContentHashIsUnchanged() {
        String url = "https://hust.edu.vn/lien-he";
        String content = "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội";
        String hash = HashUtils.sha256Hex(content);

        WebDocument existing = WebDocument.builder()
                .id(60L)
                .url(url)
                .subdomain("hust.edu.vn")
                .title("Liên hệ")
                .description("Thông tin liên hệ")
                .cleanContent(content)
                .contentHash(hash)
                .indexed(true) // Was already indexed
                .crawledAt(Instant.parse("2026-11-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-11-10T00:00:00Z"))
                .build();

        String rawHtml = "<html><body>Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội</body></html>";
        CleanedDocumentData cleaned = CleanedDocumentData.builder()
                .title("Liên hệ")
                .description("Thông tin liên hệ")
                .cleanContent(content)
                .build();

        RawCrawledPage rawPage = RawCrawledPage.builder()
                .url(url)
                .rawHtml(rawHtml)
                .httpStatus(200)
                .crawledAt(Instant.now())
                .build();

        when(htmlCleaner.clean(rawHtml, url)).thenReturn(cleaned);
        when(repository.findByUrl(url)).thenReturn(Optional.of(existing));
        when(repository.save(any(WebDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebDocument saved = documentService.saveOrUpdateFromCrawl(rawPage);

        // Content hash matched -> do NOT reset indexed to false!
        assertTrue(saved.isIndexed());
        assertEquals(hash, saved.getContentHash());

        verify(repository).save(existing);
    }

    @Test
    void findUnindexedDocumentsCallsRepositoryWithPagination() {
        WebDocument doc1 = WebDocument.builder().id(1L).indexed(false).build();
        WebDocument doc2 = WebDocument.builder().id(2L).indexed(false).build();

        when(repository.findByIndexedFalseOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(doc1, doc2));

        List<WebDocument> results = documentService.findUnindexedDocuments(10);
        assertEquals(2, results.size());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByIndexedFalseOrderByIdAsc(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());

        // limit <= 0 returns empty list immediately
        assertTrue(documentService.findUnindexedDocuments(0).isEmpty());
    }

    @Test
    void markIndexedUpdatesFlagUsingRepository() {
        documentService.markIndexed(List.of(1L, 2L, 3L));
        verify(repository).markIndexedByIds(eq(List.of(1L, 2L, 3L)), any(Instant.class));

        // null or empty list does nothing
        documentService.markIndexed(null);
        documentService.markIndexed(List.of());
    }
}
