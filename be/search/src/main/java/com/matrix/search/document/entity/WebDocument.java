package com.matrix.search.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Normalized web document entity stored in H2 and indexed into Apache Lucene.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "web_documents",
        indexes = {
                @Index(name = "idx_web_documents_subdomain", columnList = "subdomain"),
                @Index(name = "idx_web_documents_indexed", columnList = "indexed")
        }
)
public class WebDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1024)
    private String url;

    @Column(nullable = false, length = 255)
    private String subdomain;

    @Column(length = 512)
    private String title;

    @Column(length = 1024)
    private String description;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String cleanContent;

    @Column(length = 64)
    private String contentHash;

    @Builder.Default
    @Column(nullable = false)
    private boolean indexed = false;

    @Column(nullable = false)
    private Instant crawledAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (crawledAt == null) {
            crawledAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
