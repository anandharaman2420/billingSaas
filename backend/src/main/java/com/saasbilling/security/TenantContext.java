package com.saasbilling.security;

import java.util.UUID;

/**
 * Holds the current request's authenticated business (tenant) id and user id.
 *
 * This is populated ONLY by {@link JwtAuthenticationFilter} from a verified
 * JWT's claims - never from a request parameter, path variable, or body.
 * Every service method that queries or mutates tenant-scoped data MUST read
 * the business id from here, not from anything the client sent.
 */
public final class TenantContext {

    private static final ThreadLocal<AuthenticatedPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(AuthenticatedPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthenticatedPrincipal get() {
        AuthenticatedPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException(
                    "TenantContext accessed outside of an authenticated request. " +
                    "Every tenant-scoped repository call must happen inside a request " +
                    "that has passed through JwtAuthenticationFilter.");
        }
        return principal;
    }

    public static UUID currentBusinessId() {
        return get().businessId();
    }

    public static UUID currentUserId() {
        return get().userId();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record AuthenticatedPrincipal(UUID userId, UUID businessId, String email, String role) {
    }
}
