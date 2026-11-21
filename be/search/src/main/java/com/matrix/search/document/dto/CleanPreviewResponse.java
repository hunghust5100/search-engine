package com.matrix.search.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanPreviewResponse {

    private String url;
    private String title;
    private String description;
    private String cleanContent;
    private String contentHash;
    private int contentLength;
}
