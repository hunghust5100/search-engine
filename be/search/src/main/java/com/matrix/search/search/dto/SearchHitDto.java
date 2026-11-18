package com.matrix.search.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Single ranked search result item returned to Swagger/UI clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitDto {

    private String url;
    private String subdomain;
    private String title;
    private String description;
    private String snippet;
    private float score;
    private Instant crawledAt;
}
