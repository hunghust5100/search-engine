package com.matrix.search.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters for full-text search requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String q;
    private String subdomain;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;
}
