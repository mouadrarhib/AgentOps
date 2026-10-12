package com.AgentOps.exceptions;

public record ValidationError(String field, String message) {
}
