package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "Authentication is required or the account is no longer available.");
    }
}
