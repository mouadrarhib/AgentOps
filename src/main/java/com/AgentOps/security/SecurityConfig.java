package com.AgentOps.security;

import com.AgentOps.entities.enums.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class})
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be a Base64-encoded key.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties, SessionJwtValidator sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, value -> value != null),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, value -> {
                    try { return value != null && Long.parseLong(value) > 0; }
                    catch (NumberFormatException exception) { return false; }
                }),
                new JwtClaimValidator<String>("role", value -> {
                    try { return value != null && UserRole.valueOf(value) != null; }
                    catch (IllegalArgumentException exception) { return false; }
                }), sessions));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems, JwtDecoder decoder,
            @org.springframework.beans.factory.annotation.Value("${security.cookies.secure:false}") boolean secureCookies) throws Exception {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        CookieCsrfTokenRepository csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfCookies.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(secureCookies));
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        BearerTokenAuthenticationFilter tokens = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        tokens.setAuthenticationEntryPoint(problems);
        tokens.setBearerTokenResolver(request -> {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) return bearer.resolve(request);
            if (java.util.Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/csrf")
                    .contains(request.getRequestURI().substring(request.getContextPath().length()))) return null;
            return AuthCookies.read(request, AuthCookies.ACCESS);
        });
        return http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfCookies)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> {
                            boolean unsafe = !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
                            boolean cookieCredentials = AuthCookies.read(request, AuthCookies.ACCESS) != null
                                    || AuthCookies.read(request, AuthCookies.REFRESH) != null;
                            // A refresh without a body always uses the refresh cookie, even with a Bearer header.
                            boolean cookieRefresh = "/api/auth/refresh".equals(request.getRequestURI().substring(request.getContextPath().length()))
                                    && AuthCookies.read(request, AuthCookies.REFRESH) != null;
                            return unsafe && cookieCredentials
                                    && (request.getHeader(HttpHeaders.AUTHORIZATION) == null || cookieRefresh);
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                // Install the JWT filter directly: the resource-server DSL automatically exempts
                // every resolved token from CSRF, which would also exempt our JWT cookies.
                .addFilterAt(tokens, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
