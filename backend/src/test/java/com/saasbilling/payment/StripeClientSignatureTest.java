package com.saasbilling.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class StripeClientSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private final StripeClient client = new StripeClient("sk_test", SECRET, "http://localhost:4200");

    @Test
    void acceptsValidTimestampedSignature() throws Exception {
        String body = "{\"type\":\"checkout.session.completed\"}";
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertThat(client.verifyWebhookSignature(body, header(timestamp, body, SECRET))).isTrue();
    }

    @Test
    void rejectsTamperedBodyAndWrongSecret() throws Exception {
        String body = "{\"type\":\"checkout.session.completed\"}";
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertThat(client.verifyWebhookSignature("{\"type\":\"forged\"}",
                header(timestamp, body, SECRET))).isFalse();
        assertThat(client.verifyWebhookSignature(body,
                header(timestamp, body, "attacker-secret"))).isFalse();
    }

    @Test
    void rejectsStaleAndMissingSignatures() throws Exception {
        String body = "{}";
        String staleTimestamp = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());

        assertThat(client.verifyWebhookSignature(body, header(staleTimestamp, body, SECRET))).isFalse();
        assertThat(client.verifyWebhookSignature(body, null)).isFalse();
    }

    private static String header(String timestamp, String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(
                (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }
}
