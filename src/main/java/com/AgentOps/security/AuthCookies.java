package com.AgentOps.security;

import com.AgentOps.dto.response.AuthResponse;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    public static final String ACCESS = "AGENTOPS_ACCESS";
    public static final String REFRESH = "AGENTOPS_REFRESH";
    private final boolean secure;

    public AuthCookies(@Value("${security.cookies.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, AuthResponse auth) {
        set(response, ACCESS, auth.accessToken(), "/", auth.expiresIn());
        set(response, REFRESH, auth.refreshToken(), "/api/auth", auth.refreshExpiresIn());
    }

    public void clear(HttpServletResponse response) {
        set(response, ACCESS, "", "/", 0);
        set(response, REFRESH, "", "/api/auth", 0);
    }

    private void set(HttpServletResponse response, String name, String value, String path, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite("Strict").path(path).maxAge(seconds)
                .build().toString());
    }
}
