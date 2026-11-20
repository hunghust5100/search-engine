package com.matrix.search.crawler.web;

import com.matrix.search.common.web.ErrorResponse;
import com.matrix.search.crawler.dto.CrawlStartRequest;
import com.matrix.search.crawler.dto.CrawlStatusResponse;
import com.matrix.search.crawler.service.CrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API to start, stop and monitor crawling of *.hust.edu.vn pages.
 */
@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
@Tag(name = "Crawler", description = "Starts, stops and monitors crawls over hust.edu.vn subdomains")
public class CrawlerController {

    private final CrawlerService crawlerService;

    @Operation(summary = "Start a new crawl run",
            description = "Starts an asynchronous multi-threaded crawl. All request body fields are optional "
                    + "and default to the configured crawler settings. "
                    + "Returns 409 when a crawl is already running.")
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) CrawlStartRequest request) {
        try {
            CrawlStartRequest effective = request != null ? request : CrawlStartRequest.builder().build();
            return ResponseEntity.accepted().body(crawlerService.start(effective));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(crawlerService.status());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }

    @Operation(summary = "Stop the running crawl",
            description = "Requests a graceful stop: workers finish their current fetches and the run ends in "
                    + "the STOPPED state. Returns 409 when no crawl is running.")
    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        try {
            return ResponseEntity.ok(crawlerService.stop());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage()));
        }
    }

    @Operation(summary = "Get crawl status",
            description = "Returns a live snapshot of the running crawl, or of the most recently finished run.")
    @GetMapping("/status")
    public CrawlStatusResponse status() {
        return crawlerService.status();
    }
}
