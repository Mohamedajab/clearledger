package com.clearledger.ledger.config;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenPolicyTest {
    private Jwt token(String issuer, String audience, Object tenant, Instant expires) {
        var builder = Jwt.withTokenValue("test").header("alg", "RS256").issuer(issuer)
            .subject("operator").audience(List.of(audience)).issuedAt(expires.minusSeconds(600)).expiresAt(expires);
        if (tenant != null) builder.claim("tenant_id", tenant);
        return builder.build();
    }

    @Test
    void validatesIssuerAudienceExpiryAndTenant() {
        var policy = TokenPolicy.validator("https://identity.example", "clearledger-api");
        var future = Instant.now().plusSeconds(300);
        assertThat(policy.validate(token("https://identity.example", "clearledger-api", "northstar", future)).hasErrors()).isFalse();
        assertThat(policy.validate(token("https://evil.example", "clearledger-api", "northstar", future)).hasErrors()).isTrue();
        assertThat(policy.validate(token("https://identity.example", "another-api", "northstar", future)).hasErrors()).isTrue();
        assertThat(policy.validate(token("https://identity.example", "clearledger-api", null, future)).hasErrors()).isTrue();
        assertThat(policy.validate(token("https://identity.example", "clearledger-api", List.of("northstar"), future)).hasErrors()).isTrue();
        assertThat(policy.validate(token("https://identity.example", "clearledger-api", "../other", future)).hasErrors()).isTrue();
        assertThat(policy.validate(token("https://identity.example", "clearledger-api", "northstar", Instant.now().minusSeconds(120))).hasErrors()).isTrue();
    }

    @Test
    void grantsOnlyKnownPermissionRoles() {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256")
            .claim("scope", "profile").claim("realm_access", Map.of("roles", List.of("ledger:read", "admin"))).build();
        assertThat(TokenPolicy.authorities(jwt)).extracting("authority").contains("SCOPE_ledger:read").doesNotContain("SCOPE_ledger:write", "SCOPE_admin");
    }

    @Test
    void missingTenantIsDeniedEvenForMockAuthentication() {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("operator").build();
        assertThatThrownBy(() -> TenantIdentity.require(jwt)).isInstanceOf(AccessDeniedException.class);
    }
}
