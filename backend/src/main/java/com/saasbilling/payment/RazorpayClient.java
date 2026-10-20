package com.saasbilling.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Thin client over Razorpay's REST API (https://razorpay.com/docs/api/orders/),
 * using Spring's RestClient directly rather than Razorpay's Java SDK - the
 * API is plain HTTPS + Basic auth, so the SDK isn't needed.
 *
 * NOT exercised end-to-end in this repo's sandbox: real testing needs
 * genuine Razorpay test-mode keys (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET).
 * Until those are set, {@link #isConfigured()} is false and the
 * subscription checkout endpoint refuses to start a checkout.
 */
@Service
public class RazorpayClient {

    private static final String API_BASE = "https://api.razorpay.com/v1";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient restClient;

    public RazorpayClient(@Value("${app.razorpay.key-id:}") String keyId,
                           @Value("${app.razorpay.key-secret:}") String keySecret,
                           @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.restClient = RestClient.builder().baseUrl(API_BASE).build();
    }

    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank() && !webhookSecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    /** Creates a Razorpay order; the frontend then opens Razorpay Checkout with the returned order id. */
    public CreatedOrder createOrder(BigDecimal amountInr, String receipt, Map<String, String> notes) {
        if (!isConfigured()) {
            throw new IllegalStateException("Razorpay is not configured");
        }

        long amountPaise = amountInr.multiply(BigDecimal.valueOf(100)).longValueExact();
        String basicAuth = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        JsonNode response = restClient.post()
                .uri("/orders")
                .header("Authorization", "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
                .retrieve()
                .body(JsonNode.class);

        return new CreatedOrder(response.get("id").asText(), amountPaise, "INR");
    }

    /**
     * Verifies Razorpay's webhook signature: HMAC-SHA256 of the raw
     * request body using the webhook secret, compared to the
     * X-Razorpay-Signature header. Uses a constant-time comparison so
     * the check doesn't leak how many leading characters matched.
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (webhookSecret.isBlank() || signatureHeader == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedHex = toHex(expected);
            return java.security.MessageDigest.isEqual(
                    expectedHex.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record CreatedOrder(String orderId, long amountPaise, String currency) {
    }
}
