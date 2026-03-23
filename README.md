# Service Manager

This repository is a small Spring Boot saga demo built around a coordinator and a few local services. The current flow shows how a single checkout request becomes a multi-step distributed transaction, and how the coordinator compensates successful steps when a later step fails.

## What is in the repo

- `coordinator`: starts global transactions, stores step state, and triggers reverse-order compensation
- `demo-orch`: simple orchestrator that starts a transaction and calls the services in order
- `order-service`: creates and cancels orders
- `payment-service`: charges and refunds payments
- `inventory-service`: reserves and releases inventory, and can simulate a failure
- `client-sdk`: placeholder module for shared client code
- `common-lib`: placeholder module for shared common code

## Architecture

Success path:

```text
client -> demo-orch -> coordinator(begin)
                    -> order-service
                    -> payment-service
                    -> inventory-service
```

Failure path:

```text
client -> demo-orch -> order-service      -> SUCCESS
                    -> payment-service    -> SUCCESS
                    -> inventory-service  -> FAIL

coordinator -> payment-service /payments/compensate/refund
            -> order-service   /orders/compensate/cancel
```

Compensation runs in reverse step order.

## Tech stack

- Java 21
- Spring Boot 4.0.3
- Spring Web
- Spring Data JPA
- PostgreSQL for local runtime
- H2 for tests
- Maven Wrapper in each runnable module

## Services and ports

| Module | Port | Purpose | Database |
| --- | --- | --- | --- |
| `coordinator` | `8081` | Transaction state and compensation | `coordinator_db` on `5432` |
| `demo-orch` | `8082` | Checkout entrypoint | none |
| `order-service` | `8083` | Order step | `order_db` on `5433` |
| `payment-service` | `8084` | Payment step | `payment_db` on `5434` |
| `inventory-service` | `8085` | Inventory step | `inventory_db` on `5435` |

## How the saga works

1. `demo-orch` calls `POST /api/tx/begin` on the coordinator.
2. The coordinator returns an `XID`.
3. `demo-orch` sends that `X-XID` header to each service call.
4. Each service registers its own step with the coordinator before doing work.
5. On success, the service marks the step as `SUCCESS`.
6. If a later step fails, the coordinator marks the transaction as `COMPENSATING`.
7. The coordinator calls each stored compensation endpoint in reverse order.
8. Successful rollback updates step state to `COMPENSATED`.

## Running locally

### 1. Start PostgreSQL containers

From the repo root:

```powershell
docker compose up -d
```

This starts four Postgres containers for the coordinator, order, payment, and inventory services.

### 2. Start the applications

Open a terminal per service and run:

```powershell
cd coordinator
.\mvnw.cmd spring-boot:run
```

```powershell
cd demo-orch
.\mvnw.cmd spring-boot:run
```

```powershell
cd order-service
.\mvnw.cmd spring-boot:run
```

```powershell
cd payment-service
.\mvnw.cmd spring-boot:run
```

```powershell
cd inventory-service
.\mvnw.cmd spring-boot:run
```

### 3. Check health

- `GET http://localhost:8081/health`
- `GET http://localhost:8082/health`
- `GET http://localhost:8083/health`
- `GET http://localhost:8084/health`
- `GET http://localhost:8085/health`

## Try the success flow

Request:

```http
POST http://localhost:8082/checkout
Content-Type: application/json

{
  "orderRef": "ORD-100"
}
```

Expected outcome:

- order is created
- payment is charged
- inventory is reserved
- coordinator stores one global transaction with three successful steps

You can inspect the coordinator state with:

```http
GET http://localhost:8081/api/tx/{xid}
```

## Try the compensation flow

The inventory service simulates a failure when `orderRef` contains `FAIL`.

Request:

```http
POST http://localhost:8082/checkout
Content-Type: application/json

{
  "orderRef": "ORD-FAIL-001"
}
```

Expected outcome:

- order step succeeds first
- payment step succeeds second
- inventory step fails
- coordinator compensates in reverse order
- payment becomes `REFUNDED`
- order becomes `CANCELLED`
- transaction ends in a compensated state

## Main endpoints

Coordinator:

- `POST /api/tx/begin`
- `POST /api/tx/{xid}/steps`
- `POST /api/tx/{xid}/steps/{stepId}/success`
- `POST /api/tx/{xid}/steps/{stepId}/failure`
- `GET /api/tx/{xid}`

Demo orchestrator:

- `POST /checkout`

Order service:

- `POST /orders`
- `POST /orders/compensate/cancel`

Payment service:

- `POST /payments/charge`
- `POST /payments/compensate/refund`

Inventory service:

- `POST /inventory/reserve`
- `POST /inventory/compensate/release`

## Running tests

Each runnable module has its own Maven wrapper and test suite. For example:

```powershell
cd coordinator
.\mvnw.cmd test
```

Test databases use in-memory H2 with PostgreSQL mode enabled.

## Notes

- Service URLs are configured with simple localhost base URLs in each `application.yml`.
- The coordinator currently resolves compensation targets with a small hardcoded service-to-port map.
- `client-sdk` and `common-lib` are present as placeholders and are not wired into the running flow yet.
