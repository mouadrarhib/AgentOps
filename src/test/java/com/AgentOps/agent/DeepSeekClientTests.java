package com.AgentOps.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekClientTests {

    @Test
    void stripsOnlyTrailingSlashesFromTheApiBase() {
        assertEquals("https://api.deepseek.com", DeepSeekClient.stripTrailingSlashes("https://api.deepseek.com///"));
        assertEquals("https://api.deepseek.com/v1", DeepSeekClient.stripTrailingSlashes("https://api.deepseek.com/v1/"));
        assertEquals("https://api.deepseek.com/v1", DeepSeekClient.stripTrailingSlashes("https://api.deepseek.com/v1"));
        assertEquals("", DeepSeekClient.stripTrailingSlashes("///"));
        assertEquals("", DeepSeekClient.stripTrailingSlashes(""));
    }
}
