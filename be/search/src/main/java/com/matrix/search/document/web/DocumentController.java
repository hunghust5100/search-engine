package com.matrix.search.document.web;

import com.matrix.search.common.web.ErrorResponse;
import com.matrix.search.document.cleaner.CleanedDocumentData;
import com.matrix.search.document.cleaner.HtmlCleaner;
import com.matrix.search.document.dto.CleanPreviewRequest;
import com.matrix.search.document.dto.CleanPreviewResponse;
import com.matrix.search.document.dto.DocumentDto;
import com.matrix.search.document.dto.DocumentStatsResponse;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.repository.WebDocumentRepository;
import com.matrix.search.document.service.DocumentServiceImpl;
import com.matrix.search.document.util.HashUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST API for viewing stored WebDocuments in H2, stats, and testing HTML cleaning.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document", description = "Quản lý WebDocument, lọc HTML và băm SHA-256 chống trùng")
public class DocumentController {

    private final DocumentServiceImpl documentService;
    private final WebDocumentRepository repository;
    private final HtmlCleaner htmlCleaner;

    @Operation(summary = "Lấy danh sách WebDocument (phân trang)")
    @GetMapping
    public ResponseEntity<Page<DocumentDto>> getDocuments(
            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang (mặc định 20)")
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<WebDocument> result = repository.findAll(pageable);
        return ResponseEntity.ok(result.map(DocumentDto::fromEntity));
    }

    @Operation(summary = "Xem chi tiết WebDocument theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable("id") Long id) {
        Optional<WebDocument> docOpt = documentService.findById(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu với ID: " + id));
        }
        return ResponseEntity.ok(docOpt.get());
    }

    @Operation(summary = "Thống kê số lượng WebDocument trong CSDL H2")
    @GetMapping("/stats")
    public ResponseEntity<DocumentStatsResponse> getStats() {
        long total = documentService.countTotalDocuments();
        long indexed = documentService.countIndexedDocuments();
        long unindexed = documentService.countUnindexedDocuments();

        return ResponseEntity.ok(DocumentStatsResponse.builder()
                .totalDocuments(total)
                .indexedDocuments(indexed)
                .unindexedDocuments(unindexed)
                .build());
    }

    @Operation(summary = "Thử nghiệm làm sạch HTML và tính mã băm SHA-256 (không lưu vào DB)")
    @PostMapping("/clean")
    public ResponseEntity<CleanPreviewResponse> previewClean(
            @RequestBody CleanPreviewRequest request
    ) {
        String rawHtml = request != null ? request.getRawHtml() : "";
        String url = request != null ? request.getUrl() : "";

        CleanedDocumentData cleaned = htmlCleaner.clean(rawHtml, url);
        String hash = HashUtils.sha256Hex(cleaned.cleanContent());

        return ResponseEntity.ok(CleanPreviewResponse.builder()
                .url(url)
                .title(cleaned.title())
                .description(cleaned.description())
                .cleanContent(cleaned.cleanContent())
                .contentHash(hash)
                .contentLength(cleaned.cleanContent().length())
                .build());
    }
}
