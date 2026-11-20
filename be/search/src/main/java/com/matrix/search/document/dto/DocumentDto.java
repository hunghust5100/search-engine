package com.matrix.search.document.dto;

import com.matrix.search.document.entity.WebDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {

    private Long id;
    private String url;
    private String subdomain;
    private String title;
    private String description;
    private String contentHash;
    private boolean indexed;
    private Instant crawledAt;
    private Instant updatedAt;
    private String cleanContentPreview;

    public static DocumentDto fromEntity(WebDocument entity) {
        if (entity == null) {
            return null;
        }
        String content = entity.getCleanContent();
        String preview = "";
        if (content != null) {
            preview = content.length() > 300 ? content.substring(0, 300) + "..." : content;
        }

        return DocumentDto.builder()
                .id(entity.getId())
                .url(entity.getUrl())
                .subdomain(entity.getSubdomain())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .contentHash(entity.getContentHash())
                .indexed(entity.isIndexed())
                .crawledAt(entity.getCrawledAt())
                .updatedAt(entity.getUpdatedAt())
                .cleanContentPreview(preview)
                .build();
    }
}
