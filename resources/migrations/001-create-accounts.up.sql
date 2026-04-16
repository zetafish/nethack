CREATE TABLE accounts (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name          TEXT NOT NULL,
  account_type  TEXT NOT NULL CHECK (account_type IN ('checking', 'savings', 'balancing')),
  balance       NUMERIC(15,2) NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Balancing account used as counterparty for deposits/withdrawals
INSERT INTO accounts (name, account_type) VALUES ('Balancing Account', 'balancing');
