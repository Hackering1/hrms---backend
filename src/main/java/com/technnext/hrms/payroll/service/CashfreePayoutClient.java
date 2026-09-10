package com.technnext.hrms.payroll.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Raw client for Cashfree Payouts v2 (https://docs.cashfree.com/reference/payouts-version2-apis).
 * Same shape as GraphTokenService (JDK HttpClient, no extra dependency) —
 * kept deliberately dumb (no retries, no business logic): PayoutService owns
 * all the safety gating (approval-only, idempotency, amount reconciliation).
 *
 * Auth: x-client-id / x-client-secret headers (NOT OAuth — simpler than
 * Graph's client-credentials flow, no token to cache).
 *
 * IMPORTANT — verified against Cashfree's public docs at build time, but
 * Cashfree can change response shapes; before relying on this in production,
 * run a real batch transfer + status check against the TEST/sandbox
 * environment and compare against what's implemented here.
 */
@Service
@Slf4j
public class CashfreePayoutClient {

    @Value("${app.cashfree.enabled}")
    private boolean enabled;

    @Value("${app.cashfree.environment}")
    private String environment; // TEST | PROD

    @Value("${app.cashfree.client-id}")
    private String clientId;

    @Value("${app.cashfree.client-secret}")
    private String clientSecret;

    @Value("${app.cashfree.api-version}")
    private String apiVersion;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isEnabled() { return enabled; }
    public String getEnvironment() { return environment; }

    private String baseUrl() {
        return "PROD".equalsIgnoreCase(environment)
                ? "https://api.cashfree.com/payout"
                : "https://sandbox.cashfree.com/payout";
    }

    private void assertConfigured() {
        if (!enabled) {
            throw new IllegalStateException("Cashfree Payouts is not enabled (app.cashfree.enabled=false).");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Cashfree credentials are not configured (CASHFREE_CLIENT_ID / CASHFREE_CLIENT_SECRET).");
        }
    }

    public record BeneficiaryResult(String beneficiaryId, String status) {}

    /**
     * Registers a beneficiary. Cashfree treats beneficiary_id as the idempotency
     * key — calling this again with the same id for the same account is safe
     * (returns the existing beneficiary rather than erroring), so callers don't
     * need to pre-check existence themselves.
     */
    public BeneficiaryResult createBeneficiary(String beneficiaryId, String beneficiaryName,
                                                String bankAccountNumber, String bankIfsc, String phone) {
        assertConfigured();
        try {
            java.util.Map<String, Object> instrument = new java.util.LinkedHashMap<>();
            instrument.put("bank_account_number", bankAccountNumber);
            instrument.put("bank_ifsc", bankIfsc);

            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("beneficiary_id", beneficiaryId);
            payload.put("beneficiary_name", beneficiaryName);
            payload.put("beneficiary_instrument_details", instrument);
            if (phone != null && !phone.isBlank()) {
                java.util.Map<String, Object> contact = new java.util.LinkedHashMap<>();
                contact.put("beneficiary_phone", phone);
                contact.put("beneficiary_country_code", "+91");
                payload.put("beneficiary_contact_details", contact);
            }
            String body = objectMapper.writeValueAsString(payload);

            HttpResponse<String> response = post("/beneficiary", body);
            // A 409 "beneficiary already exists" is not an error for our purposes —
            // it means a previous attempt already registered them.
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                return new BeneficiaryResult(
                        json.path("beneficiary_id").asText(beneficiaryId),
                        json.path("beneficiary_status").asText("UNKNOWN"));
            }
            if (response.statusCode() == 409) {
                return new BeneficiaryResult(beneficiaryId, "ALREADY_EXISTS");
            }
            log.error("[CashfreePayoutClient] createBeneficiary failed: HTTP {} — {}", response.statusCode(), response.body());
            throw new IllegalStateException("Cashfree createBeneficiary failed (HTTP " + response.statusCode() + "): " + response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cashfree createBeneficiary request failed: " + e.getMessage(), e);
        }
    }

    public record TransferLine(String transferId, BigDecimal amount, String beneficiaryId) {}
    public record BatchResult(String batchTransferId, String cfBatchTransferId, String status) {}

    /** Initiates a batch transfer (async — Cashfree acknowledges receipt; use getBatchStatus to poll the real outcome). */
    public BatchResult batchTransfer(String batchTransferId, List<TransferLine> transfers) {
        assertConfigured();
        try {
            List<Object> transferList = new java.util.ArrayList<>();
            for (TransferLine t : transfers) {
                java.util.Map<String, Object> beneficiary = new java.util.LinkedHashMap<>();
                beneficiary.put("beneficiary_id", t.beneficiaryId());
                java.util.Map<String, Object> line = new java.util.LinkedHashMap<>();
                line.put("transfer_id", t.transferId());
                line.put("transfer_amount", t.amount());
                line.put("beneficiary_details", beneficiary);
                transferList.add(line);
            }

            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("batch_transfer_id", batchTransferId);
            payload.put("transfers", transferList);
            String body = objectMapper.writeValueAsString(payload);

            HttpResponse<String> response = post("/transfers/batch", body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                return new BatchResult(
                        json.path("batch_transfer_id").asText(batchTransferId),
                        json.path("cf_batch_transfer_id").asText(null),
                        json.path("status").asText("RECEIVED"));
            }
            log.error("[CashfreePayoutClient] batchTransfer failed: HTTP {} — {}", response.statusCode(), response.body());
            throw new IllegalStateException("Cashfree batchTransfer failed (HTTP " + response.statusCode() + "): " + response.body()
                    + " — per Cashfree's guidance, do NOT retry a 5xx automatically; check status first.");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cashfree batchTransfer request failed: " + e.getMessage(), e);
        }
    }

    public record TransferStatus(String transferId, String cfTransferId, String status, String statusDescription, String utr) {}

    /**
     * Fetches the status of every transfer in a batch. NOTE: the exact "get
     * batch status" endpoint path is inferred from Cashfree's REST convention
     * (GET /transfers/batch?batch_transfer_id=... mirrors their documented
     * GET /transfers?transfer_id=... for single transfers) — Cashfree's public
     * docs describe the endpoint's existence but not its exact path/response
     * shape at the time this was written. VERIFY this against a real sandbox
     * call before depending on it in production; PayoutService falls back to
     * per-transfer getTransferStatus() if this doesn't return usable data.
     */
    public List<TransferStatus> getBatchStatus(String batchTransferId) {
        assertConfigured();
        try {
            HttpResponse<String> response = get("/transfers/batch?batch_transfer_id=" + urlEncode(batchTransferId));
            if (response.statusCode() != 200) {
                log.warn("[CashfreePayoutClient] getBatchStatus HTTP {} — falling back to per-transfer lookups", response.statusCode());
                return List.of();
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode transfers = json.path("transfers");
            List<TransferStatus> results = new java.util.ArrayList<>();
            if (transfers.isArray()) {
                for (JsonNode t : transfers) {
                    results.add(parseTransferStatus(t));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[CashfreePayoutClient] getBatchStatus failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Single-transfer status lookup — the documented, confirmed fallback if batch status parsing doesn't line up. */
    public TransferStatus getTransferStatus(String transferId) {
        assertConfigured();
        try {
            HttpResponse<String> response = get("/transfers?transfer_id=" + urlEncode(transferId));
            if (response.statusCode() != 200) {
                return new TransferStatus(transferId, null, "UNKNOWN", "HTTP " + response.statusCode(), null);
            }
            return parseTransferStatus(objectMapper.readTree(response.body()));
        } catch (Exception e) {
            return new TransferStatus(transferId, null, "UNKNOWN", e.getMessage(), null);
        }
    }

    private TransferStatus parseTransferStatus(JsonNode json) {
        return new TransferStatus(
                json.path("transfer_id").asText(null),
                json.path("cf_transfer_id").asText(null),
                json.path("status").asText("UNKNOWN"),
                json.path("status_description").asText(null),
                json.path("utr").asText(null)
        );
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-version", apiVersion)
                .header("x-client-id", clientId)
                .header("x-client-secret", clientSecret)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String pathWithQuery) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + pathWithQuery))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-version", apiVersion)
                .header("x-client-id", clientId)
                .header("x-client-secret", clientSecret)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}