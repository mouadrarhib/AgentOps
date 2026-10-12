package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource) {
        super(HttpStatus.NOT_FOUND, resource + " was not found.");
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(HttpStatus.NOT_FOUND, resource + " with ID " + id + " was not found.");
    }
}
