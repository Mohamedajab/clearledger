package com.clearledger.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentApiIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("clearledger")
        .withUsername("clearledger")
        .withPassword("clearledger");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("clearledger.outbox.publisher.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;

    @Test
    void paymentCommitsBalancesJournalAndIdempotentResponse() throws Exception {
        String body = """
            {
              "merchantReference": "ORDER-INTEGRATION-1",
              "sourceAccountId": "11111111-1111-1111-1111-111111111111",
              "destinationAccountId": "33333333-3333-3333-3333-333333333333",
              "amountMinor": 125000,
              "currency": "GBP",
              "description": "Integration settlement"
            }
            """;

        mvc.perform(post("/api/v1/payments")
                .header("X-Tenant-Id", "northstar")
                .header("Idempotency-Key", "integration-key-1")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.amountMinor").value(125000));

        mvc.perform(post("/api/v1/payments")
                .header("X-Tenant-Id", "northstar")
                .header("Idempotency-Key", "integration-key-1")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.merchantReference").value("ORDER-INTEGRATION-1"));
    }
}
