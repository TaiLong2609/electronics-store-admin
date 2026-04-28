package com.example.ecomerce.service;

import com.example.ecomerce.security.Permission;
import com.example.ecomerce.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final InMemoryUserDetailsManager userDetailsManager;
    private final Map<String, UserDetails> users = new ConcurrentHashMap<>();

    public AccountService(PasswordEncoder passwordEncoder) {
        var superAdmin = buildUser("superadmin", "super123", Role.SUPER_ADMIN, passwordEncoder);
        var sales = buildUser("sales", "sales123", Role.SALES, passwordEncoder);
        var warehouse = buildUser("warehouse", "warehouse123", Role.WAREHOUSE, passwordEncoder);
        var customer = buildUser("customer", "customer123", Role.CUSTOMER, passwordEncoder);
        var content = buildUser("content", "content123", Role.CONTENT, passwordEncoder);

        this.userDetailsManager = new InMemoryUserDetailsManager(superAdmin, sales, warehouse, customer, content);
        users.put(superAdmin.getUsername(), superAdmin);
        users.put(sales.getUsername(), sales);
        users.put(warehouse.getUsername(), warehouse);
        users.put(customer.getUsername(), customer);
        users.put(content.getUsername(), content);
    }

    private UserDetails buildUser(String username, String password, Role role, PasswordEncoder passwordEncoder) {
        return User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .authorities(createAuthorities(role))
                .build();
    }

    private UserDetails buildUser(String username, String password, List<String> roles, PasswordEncoder passwordEncoder) {
        var parsedRoles = roles.stream()
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .toList();

        return User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .authorities(createAuthorities(parsedRoles))
                .build();
    }

        private UserDetails rebuildUser(UserDetails existing, String rawPassword, List<String> roles, PasswordEncoder passwordEncoder) {
        var parsedRoles = roles.stream()
            .map(String::toUpperCase)
            .map(Role::valueOf)
            .toList();

        String encodedPassword = existing.getPassword();
        if (rawPassword != null && !rawPassword.isBlank()) {
            encodedPassword = passwordEncoder.encode(rawPassword);
        }

        return User.withUserDetails(existing)
            .password(encodedPassword)
            .authorities(createAuthorities(parsedRoles))
            .build();
        }

    private Collection<GrantedAuthority> createAuthorities(Role role) {
        return createAuthorities(List.of(role));
    }

    private Collection<GrantedAuthority> createAuthorities(List<Role> roles) {
        var authorities = new ArrayList<GrantedAuthority>();
        for (var role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            role.getPermissions().stream()
                    .map(Permission::getPermission)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        return authorities;
    }

    public InMemoryUserDetailsManager getUserDetailsManager() {
        return userDetailsManager;
    }

    public List<UserDetails> listUsers() {
        return List.copyOf(users.values());
    }

    public boolean userExists(String username) {
        return userDetailsManager.userExists(username);
    }

    public void createUser(String username, String password, List<String> roles, PasswordEncoder passwordEncoder) {
        var user = buildUser(username, password, roles, passwordEncoder);
        userDetailsManager.createUser(user);
        users.put(username, user);
    }

    public void updateUser(String username, String password, List<String> roles, PasswordEncoder passwordEncoder) {
        if (!userExists(username)) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        var existing = userDetailsManager.loadUserByUsername(username);
        var user = rebuildUser(existing, password, roles, passwordEncoder);
        userDetailsManager.updateUser(user);
        users.put(username, user);
    }

    public void setUserEnabled(String username, boolean enabled) {
        if (!userExists(username)) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        var existing = userDetailsManager.loadUserByUsername(username);
        var updated = User.withUserDetails(existing)
                .disabled(!enabled)
                .build();

        userDetailsManager.updateUser(updated);
        users.put(username, updated);
    }

    public void deleteUser(String username) {
        if (!userExists(username)) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        userDetailsManager.deleteUser(username);
        users.remove(username);
    }

    public boolean isUserEnabled(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        var cached = users.get(username);
        if (cached != null) {
            return cached.isEnabled();
        }
        if (!userExists(username)) {
            return false;
        }
        return userDetailsManager.loadUserByUsername(username).isEnabled();
    }
}
