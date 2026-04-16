CREATE TABLE IF NOT EXISTS accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT NOT NULL,
    account_type  TEXT NOT NULL DEFAULT 'checking',
    balance       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debit_account   UUID NOT NULL REFERENCES accounts(id),
    credit_account  UUID NOT NULL REFERENCES accounts(id),
    amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    description     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_transactions_debit ON transactions(debit_account);
CREATE INDEX idx_transactions_credit ON transactions(credit_account);

-- Balancing account: used as counterparty for deposits/withdrawals
INSERT INTO accounts (id, name, account_type, balance)
VALUES ('00000000-0000-0000-0000-000000000000', 'Balancing Account', 'system', 0.00)
ON CONFLICT DO NOTHING;
