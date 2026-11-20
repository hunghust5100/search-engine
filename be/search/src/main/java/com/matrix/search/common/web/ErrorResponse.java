package com.matrix.search.common.web;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Generic error payload returned by REST endpoints when a request cannot be fulfilled.
 */
@Data
@Builder
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private Instant timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String error, String message, Instant timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * Builds an error response from an HTTP status and a human-readable message.
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                message,
                Instant.now());
    }
}
