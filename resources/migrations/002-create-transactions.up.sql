CREATE TABLE transactions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  debit_account   UUID NOT NULL REFERENCES accounts(id),
  credit_account  UUID NOT NULL REFERENCES accounts(id),
  amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
  description     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_debit ON transactions(debit_account);
CREATE INDEX idx_transactions_credit ON transactions(credit_account);
