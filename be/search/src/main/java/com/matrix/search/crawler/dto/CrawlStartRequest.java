package com.matrix.search.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Optional parameters for starting a crawl run. All fields are optional;
 * omitted values fall back to {@code app.search.crawler} configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlStartRequest {

    /** Seed URLs to start crawling from. Must belong to the allowed domain suffix. */
    private List<String> seedUrls;

    /** Maximum number of pages to fetch in this run. */
    private Integer maxPages;

    /** Maximum link-follow depth (0 = seed pages only). */
    private Integer maxDepth;

    /** Number of parallel worker threads for this run. */
    private Integer threads;
}
