package com.example.ecomerce.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final long jwtTtlSeconds;

    public AuthController(
        AuthenticationManager authenticationManager,
        JwtEncoder jwtEncoder,
        @Value("${app.security.jwt.ttl-seconds:3600}") long jwtTtlSeconds
    ) {
    this.authenticationManager = authenticationManager;
    this.jwtEncoder = jwtEncoder;
    this.jwtTtlSeconds = jwtTtlSeconds;
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        List<String> roles,
        List<String> permissions
    ) {
    }

    public record MeResponse(
        String username,
        List<String> roles,
        List<String> permissions
    ) {
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
    if (request == null
        || request.username() == null
        || request.password() == null
        || request.username().isBlank()
        || request.password().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
    }

    Authentication authentication;
    try {
        authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
    } catch (AuthenticationException ex) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    List<String> authorities = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("ecomerce")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(jwtTtlSeconds))
        .subject(authentication.getName())
        .claim("authorities", authorities)
        .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    MeResponse me = toMeResponse(authentication);
    return new LoginResponse(
        token,
        "Bearer",
        jwtTtlSeconds,
        me.username(),
        me.roles(),
        me.permissions()
    );
    }

    @GetMapping("/me")
    public MeResponse getCurrentUser(Authentication authentication) {
    return toMeResponse(authentication);
    }

    private static MeResponse toMeResponse(Authentication authentication) {
    List<String> authorities = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    List<String> roles = authorities.stream()
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> a.substring("ROLE_".length()))
        .collect(Collectors.toList());

    List<String> permissions = authorities.stream()
        .filter(a -> !a.startsWith("ROLE_"))
        .collect(Collectors.toList());

    return new MeResponse(authentication.getName(), roles, permissions);
    }
}
