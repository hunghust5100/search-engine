package com.matrix.search.common;

/**
 * Shared Apache Lucene document field names used by both indexer and search modules.
 */
public final class LuceneFields {

    public static final String ID = "id";
    public static final String URL = "url";
    public static final String SUBDOMAIN = "subdomain";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String CONTENT = "content";
    public static final String CRAWLED_AT = "crawledAt";

    private LuceneFields() {
    }
}
