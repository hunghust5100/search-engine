package com.matrix.search.document.web;

import com.matrix.search.document.cleaner.CleanedDocumentData;
import com.matrix.search.document.cleaner.HtmlCleaner;
import com.matrix.search.document.dto.CleanPreviewRequest;
import com.matrix.search.document.dto.CleanPreviewResponse;
import com.matrix.search.document.dto.DocumentDto;
import com.matrix.search.document.dto.DocumentStatsResponse;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.repository.WebDocumentRepository;
import com.matrix.search.document.service.DocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentControllerTest {

    private DocumentServiceImpl documentService;
    private WebDocumentRepository repository;
    private HtmlCleaner htmlCleaner;
    private DocumentController controller;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentServiceImpl.class);
        repository = mock(WebDocumentRepository.class);
        htmlCleaner = mock(HtmlCleaner.class);
        controller = new DocumentController(documentService, repository, htmlCleaner);
    }

    @Test
    void getDocumentsReturnsPagedResults() {
        WebDocument doc = WebDocument.builder()
                .id(1L)
                .url("https://hust.edu.vn")
                .subdomain("hust.edu.vn")
                .title("Đại học Bách khoa")
                .cleanContent("Nội dung bài viết")
                .crawledAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(doc)));

        ResponseEntity<?> response = controller.getDocuments(0, 10);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getDocumentByIdReturns404WhenNotFound() {
        when(documentService.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getDocumentById(999L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getStatsReturnsCorrectCounts() {
        when(documentService.countTotalDocuments()).thenReturn(100L);
        when(documentService.countIndexedDocuments()).thenReturn(85L);
        when(documentService.countUnindexedDocuments()).thenReturn(15L);

        ResponseEntity<DocumentStatsResponse> response = controller.getStats();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getTotalDocuments());
        assertEquals(85L, response.getBody().getIndexedDocuments());
        assertEquals(15L, response.getBody().getUnindexedDocuments());
    }

    @Test
    void previewCleanExtractsAndHashesWithoutSaving() {
        CleanPreviewRequest req = new CleanPreviewRequest("https://hust.edu.vn", "<html><title>Test</title><body>Clean text</body></html>");
        when(htmlCleaner.clean("<html><title>Test</title><body>Clean text</body></html>", "https://hust.edu.vn"))
                .thenReturn(new CleanedDocumentData("Test", "Desc", "Clean text"));

        ResponseEntity<CleanPreviewResponse> response = controller.previewClean(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test", response.getBody().getTitle());
        assertEquals("Clean text", response.getBody().getCleanContent());
        assertEquals(64, response.getBody().getContentHash().length());
    }
}
