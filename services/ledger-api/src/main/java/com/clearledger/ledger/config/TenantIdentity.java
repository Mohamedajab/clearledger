package com.clearledger.ledger.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class TenantIdentity {
    private TenantIdentity() {}

    public static String require(Jwt jwt) {
        Object claim = jwt == null ? null : jwt.getClaims().get("tenant_id");
        if (!(claim instanceof String tenant) || !tenant.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new AccessDeniedException("A verified tenant identity is required");
        }
        return tenant;
    }
}
