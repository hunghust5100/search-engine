package com.matrix.search.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated search API response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private String query;
    private String subdomain;
    private long totalHits;
    private int page;
    private int size;
    private long tookMs;

    @Builder.Default
    private List<SearchHitDto> hits = new ArrayList<>();
}
