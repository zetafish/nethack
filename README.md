# Banking App

Clojure banking application with double-entry bookkeeping.

## Prerequisites

- Java 21+
- Clojure CLI
- Docker & Docker Compose

## Setup

```bash
# Start databases
docker compose up -d

# Run migrations
clj -M:migrate

# Start application
clj -M:dev
# In REPL: (go)
```

## Testing

```bash
# Ensure test DB is running
docker compose up -d postgres-test

# Run tests
clj -X:test
```
