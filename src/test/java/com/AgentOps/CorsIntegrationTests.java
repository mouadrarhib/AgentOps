package com.AgentOps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTests {
    private static final String FRONTEND = "http://localhost:5173";

    @Autowired MockMvc mvc;

    @Test
    void allowedFrontendPreflightPassesSecurityForAgentAndCsrfHeaders() throws Exception {
        mvc.perform(options("/agent/chat")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }

    @Test
    void preflightSupportsTicketUpdates() throws Exception {
        mvc.perform(options("/tickets/1")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void corsHeadersArePresentOnActualResponsesIncludingAuthErrors() throws Exception {
        mvc.perform(get("/health").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
        mvc.perform(get("/agent/conversations").header("Origin", FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(options("/agent/chat")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
