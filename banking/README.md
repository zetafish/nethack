# Banking App

A Clojure banking application with double-entry bookkeeping.

## Prerequisites

- Java 21+
- Clojure CLI
- Docker & Docker Compose

## Setup

```bash
# Start postgres
docker compose up -d

# Run the app
clj -M:run

# Run tests
clj -X:test
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/accounts | List accounts |
| POST | /api/accounts | Create account |
| GET | /api/accounts/:id | Get account |
| GET | /api/accounts/:id/transactions | Account transactions |
| POST | /api/transactions/deposit | Deposit funds |
| POST | /api/transactions/withdraw | Withdraw funds |
| POST | /api/transactions/transfer | Transfer between accounts |

Swagger UI available at `/swagger`.
