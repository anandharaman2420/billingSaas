package com.saasbilling.controller;

import com.saasbilling.dto.subscription.SubscriptionDtos.ChangePlanRequest;
import com.saasbilling.dto.subscription.SubscriptionDtos.SubscriptionResponse;
import com.saasbilling.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public SubscriptionResponse get() {
        return subscriptionService.get();
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/plan")
    public SubscriptionResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return subscriptionService.changePlan(request);
    }
}
