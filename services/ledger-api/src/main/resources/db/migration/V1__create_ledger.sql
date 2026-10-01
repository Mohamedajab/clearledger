CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    external_ref VARCHAR(96) NOT NULL,
    name VARCHAR(140) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    account_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    balance_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_tenant_external UNIQUE (tenant_id, external_ref),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    merchant_reference VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_payment_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_payment_reference UNIQUE (tenant_id, merchant_reference),
    CONSTRAINT chk_payment_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_payment_accounts_differ CHECK (source_account_id <> destination_account_id)
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    payment_intent_id UUID REFERENCES payment_intents(id),
    reference VARCHAR(96) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    description VARCHAR(280) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_journal_reference UNIQUE (tenant_id, reference)
);

CREATE TABLE postings (
    id BIGSERIAL PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    direction VARCHAR(8) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_posting_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_posting_amount_positive CHECK (amount_minor > 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_accounts_tenant_status ON accounts(tenant_id, status);
CREATE INDEX idx_payments_tenant_created ON payment_intents(tenant_id, created_at DESC);
CREATE INDEX idx_journal_tenant_occurred ON journal_entries(tenant_id, occurred_at DESC);
CREATE INDEX idx_postings_entry ON postings(journal_entry_id);
CREATE INDEX idx_postings_account_created ON postings(account_id, created_at DESC);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION assert_balanced_entry() RETURNS TRIGGER AS $$
DECLARE
    debit_total BIGINT;
    credit_total BIGINT;
BEGIN
    IF NEW.status = 'POSTED' AND OLD.status <> 'POSTED' THEN
        SELECT COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
               COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0)
        INTO debit_total, credit_total
        FROM postings WHERE journal_entry_id = NEW.id;
        IF debit_total = 0 OR debit_total <> credit_total THEN
            RAISE EXCEPTION 'Journal entry % is not balanced: debits %, credits %', NEW.id, debit_total, credit_total;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_assert_balanced_entry
BEFORE UPDATE OF status ON journal_entries
FOR EACH ROW EXECUTE FUNCTION assert_balanced_entry();
