package com.matrix.search.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatsResponse {

    private long totalDocuments;
    private long indexedDocuments;
    private long unindexedDocuments;
}
