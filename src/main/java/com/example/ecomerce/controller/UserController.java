package com.example.ecomerce.controller;

import com.example.ecomerce.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserController(AccountService accountService, PasswordEncoder passwordEncoder) {
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Map<String, Object>> getUsers() {
        return accountService.listUsers().stream()
            .map(user -> {
                var authorities = user.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .collect(Collectors.toList());

                var roles = authorities.stream()
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring("ROLE_".length()))
                    .collect(Collectors.toList());

                var permissions = authorities.stream()
                    .filter(a -> !a.startsWith("ROLE_"))
                    .collect(Collectors.toList());

                return Map.<String, Object>of(
                    "username", user.getUsername(),
                    "enabled", user.isEnabled(),
                    "roles", roles,
                    "permissions", permissions
                );
            })
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username is required"));
        }

        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "password is required"));
        }

        if (accountService.userExists(request.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "User already exists"));
        }

        if (request.roles() == null || request.roles().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "roles is required"));
        }

        try {
            accountService.createUser(request.username(), request.password(), request.roles(), passwordEncoder);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("created", request.username()));
    }

    @PutMapping("/{username}")
    public ResponseEntity<?> updateUser(@PathVariable String username, @RequestBody UpdateUserRequest request) {
        if (!accountService.userExists(username)) {
            return ResponseEntity.notFound().build();
        }

        if (request.roles() == null || request.roles().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "roles is required"));
        }

        try {
            accountService.updateUser(username, request.password(), request.roles(), passwordEncoder);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("updated", username));
    }

    @PatchMapping("/{username}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable String username, @RequestBody UserStatusRequest request) {
        if (!accountService.userExists(username)) {
            return ResponseEntity.notFound().build();
        }

        if (request == null || request.enabled() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled is required"));
        }

        try {
            accountService.setUserEnabled(username, request.enabled());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
            "username", username,
            "enabled", request.enabled()
        ));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<?> deleteUser(@PathVariable String username) {
        if (!accountService.userExists(username)) {
            return ResponseEntity.notFound().build();
        }
        accountService.deleteUser(username);
        return ResponseEntity.ok(Map.of("deleted", username));
    }

    public record CreateUserRequest(String username, String password, List<String> roles) {
    }

    public record UpdateUserRequest(String password, List<String> roles) {
    }

    public record UserStatusRequest(Boolean enabled) {
    }
}
