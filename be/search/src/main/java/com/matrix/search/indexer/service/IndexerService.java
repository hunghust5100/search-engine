package com.matrix.search.indexer.service;

import com.matrix.search.document.entity.WebDocument;

import java.util.List;

/**
 * Contract for indexing normalized {@link WebDocument} records into Apache Lucene.
 */
public interface IndexerService {

    /**
     * Add or update a single document in the Lucene index.
     */
    void indexDocument(WebDocument document);

    /**
     * Add or update a batch of documents and commit changes to the Lucene index.
     */
    void indexBatch(List<WebDocument> documents);

    /**
     * Rebuild the entire Lucene index from all documents stored in the database.
     *
     * @return total number of indexed documents
     */
    int reindexAll();
}
