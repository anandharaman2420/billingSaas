package com.saasbilling.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbilling.payment.RazorpayClient;
import com.saasbilling.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Receives Razorpay webhook events. This endpoint is public (no JWT -
 * Razorpay's servers call it), so authentication is entirely the HMAC
 * signature check: anything without a valid X-Razorpay-Signature over
 * the raw body is rejected before its content is even parsed.
 *
 * NOT exercised end-to-end in this repo's sandbox - needs real
 * Razorpay test-mode keys and a webhook pointed at this URL.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RazorpayClient razorpayClient;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public WebhookController(RazorpayClient razorpayClient, SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this.razorpayClient = razorpayClient;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(@RequestBody String rawBody,
                                          @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (!razorpayClient.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected Razorpay webhook with missing/invalid signature");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();

            // "order.paid" carries both the order (with our businessId/plan notes) and the payment.
            if ("order.paid".equals(event)) {
                JsonNode notes = root.path("payload").path("order").path("entity").path("notes");
                String paymentId = root.path("payload").path("payment").path("entity").path("id").asText(null);
                String businessIdText = notes.path("businessId").asText(null);
                String plan = notes.path("plan").asText(null);

                if (businessIdText != null && plan != null) {
                    subscriptionService.applyProviderPlanChange(UUID.fromString(businessIdText), plan, paymentId);
                } else {
                    log.warn("Razorpay order.paid event missing businessId/plan notes - ignored");
                }
            }
        } catch (Exception e) {
            // Log and still 200: the signature was valid, so retrying the same payload won't help.
            log.error("Failed to process verified Razorpay webhook", e);
        }

        return ResponseEntity.ok().build();
    }
}
