package com.technnext.hrms.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Acquires (and caches) an OAuth2 app-only access token for Microsoft Graph,
 * using the client-credentials flow against the "TechNext HRMS Mailer" Azure
 * AD App Registration.
 *
 * Requires (as env vars): AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET.
 * The app registration must have Microsoft Graph -> Mail.Send (Application
 * permission) granted with admin consent.
 */
@Service
@Slf4j
public class GraphTokenService {

    private static final String SCOPE = "https://graph.microsoft.com/.default";

    @Value("${azure.graph.tenant-id}")
    private String tenantId;

    @Value("${azure.graph.client-id}")
    private String clientId;

    @Value("${azure.graph.client-secret}")
    private String clientSecret;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache: client-credentials tokens are typically valid ~60-90
    // minutes, so we cache and only refresh when close to expiry. Synchronized so
    // concurrent requests don't all fetch a new token at once.
    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public synchronized String getAccessToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }
        if (tenantId == null || tenantId.isBlank()
                || clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "Azure AD Graph credentials are not configured " +
                    "(AZURE_TENANT_ID / AZURE_CLIENT_ID / AZURE_CLIENT_SECRET).");
        }

        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String form = "client_id=" + urlEncode(clientId)
                + "&scope=" + urlEncode(SCOPE)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[GraphTokenService] Token request failed: HTTP {} — {}",
                    response.statusCode(), response.body());
            throw new IllegalStateException(
                    "Failed to acquire Microsoft Graph token (HTTP " + response.statusCode() + ")");
        }

        JsonNode json = objectMapper.readTree(response.body());
        String accessToken = json.path("access_token").asText(null);
        int expiresInSeconds = json.path("expires_in").asInt(3600);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Graph token response did not contain an access_token.");
        }

        this.cachedToken = accessToken;
        // Refresh 2 minutes early to avoid edge-of-expiry failures.
        this.cachedTokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 120));
        return accessToken;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}