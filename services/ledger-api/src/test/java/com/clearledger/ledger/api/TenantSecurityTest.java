package com.clearledger.ledger.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.clearledger.ledger.account.*;
import com.clearledger.ledger.config.SecurityConfig;
import com.clearledger.ledger.ledger.JournalEntryRepository;
import com.clearledger.ledger.outbox.*;
import com.clearledger.ledger.payment.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, DashboardController.class, LedgerController.class, PaymentController.class})
@Import(SecurityConfig.class)
class TenantSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AccountRepository accounts;
    @MockitoBean PaymentIntentRepository payments;
    @MockitoBean JournalEntryRepository entries;
    @MockitoBean OutboxEventRepository outbox;
    @MockitoBean PaymentService service;

    @Test
    void invalidPaymentKeysAreBadRequestsWithoutCallingTheService() throws Exception {
        for (String key : new String[] { null, "", " ", "x".repeat(129) }) {
            var request = post("/api/v1/payments").contentType("application/json").content("""
                {"merchantReference":"ORDER-1","sourceAccountId":"11111111-1111-1111-1111-111111111111",
                 "destinationAccountId":"22222222-2222-2222-2222-222222222222","amountMinor":100,"currency":"GBP"}
                """)
                .with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
                    .authorities(new SimpleGrantedAuthority("SCOPE_ledger:write")));
            if (key != null) request.header("Idempotency-Key", key);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void malformedPaymentJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-1")
            .contentType("application/json").content("{")
            .with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
                .authorities(new SimpleGrantedAuthority("SCOPE_ledger:write"))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedDatabaseFailuresRemainSanitizedServerErrors() throws Exception {
        when(service.create(anyString(), anyString(), any())).thenThrow(
            new org.springframework.dao.DataIntegrityViolationException("private database detail",
                new org.hibernate.exception.ConstraintViolationException("private constraint detail", null, (String) null)));
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-1")
            .contentType("application/json").content("""
                {"merchantReference":"ORDER-1","sourceAccountId":"11111111-1111-1111-1111-111111111111",
                 "destinationAccountId":"22222222-2222-2222-2222-222222222222","amountMinor":100,"currency":"GBP"}
                """)
            .with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
                .authorities(new SimpleGrantedAuthority("SCOPE_ledger:write"))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Unexpected server error"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }

    @Test
    void callerCannotSelectAnotherTenantWithAHeader() throws Exception {
        when(accounts.findAllByTenantIdOrderByCreatedAtAsc("northstar")).thenReturn(List.of());
        mvc.perform(get("/api/v1/accounts").header("X-Tenant-Id", "other")
            .with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
                .authorities(new SimpleGrantedAuthority("SCOPE_ledger:read"))))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(accounts).findAllByTenantIdOrderByCreatedAtAsc("northstar");
        verify(accounts, never()).findAllByTenantIdOrderByCreatedAtAsc("other");
    }

    @Test
    void missingTenantClaimCannotReachRepository() throws Exception {
        mvc.perform(get("/api/v1/accounts").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_ledger:read"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void readerCannotCreatePayments() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType("application/json").content("{}")
            .with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
                .authorities(new SimpleGrantedAuthority("SCOPE_ledger:read"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void dashboardCountsOnlyTheVerifiedTenantsPendingEvents() throws Exception {
        when(accounts.findAllByTenantIdOrderByCreatedAtAsc("northstar")).thenReturn(List.of());
        when(outbox.countForTenant("northstar", OutboxEvent.Status.PENDING)).thenReturn(3L);
        mvc.perform(get("/api/v1/dashboard").with(jwt().jwt(token -> token.claim("tenant_id", "northstar"))
            .authorities(new SimpleGrantedAuthority("SCOPE_ledger:read"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pendingEvents").value(3));
        verify(outbox).countForTenant("northstar", OutboxEvent.Status.PENDING);
        verifyNoMoreInteractions(outbox);
    }
}
