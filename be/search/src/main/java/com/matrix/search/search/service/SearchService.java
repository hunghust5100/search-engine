package com.matrix.search.search.service;

import com.matrix.search.search.dto.SearchRequest;
import com.matrix.search.search.dto.SearchResponse;

/**
 * Contract for querying and ranking documents from Apache Lucene.
 */
public interface SearchService {

    /**
     * Execute a full-text search query and return paginated ranked hits with snippets.
     */
    SearchResponse search(SearchRequest request);
}
