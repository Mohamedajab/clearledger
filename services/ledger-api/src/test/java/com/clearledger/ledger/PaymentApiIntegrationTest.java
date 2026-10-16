package com.clearledger.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("postgresAvailable")
class PaymentApiIntegrationTest {
    private static PostgreSQLContainer postgres;

    static boolean postgresAvailable() {
        return System.getenv("LEDGER_TEST_DATABASE_URL") != null
            || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("LEDGER_TEST_DATABASE_URL");
        if (url != null) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("LEDGER_TEST_DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("LEDGER_TEST_DATABASE_PASSWORD", ""));
        } else {
            postgres = new PostgreSQLContainer("postgres:17-alpine");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
        registry.add("clearledger.outbox.publisher.enabled", () -> "false");
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) postgres.stop();
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    String tenant;
    UUID source;
    UUID destination;
    UUID otherSource;
    UUID otherDestination;

    @BeforeEach
    void createIsolatedAccounts() {
        tenant = "test-" + UUID.randomUUID();
        source = account("source", 10_000);
        destination = account("destination", 0);
        otherSource = account("other-source", 10_000);
        otherDestination = account("other-destination", 0);
    }

    private UUID account(String reference, long balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into accounts (id, tenant_id, external_ref, name, currency, account_type, balance_minor)
            values (?, ?, ?, ?, 'GBP', 'WALLET', ?)
            """, id, tenant, reference, reference, balance);
        return id;
    }

    @Test
    void paymentCommitsBalancesJournalAndIdempotentResponse() throws Exception {
        PaymentRequest request = request("key-1", "ORDER-1", source, destination, 1_250);
        HttpResult created = pay(request);
        assertThat(created.status()).isEqualTo(201);
        assertThat(pay(request)).isEqualTo(created);
        assertSingleTransfer(1_250);
    }

    @Test
    void simultaneousIdenticalRetriesReturnTheSamePayment() throws Exception {
        PaymentRequest request = request("same-key", "SAME-ORDER", source, destination, 1_250);
        List<HttpResult> responses = runTogether(request, request);
        assertThat(responses).extracting(HttpResult::status).containsExactly(201, 201);
        assertThat(responses.getFirst()).isEqualTo(responses.getLast());
        assertSingleTransfer(1_250);
    }

    @Test
    void apiDocumentationAssetsLoadWithoutAnAccessToken() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void simultaneousKeyReuseForDifferentAccountsReturnsConflictAndRollsBack() throws Exception {
        List<HttpResult> responses = runTogether(
            request("shared-key", "ORDER-A", source, destination, 500),
            request("shared-key", "ORDER-B", otherSource, otherDestination, 500));
        assertThat(responses).extracting(HttpResult::status).containsExactlyInAnyOrder(201, 409);
        assertThat(count("payment_intents")).isEqualTo(1);
        assertThat(count("journal_entries")).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(1);
        assertThat(balance(source) + balance(otherSource)).isEqualTo(19_500);
        assertThat(balance(destination) + balance(otherDestination)).isEqualTo(500);
    }

    @Test
    void simultaneousKeyReuseForDifferentAmountsReturnsConflict() throws Exception {
        List<HttpResult> responses = runTogether(
            request("shared-key", "ORDER-A", source, destination, 500),
            request("shared-key", "ORDER-B", source, destination, 600));
        assertThat(responses).extracting(HttpResult::status).containsExactlyInAnyOrder(201, 409);
        long amount = jdbc.queryForObject("select amount_minor from payment_intents where tenant_id = ?", Long.class, tenant);
        assertSingleTransfer(amount);
    }

    @Test
    void competingSpendsCannotOverdrawAnAccount() throws Exception {
        List<HttpResult> responses = runTogether(
            request("spend-a", "SPEND-A", source, destination, 8_000),
            request("spend-b", "SPEND-B", source, otherDestination, 8_000));
        assertThat(responses).extracting(HttpResult::status).containsExactlyInAnyOrder(201, 422);
        assertThat(balance(source)).isEqualTo(2_000);
        assertThat(balance(destination) + balance(otherDestination)).isEqualTo(8_000);
        assertThat(count("payment_intents")).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(1);
    }

    @Test
    void duplicateMerchantReferenceReturnsConflictWithoutAnotherDebit() throws Exception {
        assertThat(pay(request("first-key", "ORDER-1", source, destination, 500)).status()).isEqualTo(201);
        assertThat(pay(request("second-key", "ORDER-1", source, destination, 500)).status()).isEqualTo(409);
        assertSingleTransfer(500);
    }

    private void assertSingleTransfer(long amount) {
        assertThat(count("payment_intents")).isEqualTo(1);
        assertThat(count("journal_entries")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from journal_entries where tenant_id = ?", String.class, tenant)).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("select count(*) from postings p join journal_entries j on j.id = p.journal_entry_id where j.tenant_id = ?", Long.class, tenant)).isEqualTo(2);
        assertThat(balance(source)).isEqualTo(10_000 - amount);
        assertThat(balance(destination)).isEqualTo(amount);
        assertThat(outboxCount()).isEqualTo(1);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where tenant_id = ?", Long.class, tenant);
    }

    private long outboxCount() {
        return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id in (select id from payment_intents where tenant_id = ?)", Long.class, tenant);
    }

    private long balance(UUID id) {
        return jdbc.queryForObject("select balance_minor from accounts where id = ?", Long.class, id);
    }

    private PaymentRequest request(String key, String reference, UUID from, UUID to, long amount) {
        return new PaymentRequest(key, """
            {"merchantReference":"%s","sourceAccountId":"%s","destinationAccountId":"%s",
             "amountMinor":%d,"currency":"GBP","description":"Integration transfer"}
            """.formatted(reference, from, to, amount));
    }

    private HttpResult pay(PaymentRequest request) throws Exception {
        var result = mvc.perform(post("/api/v1/payments")
            .with(jwt().jwt(token -> token.claim("tenant_id", tenant))
                .authorities(new SimpleGrantedAuthority("SCOPE_ledger:write")))
            .header("Idempotency-Key", request.key())
            .contentType("application/json").content(request.body())).andReturn().getResponse();
        return new HttpResult(result.getStatus(), result.getContentAsString());
    }

    private List<HttpResult> runTogether(PaymentRequest first, PaymentRequest second) throws Exception {
        // Hold the accounts until both requests have passed their initial key lookup.
        // Waiting for PostgreSQL lock waiters makes the race deterministic.
        try (Connection blocker = jdbc.getDataSource().getConnection();
             var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try {
                try (var statement = blocker.prepareStatement("select id from accounts where tenant_id = ? for update")) {
                    statement.setString(1, tenant);
                    try (var rows = statement.executeQuery()) { while (rows.next()) { /* lock all test accounts */ } }
                }
                CountDownLatch started = new CountDownLatch(2);
                var a = executor.submit(() -> { started.countDown(); return pay(first); });
                var b = executor.submit(() -> { started.countDown(); return pay(second); });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                long waiters;
                do {
                    waiters = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Long.class);
                    if (waiters >= 2) break;
                    Thread.sleep(10);
                } while (System.nanoTime() < deadline);
                assertThat(waiters).as("both HTTP requests are waiting on account locks").isGreaterThanOrEqualTo(2);
                blocker.commit();
                return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            } finally {
                blocker.rollback();
            }
        }
    }

    private record PaymentRequest(String key, String body) {}
    private record HttpResult(int status, String body) {}
}
