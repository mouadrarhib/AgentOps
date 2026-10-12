package com.AgentOps.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
