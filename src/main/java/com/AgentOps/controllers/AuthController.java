package com.AgentOps.controllers;

import com.AgentOps.dto.request.*;
import com.AgentOps.dto.response.*;
import com.AgentOps.services.AuthService;
import com.AgentOps.security.AuthCookies;
import com.AgentOps.exceptions.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResponse result = auth.register(request);
        cookies.write(response, result);
        return result;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse result = auth.login(request);
        cookies.write(response, result);
        return result;
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) { return auth.currentUser(jwt.getSubject()); }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                HttpServletRequest httpRequest, HttpServletResponse response) {
        if (request == null) {
            String token = AuthCookies.read(httpRequest, AuthCookies.REFRESH);
            if (token == null || token.isBlank() || token.length() > 128) throw new InvalidRefreshTokenException();
            request = new RefreshTokenRequest(token);
        }
        AuthResponse result = auth.refresh(request);
        cookies.write(response, result);
        return result;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void logout(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        auth.logout(jwt.getSubject(), jwt.getClaimAsString("sid"));
        cookies.clear(response);
    }

    @PatchMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request,
                                 HttpServletResponse response) {
        UserResponse result = auth.updateProfile(jwt.getSubject(), request);
        if (request.password() != null) cookies.clear(response);
        return result;
    }
}
