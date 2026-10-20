package com.saasbilling.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class StripeClient {

    private static final long WEBHOOK_TOLERANCE_SECONDS = 300;

    private final String secretKey;
    private final String webhookSecret;
    private final String frontendBaseUrl;
    private final RestClient restClient;

    public StripeClient(@Value("${app.stripe.secret-key:}") String secretKey,
                        @Value("${app.stripe.webhook-secret:}") String webhookSecret,
                        @Value("${app.mail.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.frontendBaseUrl = frontendBaseUrl;
        this.restClient = RestClient.builder().baseUrl("https://api.stripe.com/v1").build();
    }

    public boolean isConfigured() {
        return !secretKey.isBlank() && !webhookSecret.isBlank();
    }

    public CreatedSession createSubscriptionSession(String plan, BigDecimal amountInr, String businessId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured");
        }
        long amountMinor = amountInr.movePointRight(2).longValueExact();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("success_url", frontendBaseUrl + "/settings?checkout=success");
        form.add("cancel_url", frontendBaseUrl + "/settings?checkout=cancelled");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", "inr");
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amountMinor));
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][price_data][product_data][name]", plan + " monthly subscription");
        form.add("metadata[businessId]", businessId);
        form.add("metadata[plan]", plan);
        form.add("subscription_data[metadata][businessId]", businessId);
        form.add("subscription_data[metadata][plan]", plan);

        JsonNode response = restClient.post()
                .uri("/checkout/sessions")
                .headers(headers -> headers.setBasicAuth(secretKey, ""))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        return new CreatedSession(response.path("id").asText(), response.path("url").asText(),
                amountMinor, "INR");
    }

    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (webhookSecret.isBlank() || signatureHeader == null) {
            return false;
        }
        try {
            String timestamp = null;
            java.util.List<String> signatures = new java.util.ArrayList<>();
            for (String part : signatureHeader.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                if ("t".equals(pair[0])) timestamp = pair[1];
                if ("v1".equals(pair[0])) signatures.add(pair[1]);
            }
            if (timestamp == null || signatures.isEmpty()) return false;
            long signedAt = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (signedAt < now - WEBHOOK_TOLERANCE_SECONDS || signedAt > now + WEBHOOK_TOLERANCE_SECONDS) return false;
            byte[] expected = signatureFor(timestamp, rawBody);
            for (String signature : signatures) {
                byte[] candidate = parseHex(signature);
                if (candidate != null && MessageDigest.isEqual(expected, candidate)) return true;
            }
            return false;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    private byte[] signatureFor(String timestamp, String rawBody) throws java.security.GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] parseHex(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record CreatedSession(String sessionId, String checkoutUrl, long amountMinor, String currency) {
    }
}
