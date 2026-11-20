package com.matrix.search.document.repository;

import com.matrix.search.document.entity.WebDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for persisting and querying {@link WebDocument} entities in H2 DB.
 */
@Repository
public interface WebDocumentRepository extends JpaRepository<WebDocument, Long> {

    /**
     * Find document by canonical URL.
     */
    Optional<WebDocument> findByUrl(String url);

    /**
     * Check if a document with the given URL already exists.
     */
    boolean existsByUrl(String url);

    /**
     * Find document by content SHA-256 hash (used for content deduplication across URLs).
     */
    Optional<WebDocument> findFirstByContentHash(String contentHash);

    /**
     * Find unindexed documents ordered by ID ascending with limit/pageable.
     */
    List<WebDocument> findByIndexedFalseOrderByIdAsc(Pageable pageable);

    /**
     * Find all unindexed documents.
     */
    List<WebDocument> findByIndexedFalse();

    /**
     * Count unindexed documents.
     */
    long countByIndexedFalse();

    /**
     * Count indexed documents.
     */
    long countByIndexedTrue();

    /**
     * Mark a batch of documents as indexed with current timestamp.
     */
    @Modifying
    @Query("UPDATE WebDocument w SET w.indexed = true, w.updatedAt = :now WHERE w.id IN :ids")
    int markIndexedByIds(@Param("ids") List<Long> ids, @Param("now") Instant now);
}
