package com.matrix.search.document.service;

import com.matrix.search.crawler.dto.RawCrawledPage;
import com.matrix.search.document.entity.WebDocument;

import java.util.List;

/**
 * Contract for normalizing, persisting, and querying crawled web documents.
 */
public interface DocumentService {

    /**
     * Extract clean text from a crawled page and save or update the stored document.
     */
    WebDocument saveOrUpdateFromCrawl(RawCrawledPage rawPage);

    /**
     * Fetch documents that have not yet been indexed into Lucene.
     */
    List<WebDocument> findUnindexedDocuments(int limit);

    /**
     * Fetch all stored documents (used for full re-indexing).
     */
    List<WebDocument> findAllDocuments();

    /**
     * Mark documents as indexed after Lucene commit succeeds.
     */
    void markIndexed(List<Long> documentIds);
}
