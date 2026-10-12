package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "The refresh token is invalid, expired, or revoked.");
    }
}
