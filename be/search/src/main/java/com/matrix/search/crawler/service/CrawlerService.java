package com.matrix.search.crawler.service;

import com.matrix.search.crawler.dto.CrawlStartRequest;
import com.matrix.search.crawler.dto.CrawlStatusResponse;

/**
 * Contract for running and monitoring crawl jobs over *.hust.edu.vn pages.
 */
public interface CrawlerService {

    /**
     * Starts a new crawl run asynchronously with the given overrides.
     *
     * @throws IllegalStateException  when a crawl is already running
     * @throws IllegalArgumentException when the request contains no usable seed URL
     *                                  or an invalid parameter value
     */
    CrawlStatusResponse start(CrawlStartRequest request);

    /**
     * Requests the running crawl to stop; workers finish their current fetches.
     *
     * @throws IllegalStateException when no crawl is running
     */
    CrawlStatusResponse stop();

    /**
     * Returns a snapshot of the running crawl, or of the most recently finished run.
     */
    CrawlStatusResponse status();
}
