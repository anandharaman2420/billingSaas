package com.saasbilling.controller;

import com.saasbilling.entity.User;
import com.saasbilling.security.TenantContext;
import com.saasbilling.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny read-only endpoint that proves the JWT -> TenantContext ->
 * tenant-scoped-query chain works end to end.
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

        User user = userService.getCurrentUserWithBusiness(principal.userId(), principal.businessId());

        return Map.of(
                "userId", user.getId(),
                "businessId", user.getBusiness().getId(),
                "businessName", user.getBusiness().getBusinessName(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        );
    }
}
