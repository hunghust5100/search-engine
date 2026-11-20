package com.matrix.search.search.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Single ranked search result item returned to Swagger/UI clients.
 */
@Data
@Builder
public class SearchHitDto {

    private String url;
    private String subdomain;
    private String title;
    private String description;
    private String snippet;
    private float score;
    private Instant crawledAt;

    public SearchHitDto() {
    }

    public SearchHitDto(
            String url,
            String subdomain,
            String title,
            String description,
            String snippet,
            float score,
            Instant crawledAt) {
        this.url = url;
        this.subdomain = subdomain;
        this.title = title;
        this.description = description;
        this.snippet = snippet;
        this.score = score;
        this.crawledAt = crawledAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public Instant getCrawledAt() {
        return crawledAt;
    }

    public void setCrawledAt(Instant crawledAt) {
        this.crawledAt = crawledAt;
    }
}
