package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

/** Base exception for intentional, client-visible API failures. Messages must be safe to expose. */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
