package com.clearledger.ledger.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class TokenPolicy {
    private TokenPolicy() {}

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> identity = jwt -> {
            Object tenant = jwt.getClaims().get("tenant_id");
            if (!jwt.getAudience().contains(audience) || !(tenant instanceof String value)
                    || !value.matches("[a-zA-Z0-9_-]{1,64}")) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience or tenant identity", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), identity);
    }

    public static Collection<GrantedAuthority> authorities(Jwt jwt) {
        var result = new ArrayList<GrantedAuthority>(new JwtGrantedAuthoritiesConverter().convert(jwt));
        Object access = jwt.getClaims().get("realm_access");
        if (access instanceof Map<?, ?> realm && realm.get("roles") instanceof Collection<?> roles) {
            for (String permission : List.of("ledger:read", "ledger:write")) {
                if (roles.contains(permission)) result.add(new SimpleGrantedAuthority("SCOPE_" + permission));
            }
        }
        return result;
    }
}
