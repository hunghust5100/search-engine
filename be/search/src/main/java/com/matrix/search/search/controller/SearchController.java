package com.matrix.search.search.controller;

import com.matrix.search.common.web.ErrorResponse;
import com.matrix.search.search.dto.SearchRequest;
import com.matrix.search.search.dto.SearchResponse;
import com.matrix.search.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for full-text BM25 search over indexed *.hust.edu.vn documents.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Full-text BM25 search with Vietnamese NLP and snippet highlighting")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(
            summary = "Search indexed documents (GET)",
            description = "Executes a BM25 full-text search across title, description, and content fields using "
                    + "the Vietnamese analyzer (supports both accented and unaccented Vietnamese queries).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated search results",
                            content = @Content(schema = @Schema(implementation = SearchResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @GetMapping
    public ResponseEntity<?> search(
            @Parameter(description = "Search query string (accented or unaccented Vietnamese)")
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @Parameter(description = "Optional subdomain filter (e.g. soict.hust.edu.vn or hust.edu.vn)")
            @RequestParam(name = "subdomain", required = false) String subdomain,
            @Parameter(description = "Zero-based page index")
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @Parameter(description = "Number of results per page (1..100)")
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        SearchRequest request = new SearchRequest(q, subdomain, page, size);
        return executeSearch(request);
    }

    @Operation(
            summary = "Search indexed documents (POST)",
            description = "Executes a BM25 full-text search from a JSON request payload.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated search results",
                            content = @Content(schema = @Schema(implementation = SearchResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping
    public ResponseEntity<?> searchPost(@RequestBody(required = false) SearchRequest request) {
        SearchRequest effective = request != null ? request : new SearchRequest();
        return executeSearch(effective);
    }

    private ResponseEntity<?> executeSearch(SearchRequest request) {
        try {
            return ResponseEntity.ok(searchService.search(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }
}
