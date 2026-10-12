package com.AgentOps.controllers;

import com.AgentOps.dto.request.*;
import com.AgentOps.dto.response.*;
import com.AgentOps.services.AuthService;
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

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) { return auth.currentUser(jwt.getSubject()); }
}
