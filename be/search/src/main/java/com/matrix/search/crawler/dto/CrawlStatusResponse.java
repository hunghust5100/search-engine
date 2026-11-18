package com.matrix.search.crawler.dto;

import com.matrix.search.crawler.model.CrawlState;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of the current or most recent crawl run, returned by every crawler endpoint.
 */
@Data
@Builder
public class CrawlStatusResponse {

    private CrawlState state;
    private Instant startedAt;
    private Instant finishedAt;

    private List<String> seedUrls;
    private int maxPages;
    private int maxDepth;
    private int threads;

    private long pagesCrawled;
    private long pagesFailed;
    private long urlsDiscovered;
    private long urlsQueued;
    private long urlsSkippedRobots;

    private String message;
}
