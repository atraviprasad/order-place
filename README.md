# order-place
## Overview

Order Place is a B2B enterprise application that lets corporate and institutional customers (offices, universities, etc.) design their workspace layouts and place bulk furniture orders. Orders are routed to partner dealers, who fulfil them through a structured order-management workflow.

**Project Structure**

~~~
order-place/
├── pom.xml                      # parent POM (versions, plugins, module list)
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml           # infrastructure (from the earlier skeleton)
├── docker-compose.apps.yml      # application services (added as you build)
├── infra/
│   ├── postgres/init/           # database creation SQL
│   └── keycloak/realm/          # realm export JSON
├── libs/                        # small shared libraries (see section 5)
│   ├── common-events/           # event envelope and event records
│   ├── common-web/              # error handling, correlation ID, pagination
│   └── common-security/         # JWT claims helpers, tenant context
├── services/
│   ├── api-gateway/             # Spring Cloud Gateway
│   ├── user-service/
│   ├── catalog-service/
│   ├── dealer-service/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
├── frontend/                    # React app (later)
├── docs/                        # requirements, architecture, API, diagrams
└── .github/workflows/           # CI pipelines

## Order workflow

```
Customer places order -> Dealer reviews -> Approve / Reject / Edit quantities
        -> Awaiting payment -> Customer pays (Razorpay) -> signed webhook confirms
        -> Paid -> Ready for delivery
```

- Order statuses are changed only by the services that own them. The browser's "payment done" callback is never trusted: **only the signed Razorpay webhook** marks a payment as successful.
- Every status change is recorded in the order history and triggers an email notification.

## Features

| Role | What they can do |
|---|---|
| **Customer** | Browse and search the catalog, filter by category, build a cart, choose a dealer, place and track orders, edit a draft, pay online, see e-mail notifications' deep links |
| **Dealer** | See incoming orders, approve, reject (with a reason) or edit quantities, follow orders through to payment |
| **Admin** | Dashboard, all orders and payments, e-mail log, manage customers, dealers and users, manage products (price with history, availability, images) and the category tree |

## Tech stack

**Back end**
- Java 25, Spring Boot 4, Maven (multi-module monorepo), Lombok, records
- Spring Cloud Gateway (single entry point), Spring Security OAuth2 resource server (JWT)
- Spring Data JPA / Hibernate, Flyway migrations
- Keycloak (authentication, roles `ADMIN`, `CUSTOMER`, `DEALER`)
- Apache Kafka (events between services), transactional outbox pattern, idempotent consumers
- PostgreSQL (one database per service), MongoDB (notifications), Redis
- MinIO (product images, S3-compatible), Mailpit (local e-mail testing)
- Razorpay (payments, test mode)
- JUnit 5, Mockito, AssertJ

**Front end**
- React 18, TypeScript, Vite
- Material UI 6
- TanStack React Query 5, React Router 6
- keycloak-js (login with PKCE)
- Jest + React Testing Library

**Infrastructure**
- Docker and Docker Compose for Postgres, MongoDB, Redis, Kafka, Keycloak, MinIO and Mailpit

## Architecture

```
                       +-------------------+
 React (5173) -------> |  API Gateway 8080 | ---- validates JWT from Keycloak (8180)
                       +---------+---------+
        +---------+----------+---+------+-----------+-------------+
        v         v          v          v           v             v
   User 8081  Catalog 8082  Dealer 8083  Order 8084  Payment 8085  Notification 8086
   Postgres   Postgres      Postgres     Postgres    Postgres      MongoDB
              + MinIO                       ^  |         |             ^
                                            |  +-Kafka---+-------------+
                                            +---- order-events / payment-events
```

| Service | Responsibility |
|---|---|
| **api-gateway** | Routing, token check, single public entry point |
| **user-service** | Customers, users, Keycloak user management |
| **catalog-service** | Products, categories, price history, images (MinIO) |
| **dealer-service** | Dealers and their data |
| **order-service** | Orders, order lines, status workflow, history |
| **payment-service** | Razorpay orders, webhooks, payment records |
| **notification-service** | Sends e-mails from events, keeps a log in MongoDB |

Key design points:
- **Transactional outbox + Kafka** so a database change and its event are never out of step.
- **Idempotent consumers** (a processed-event table in Order, a unique index in Notification) so a repeated event does nothing twice.
- **Optimistic locking** with a `version` field. A concurrent edit returns `409 VERSION_CONFLICT`.
- **Tenant isolation**: a user asking for another organisation's data gets `404`.
- **Payment safety**: the server takes the amount from the Order Service, the browser callback only gives `PENDING_CONFIRMATION`, webhooks are verified with HMAC-SHA256 over the raw body and de-duplicated by event id, and each payment start carries an `Idempotency-Key`.

## Getting started

### Prerequisites
- JDK 25, Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop

### 1. Start the infrastructure
```bash
cd infra            # the folder with docker-compose.yml
cp .env.example .env    # then set the values; never commit .env
docker compose up -d
```
Postgres, MongoDB, Redis, Kafka, Keycloak (8180), MinIO (9000 / console 9001) and Mailpit (8025).

### 2. Start the services
Start each service from your IDE or with Maven, for example:
```bash
mvn -pl services/catalog-service spring-boot:run
```
Ports: gateway 8080, user 8081, catalog 8082, dealer 8083, order 8084, payment 8085, notification 8086. Run the gateway last.

### 3. Start the front end
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```
The dev server proxies `/api` to the gateway, so no CORS setup is needed.

### 4. Sign in (DEV users only)
| Role | User | Password |
|---|---|---|
| Admin | `admin1` | `Admin@123` |
| Dealer | `dealer1` | `Dealer@123` |
| Customer | `customer1` | `Customer@123` |

These accounts and the seed data under `db/dev` are for **local development only** and must be removed before any real deployment.

## Configuration

Secrets are read from environment variables, never from source control.

| Variable | Purpose |
|---|---|
| `RAZORPAY_KEY_ID` | Razorpay **test** key id (must start with `rzp_test_`) |
| `RAZORPAY_KEY_SECRET` | Razorpay test key secret |
| `RAZORPAY_WEBHOOK_SECRET` | A secret you choose; the same value goes into the Razorpay webhook settings |
| `PAYMENTS_GATEWAY` | `razorpay` (default) or `fake` (local testing without a Razorpay account) |
| `MINIO_PUBLIC_URL` | Browser-reachable MinIO address for product images (default `http://localhost:9000`) |

## Testing payments

**Without Razorpay (fake gateway).** Set `payments.gateway=fake`. The order page shows a gateway order id. Confirm the payment by sending a signed webhook with `fake-pay.sh <gatewayOrderId> <amount>`.

**With Razorpay test mode.**
1. Create test keys in the Razorpay dashboard (Test Mode) and set the variables above.
2. Expose the Payment Service: `ngrok http 8085`.
3. Add a webhook in Razorpay: `https://<your-tunnel>/api/v1/webhooks/razorpay`, with your webhook secret and the events `payment.captured`, `payment.failed`, `order.paid`.
4. Pay with a domestic test card, for example Mastercard `5267 3181 8797 5449`, or UPI `success@razorpay`. Use small amounts: UPI is limited to INR 1,00,000, and test accounts may have a lower per-payment limit.

## Running the tests

```bash
mvn test                  # back end unit tests (all services)

cd frontend
npm test                  # Jest unit and component tests
npm run build             # type-check and production build
```

## Project status

Done: all back-end services, customer / dealer / admin screens, online payment with webhook confirmation, notifications.

Planned:
- Payment **refunds** and expiry of unpaid (INITIATED) payments
