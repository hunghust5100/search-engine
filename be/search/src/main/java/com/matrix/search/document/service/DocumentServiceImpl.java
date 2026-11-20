package com.matrix.search.document.service;

import com.matrix.search.crawler.dto.RawCrawledPage;
import com.matrix.search.document.cleaner.CleanedDocumentData;
import com.matrix.search.document.cleaner.HtmlCleaner;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.repository.WebDocumentRepository;
import com.matrix.search.document.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Implementation of {@link DocumentService}.
 * Cleans HTML using Jsoup, hashes clean content with SHA-256 for deduplication,
 * and manages persistence of {@link WebDocument} with H2 DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final WebDocumentRepository repository;
    private final HtmlCleaner htmlCleaner;

    @Override
    @Transactional
    public WebDocument saveOrUpdateFromCrawl(RawCrawledPage rawPage) {
        if (rawPage == null) {
            throw new IllegalArgumentException("RawCrawledPage must not be null");
        }
        String url = rawPage.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Page URL must not be blank");
        }

        // 1. Clean HTML and extract metadata
        CleanedDocumentData cleaned = htmlCleaner.clean(rawPage.getRawHtml(), url);

        // 2. Hash clean content using SHA-256 for duplicate detection
        String contentHash = HashUtils.sha256Hex(cleaned.cleanContent());

        // 3. Resolve subdomain
        String subdomain = resolveSubdomain(rawPage.getSubdomain(), url);

        Instant crawlTime = rawPage.getCrawledAt() != null ? rawPage.getCrawledAt() : Instant.now();
        Instant now = Instant.now();

        // 4. Check if document exists by canonical URL
        Optional<WebDocument> existingOpt = repository.findByUrl(url);

        WebDocument document;
        if (existingOpt.isPresent()) {
            document = existingOpt.get();
            boolean contentChanged = !contentHash.equalsIgnoreCase(document.getContentHash());

            document.setSubdomain(subdomain);
            document.setCrawledAt(crawlTime);
            document.setUpdatedAt(now);

            if (contentChanged) {
                log.info("Document content changed for URL: {} (oldHash={}, newHash={})",
                        url, document.getContentHash(), contentHash);
                document.setTitle(cleaned.title());
                document.setDescription(cleaned.description());
                document.setCleanContent(cleaned.cleanContent());
                document.setContentHash(contentHash);
                // Content changed: reset indexed to false so Indexer will re-index it
                document.setIndexed(false);
            } else {
                log.debug("Document content unchanged for URL: {} (hash={})", url, contentHash);
                // Content has not changed: keep previous indexed status
            }
        } else {
            log.info("Persisting new document for URL: {} (hash={})", url, contentHash);
            document = WebDocument.builder()
                    .url(url)
                    .subdomain(subdomain)
                    .title(cleaned.title())
                    .description(cleaned.description())
                    .cleanContent(cleaned.cleanContent())
                    .contentHash(contentHash)
                    .indexed(false)
                    .crawledAt(crawlTime)
                    .updatedAt(now)
                    .build();
        }

        return repository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebDocument> findUnindexedDocuments(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id"));
        return repository.findByIndexedFalseOrderByIdAsc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebDocument> findAllDocuments() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Override
    @Transactional
    public void markIndexed(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        int updated = repository.markIndexedByIds(documentIds, Instant.now());
        log.info("Marked {} documents as indexed in H2 DB", updated);
    }

    @Transactional(readOnly = true)
    public Optional<WebDocument> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<WebDocument> findByUrl(String url) {
        return repository.findByUrl(url);
    }

    @Transactional(readOnly = true)
    public long countTotalDocuments() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public long countIndexedDocuments() {
        return repository.countByIndexedTrue();
    }

    @Transactional(readOnly = true)
    public long countUnindexedDocuments() {
        return repository.countByIndexedFalse();
    }

    private String resolveSubdomain(String givenSubdomain, String url) {
        if (givenSubdomain != null && !givenSubdomain.isBlank()) {
            return givenSubdomain.trim().toLowerCase(Locale.ROOT);
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }
        return "hust.edu.vn";
    }
}
