package com.example.ecomerce.config;

import com.example.ecomerce.service.AccountService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 * Cấu hình bảo mật trung tâm của toàn bộ backend:
 * - Khai báo quyền cho từng endpoint
 * - Cấu hình JWT (decode/encode + authority mapping)
 * - Cấu hình CORS cho frontend React
 */
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Browser (React) needs CORS preflight for Authorization header.
                .cors(Customizer.withDefaults())
                // For a stateless REST API (e.g., Postman), CSRF usually gets in the way for POST/PUT/DELETE.
                .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/test-mouser").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/products").hasAuthority("PRODUCT_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/products/**").hasAuthority("PRODUCT_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/products/**").hasAuthority("PRODUCT_DELETE")
                        .requestMatchers("/stats/**").hasAuthority("STATS_VIEW")
                        .requestMatchers("/transactions/**").hasAuthority("STATS_VIEW")
                        .requestMatchers("/users/**").hasAuthority("USER_MANAGE")
                        .requestMatchers("/settings/**").hasAuthority("USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/cart").hasAuthority("CART_VIEW")
                        .requestMatchers(HttpMethod.POST, "/cart").hasAuthority("CART_MODIFY")
                        .requestMatchers(HttpMethod.DELETE, "/cart/**").hasAuthority("CART_MODIFY")
                        .requestMatchers(HttpMethod.POST, "/orders").hasAuthority("ORDER_CREATE")
                        .requestMatchers(HttpMethod.GET, "/orders").hasAnyAuthority("ORDER_VIEW_SELF", "ORDER_VIEW_ALL")
                        .requestMatchers(HttpMethod.PUT, "/orders/**").hasAuthority("ORDER_MODIFY_STATUS")
                        .requestMatchers(HttpMethod.GET, "/inventory/stocks", "/inventory/low-stock", "/inventory/categories", "/inventory/ledger/**", "/inventory/reports/**").hasAuthority("PRODUCT_VIEW")
                        .requestMatchers(HttpMethod.GET, "/inventory/orders").hasAnyAuthority("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE")
                        .requestMatchers(HttpMethod.POST, "/inventory/**").hasAnyAuthority("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE")
                        .requestMatchers(HttpMethod.PATCH, "/inventory/**").hasAnyAuthority("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE")
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/marketing/**").hasAuthority("MARKETING_MANAGE")
                        .requestMatchers("/me").authenticated()
                        .anyRequest().authenticated()
                )
                    .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                    )
                // Disable HTML login redirect (better behavior for APIs).
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(AccountService accountService) {
        return accountService.getUserDetailsManager();
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.security.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret must not be blank");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AccountService accountService) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> enabledValidator = jwt -> {
            String username = jwt.getSubject();
            if (accountService.isUserEnabled(username)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "User is disabled", null)
            );
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, enabledValidator));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return jwt -> {
            Object raw = jwt.getClaims().get("authorities");
            if (!(raw instanceof Collection<?> items)) {
                return List.<GrantedAuthority>of();
            }
            return items.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(SimpleGrantedAuthority::new)
                    .map(a -> (GrantedAuthority) a)
                    .toList();
        };
    }

    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
            "ROLE_SUPER_ADMIN > ROLE_SALES\n" +
            "ROLE_SUPER_ADMIN > ROLE_WAREHOUSE\n" +
            "ROLE_SUPER_ADMIN > ROLE_CUSTOMER\n" +
            "ROLE_SUPER_ADMIN > ROLE_CONTENT"
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
