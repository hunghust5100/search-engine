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

    /**
     * Builds an error response from an HTTP status and a human-readable message.
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase())
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
