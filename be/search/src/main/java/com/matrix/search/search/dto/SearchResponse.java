package com.matrix.search.search.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated search API response payload.
 */
@Data
@Builder
public class SearchResponse {

    private String query;
    private String subdomain;
    private long totalHits;
    private int page;
    private int size;
    private long tookMs;

    @Builder.Default
    private List<SearchHitDto> hits = new ArrayList<>();

    public SearchResponse() {
    }

    public SearchResponse(
            String query,
            String subdomain,
            long totalHits,
            int page,
            int size,
            long tookMs,
            List<SearchHitDto> hits) {
        this.query = query;
        this.subdomain = subdomain;
        this.totalHits = totalHits;
        this.page = page;
        this.size = size;
        this.tookMs = tookMs;
        this.hits = hits != null ? hits : new ArrayList<>();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTookMs() {
        return tookMs;
    }

    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }

    public List<SearchHitDto> getHits() {
        return hits;
    }

    public void setHits(List<SearchHitDto> hits) {
        this.hits = hits;
    }
}
