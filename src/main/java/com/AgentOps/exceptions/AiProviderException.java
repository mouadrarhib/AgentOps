package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

public class AiProviderException extends ApiException {
    public AiProviderException(HttpStatus status, String message) { super(status, message); }
}
