package com.saasbilling.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test - no Spring context, no network. */
class RazorpayClientSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private final RazorpayClient client = new RazorpayClient("key", "secret", SECRET);

    @Test
    void acceptsCorrectSignature() throws Exception {
        String body = "{\"event\":\"order.paid\"}";
        assertThat(client.verifyWebhookSignature(body, sign(body, SECRET))).isTrue();
    }

    @Test
    void rejectsSignatureMadeWithWrongSecret() throws Exception {
        String body = "{\"event\":\"order.paid\"}";
        assertThat(client.verifyWebhookSignature(body, sign(body, "attacker-secret"))).isFalse();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String signedBody = "{\"event\":\"order.paid\",\"plan\":\"BASIC\"}";
        String tamperedBody = "{\"event\":\"order.paid\",\"plan\":\"BUSINESS\"}";
        assertThat(client.verifyWebhookSignature(tamperedBody, sign(signedBody, SECRET))).isFalse();
    }

    @Test
    void rejectsMissingSignature() {
        assertThat(client.verifyWebhookSignature("{}", null)).isFalse();
    }

    @Test
    void rejectsEverythingWhenWebhookSecretNotConfigured() throws Exception {
        RazorpayClient unconfigured = new RazorpayClient("", "", "");
        String body = "{}";
        // Even a "valid" signature over an empty secret must not be accepted.
        assertThat(unconfigured.verifyWebhookSignature(body, sign(body, ""))).isFalse();
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.isEmpty() ? new byte[]{0} : secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder sb = new StringBuilder();
        for (byte b : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
