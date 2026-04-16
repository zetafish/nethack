CREATE TABLE IF NOT EXISTS accounts (
  id         UUID PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  type       VARCHAR(50)  NOT NULL CHECK (type IN ('checking', 'savings', 'balancing')),
  balance    DECIMAL(15,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions (
  id              UUID PRIMARY KEY,
  type            VARCHAR(50)   NOT NULL CHECK (type IN ('deposit', 'withdrawal', 'transfer')),
  amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
  from_account_id UUID REFERENCES accounts(id),
  to_account_id   UUID REFERENCES accounts(id),
  description     TEXT,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_from ON transactions(from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to   ON transactions(to_account_id);
