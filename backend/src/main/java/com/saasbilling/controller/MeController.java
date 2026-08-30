package com.saasbilling.controller;

import com.saasbilling.security.TenantContext;
import com.saasbilling.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny read-only endpoint that proves the JWT -> TenantContext ->
 * tenant-scoped-query chain works end to end. Useful for a smoke test
 * right after Phase 1 deploy, before any real domain module exists.
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var principal = TenantContext.get();

        Map<String, Object> user = userService.getCurrentUserSummary(principal.userId(), principal.businessId());

        return Map.of(
                "userId", user.get("userId"),
                "businessId", user.get("businessId"),
                "businessName", user.get("businessName"),
                "fullName", user.get("fullName"),
                "email", user.get("email"),
                "role", user.get("role")
        );
    }
}
