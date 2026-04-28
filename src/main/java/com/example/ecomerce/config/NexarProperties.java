package com.example.ecomerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nexar")
public class NexarProperties {

    private String graphqlUrl = "https://api.nexar.com/graphql";
    private int defaultLimit = 20;
    private final OAuth oauth = new OAuth();

    public String getGraphqlUrl() {
        return graphqlUrl;
    }

    public void setGraphqlUrl(String graphqlUrl) {
        this.graphqlUrl = graphqlUrl;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public static class OAuth {
        private String tokenUrl = "https://identity.nexar.com/connect/token";
        private String clientId;
        private String clientSecret;
        private String scope = "supply.domain";

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}