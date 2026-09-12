package com.saasbilling.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Verifies the JWT on every request and populates:
 *   1. Spring's SecurityContext (for @PreAuthorize / hasRole checks)
 *   2. TenantContext (the single source of truth every service uses to
 *      scope queries to the caller's business)
 *
 * The business id is taken exclusively from the verified token's claims.
 * It is never read from a header, query param, or request body field,
 * which is what prevents cross-tenant access (IDOR).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.parseAndValidate(token);

            if (!"access".equals(claims.get("type", String.class))) {
                throw new JwtException("Not an access token");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            UUID businessId = UUID.fromString(claims.get("businessId", String.class));
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            TenantContext.set(new TenantContext.AuthenticatedPrincipal(userId, businessId, email, role));

            var authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
        } finally {
            // Always clear thread-locals at the end of the request - the
            // container may reuse this thread for an unrelated request.
            TenantContext.clear();
        }
    }
}
