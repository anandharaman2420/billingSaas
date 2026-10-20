package com.saasbilling.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbilling.payment.RazorpayClient;
import com.saasbilling.payment.StripeClient;
import com.saasbilling.entity.SubscriptionProvider;
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
    private final StripeClient stripeClient;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public WebhookController(RazorpayClient razorpayClient, StripeClient stripeClient,
                             SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this.razorpayClient = razorpayClient;
        this.stripeClient = stripeClient;
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

    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(@RequestBody String rawBody,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!stripeClient.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected Stripe webhook with missing/invalid signature");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("type").asText();
            if ("checkout.session.completed".equals(eventType)
                    || "checkout.session.async_payment_succeeded".equals(eventType)) {
                JsonNode session = root.path("data").path("object");
                JsonNode metadata = session.path("metadata");
                String businessIdText = metadata.path("businessId").asText(null);
                String plan = metadata.path("plan").asText(null);
                String providerSubscriptionId = session.path("subscription").asText(null);
                String paymentStatus = session.path("payment_status").asText();
                boolean paid = "paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus);
                boolean subscriptionMode = "subscription".equals(session.path("mode").asText());
                if (businessIdText != null && plan != null && providerSubscriptionId != null
                        && subscriptionMode && (paid || "checkout.session.async_payment_succeeded".equals(eventType))) {
                    subscriptionService.applyProviderPlanChange(UUID.fromString(businessIdText), plan,
                            providerSubscriptionId, SubscriptionProvider.STRIPE);
                } else {
                    log.warn("Stripe checkout event is unpaid or missing subscription metadata - ignored");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process verified Stripe webhook", e);
        }
        return ResponseEntity.ok().build();
    }
}
