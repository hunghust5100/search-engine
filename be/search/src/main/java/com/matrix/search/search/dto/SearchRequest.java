package com.matrix.search.search.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Query parameters for full-text search requests.
 */
@Data
@Builder
public class SearchRequest {

    private String q;
    private String subdomain;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    public SearchRequest() {
    }

    public SearchRequest(String q, String subdomain, int page, int size) {
        this.q = q;
        this.subdomain = subdomain;
        this.page = page;
        this.size = size;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
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
}
