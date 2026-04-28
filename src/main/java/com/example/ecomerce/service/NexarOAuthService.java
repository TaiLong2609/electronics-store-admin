package com.example.ecomerce.service;

import com.example.ecomerce.config.NexarProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Service
public class NexarOAuthService {

    private static final long DEFAULT_TOKEN_TTL_SECONDS = 24 * 60 * 60;
    private static final long EXPIRY_SAFETY_WINDOW_SECONDS = 120;

    private final RestClient restClient;
    private final NexarProperties nexarProperties;

    private volatile TokenCache tokenCache;

    public NexarOAuthService(NexarProperties nexarProperties) {
        this.restClient = RestClient.create();
        this.nexarProperties = nexarProperties;
    }

    public synchronized String getAccessToken() {
        return getAccessToken(null, null, null, null);
    }

    public synchronized String getAccessToken(
            String clientIdOverride,
            String clientSecretOverride,
            String scopeOverride,
            String tokenUrlOverride
    ) {
        boolean hasOverride = StringUtils.hasText(clientIdOverride)
                || StringUtils.hasText(clientSecretOverride)
                || StringUtils.hasText(scopeOverride)
                || StringUtils.hasText(tokenUrlOverride);

        if (!hasOverride && tokenCache != null && Instant.now().isBefore(tokenCache.expiresAt())) {
            return tokenCache.accessToken();
        }

        var oauth = nexarProperties.getOauth();
        String clientId = StringUtils.hasText(clientIdOverride) ? clientIdOverride.trim() : oauth.getClientId();
        String clientSecret = StringUtils.hasText(clientSecretOverride) ? clientSecretOverride.trim() : oauth.getClientSecret();
        String scope = StringUtils.hasText(scopeOverride) ? scopeOverride.trim() : oauth.getScope();
        String tokenUrl = StringUtils.hasText(tokenUrlOverride) ? tokenUrlOverride.trim() : oauth.getTokenUrl();

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException("Missing Nexar credentials. Set NEXAR_CLIENT_ID/NEXAR_CLIENT_SECRET or provide clientId/clientSecret from the admin sync form.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if (StringUtils.hasText(scope)) {
            form.add("scope", scope);
        }

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Failed to obtain Nexar token: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Nexar token response: " + ex.getMessage(), ex);
        }

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new IllegalStateException("Nexar token response is empty or missing access_token.");
        }

        long expiresIn = response.expiresIn() != null ? response.expiresIn() : DEFAULT_TOKEN_TTL_SECONDS;
        long effectiveTtl = Math.max(60, expiresIn - EXPIRY_SAFETY_WINDOW_SECONDS);
        if (!hasOverride) {
            tokenCache = new TokenCache(response.accessToken(), Instant.now().plusSeconds(effectiveTtl));
            return tokenCache.accessToken();
        }
        return response.accessToken();
    }

    private record TokenCache(String accessToken, Instant expiresAt) {
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
