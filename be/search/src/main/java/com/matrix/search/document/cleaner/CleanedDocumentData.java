package com.matrix.search.document.cleaner;

import lombok.Builder;

/**
 * Structured data extracted from cleaned HTML.
 */
@Builder
public record CleanedDocumentData(
        String title,
        String description,
        String cleanContent
) {
    public CleanedDocumentData {
        if (title == null) {
            title = "";
        }
        if (description == null) {
            description = "";
        }
        if (cleanContent == null) {
            cleanContent = "";
        }
    }
}
