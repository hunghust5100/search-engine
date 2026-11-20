package com.matrix.search.crawler.model;

/**
 * Lifecycle states of a single crawl run.
 */
public enum CrawlState {

    /** No crawl has been started yet. */
    IDLE,

    /** A crawl run is currently being executed by its worker pool. */
    RUNNING,

    /** A stop was requested; workers are finishing their current fetches. */
    STOPPING,

    /** The crawl finished normally (frontier exhausted or page budget reached). */
    COMPLETED,

    /** The crawl was interrupted by an explicit stop request. */
    STOPPED,

    /** The crawl aborted due to an unexpected internal error. */
    FAILED
}
