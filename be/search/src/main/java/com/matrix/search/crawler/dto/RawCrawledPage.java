package com.matrix.search.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Raw crawled web page output produced by the crawler module and consumed by the document module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawCrawledPage {

    private String url;
    private String subdomain;
    private String rawHtml;
    private int httpStatus;
    private Instant crawledAt;
}
