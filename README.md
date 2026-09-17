# Billing & Business Management SaaS — Phase 1

Multi-tenant billing/invoicing SaaS for small Indian businesses.
**Phase 1 scope:** project architecture, database schema, authentication, and multi-tenancy foundation.
Customers, products, invoices, payments, expenses, reports, and PDF generation are built in subsequent phases on top of this foundation.

## Architecture

```
Angular (standalone components, Reactive Forms)
        │  JWT bearer token
        ▼
Spring Boot REST API (stateless, Spring Security)
        │  business_id resolved ONLY from verified JWT claims
        ▼
PostgreSQL (Flyway-managed schema)
```

Every tenant-scoped table carries a `business_id` foreign key. The backend never trusts a business/tenant id
from the client — `JwtAuthenticationFilter` extracts it from the verified JWT and stores it in `TenantContext`
(a request-scoped thread-local), which every service/repository call must use.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Flyway, PostgreSQL, JJWT, springdoc-openapi
- **Frontend:** Angular 18 (standalone components), TypeScript, Reactive Forms, functional interceptors/guards
- **Build:** Maven (backend), npm/Angular CLI (frontend)

## Folder structure

```
billing-saas/
├── backend/
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/java/com/saasbilling/
│       ├── entity/         Business, User, RefreshToken, BusinessSettings, AuditLog
│       ├── repository/     tenant-scoped JPA repositories
│       ├── security/       JwtService, JwtAuthenticationFilter, TenantContext
│       ├── service/        AuthService, AuditLogService
│       ├── controller/     AuthController, MeController
│       ├── exception/      GlobalExceptionHandler + typed exceptions
│       ├── config/         SecurityConfig, JpaAuditingConfig
│       └── src/main/resources/db/migration/  Flyway SQL migrations
│
└── frontend/
    └── src/app/
        ├── core/            auth service, guards, interceptors, models
        ├── shared/          reusable components (forbidden page, etc.)
        └── features/        auth (login/register), dashboard
```

## Database setup

You said you already have a PostgreSQL instance, so:

1. Create a database and a role:
   ```sql
   CREATE DATABASE billing_saas;
   CREATE USER billing_app WITH PASSWORD 'change_me';
   GRANT ALL PRIVILEGES ON DATABASE billing_saas TO billing_app;
   ```
2. Flyway creates and versions all tables automatically the first time the backend starts — you do not need to run any SQL by hand. Migrations live in `backend/src/main/resources/db/migration/`.

(If you'd rather run Postgres locally for dev instead, see `docker-compose.yml` at the repo root.)

## Environment variables (backend)

Copy `backend/.env.example` to `backend/.env` (or export as real env vars — do not commit `.env`):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing key for access tokens — generate with `openssl rand -base64 64`. **Required, no default.** |
| `JWT_ACCESS_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | Token lifetimes (default 15 min / 7 days) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated explicit origins (e.g. `http://localhost:4200`) — never `*` |
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` |

## Running the backend

```bash
cd backend
export $(grep -v '^#' .env | xargs)   # or configure env vars however you prefer
mvn spring-boot:run
```

- API base URL: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

On first startup, Flyway applies `V1__init_core_schema.sql` and `V2__audit_logs.sql` automatically.

## Running the frontend

```bash
cd frontend
npm install
npm start
```

- App: `http://localhost:4200`
- `src/environments/environment.ts` points at `http://localhost:8080/api` for local dev.

## Running tests

```bash
cd backend
mvn test
```

`TenantIsolationTest` is the most important test in this phase — it proves a user record from Business A
cannot be fetched by scoping the query to Business B's id, which is the isolation guarantee the whole
multi-tenant model depends on. It runs against an in-memory H2 database (`application-test.yml`), since the
Flyway migrations use Postgres-only features (`pgcrypto`, `jsonb`).

## What's implemented

### Phase 1 — Foundation
- [x] Business registration (creates tenant + OWNER user + default settings, atomically)
- [x] Login with account lockout after 5 failed attempts (15 min)
- [x] JWT access tokens (15 min) + rotating opaque refresh tokens (7 days, hashed at rest, revocable)
- [x] Logout (revokes refresh token server-side)
- [x] Forgot password / reset password / change password
- [x] Multi-tenant isolation via `TenantContext`, enforced independently of anything the client sends
- [x] Role field on users (`OWNER`, `ADMIN`, `MANAGER`, `STAFF`)
- [x] Centralized structured error handling (`ApiError`), no stack traces leaked to clients
- [x] Async audit logging wired into register/login/password events (now correctly writing `jsonb`, see Fixes below)
- [x] CORS locked to explicit origins, BCrypt password hashing, no secrets in source
- [x] Angular auth flow: login/register forms, JWT interceptor, 401 handling, route guards
- [x] Tenant isolation test

### Fixes applied after initial Phase 1 testing
- `AuditLog.beforeValue` / `afterValue` now use `@JdbcTypeCode(SqlTypes.JSON)` so Hibernate binds them as `jsonb`
  instead of `varchar` — fixes `column "after_value" is of type jsonb but expression is of type character varying`.
- `GET /api/me` now goes through a new `UserService.getCurrentUserWithBusiness(...)` (a `@Transactional(readOnly = true)`
  service method using a `JOIN FETCH` repository query) instead of reading `user.getBusiness()` after the Hibernate
  session had already closed — fixes `LazyInitializationException`. **This fetch-join-in-a-service pattern is now the
  standard for every module** whenever a lazy association needs to be read by a caller.

### Phase 2 — Customers, Products, Services
- [x] Full tenant-scoped CRUD for Customers, Products, Services, with a shared `Category` table
- [x] Search (name/phone/email/GSTIN for customers, name/SKU for products, name for services), status filter,
      pagination and sorting on every list endpoint (page size capped at 100 server-side)
- [x] Products: SKU uniqueness enforced **per business**, not globally (partial unique index in the DB, plus a
      service-layer duplicate check with a clear `DuplicateResourceException`)
- [x] Products: low-stock endpoint (`GET /api/products/low-stock`) — items at or below `minimumStockLevel`
- [x] Soft-delete only: `deactivate`/`reactivate` endpoints, never a hard `DELETE`, since customers/products/services
      may be referenced by historical invoices later (spec section 42)
- [x] Role-based authorization enforced server-side with `@PreAuthorize` (method security enabled in `SecurityConfig`):
      - Customers: all roles can view/create/edit; only OWNER/ADMIN/MANAGER can deactivate/reactivate
      - Products & Services: all roles can view; only OWNER/ADMIN/MANAGER can create/edit/deactivate/reactivate
        (STAFF can look up prices to build an invoice but can't change them)
- [x] Every write is audit-logged (`CUSTOMER_CREATED`, `PRODUCT_UPDATED`, `SERVICE_DEACTIVATED`, etc.)
- [x] Tenant isolation test for Customers + a cross-tenant SKU-uniqueness test for Products
- [x] Angular: full Customers module (search/list/pagination/create/edit/deactivate), full Products module
      (same, plus low-stock indicator in the list), full Services module (same)
- [x] Angular: app shell with sidebar navigation (`AppShellComponent`), all authenticated routes nested under it
- [x] Angular: `roleGuard(...)` gates Product/Service create-edit routes client-side for UX — the backend
      `@PreAuthorize` checks are the actual security boundary, this just avoids showing a form the user can't submit

### Phase 3 — Invoices
- [x] Server-side total calculation only — the client sends WHAT to bill (product/service id, quantity,
      optional per-line discount); `InvoiceService` looks up unit price and tax rate from the tenant's own
      Product/Service records and computes every total itself (spec section 11: never trust totals from
      the frontend)
- [x] Line items snapshot the item name/price/tax rate at billing time, so historical invoices stay accurate
      even if a product is later renamed, repriced, or deactivated
- [x] Configurable, concurrency-safe invoice numbering: `{PREFIX}-{YEAR}-{SEQ:00000}` format is parsed from
      `business_settings.invoice_number_format`; the sequence counter is incremented under a pessimistic
      row lock (`SELECT ... FOR UPDATE` via `BusinessSettingsRepository#findByBusinessIdForUpdate`) inside
      the same transaction that issues the invoice, so two concurrent requests can never collide on a number
- [x] The invoice number is assigned at **issue** time, not at creation — a `DRAFT` that's abandoned never
      burns a sequence number (`invoice_number` is nullable with a partial unique index)
- [x] All six statuses (`DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `CANCELLED`); editing is only
      allowed while `DRAFT`; issued invoices can only be cancelled (never silently rewritten, spec section 14)
- [x] Cancellation is blocked if any payment has already been recorded against the invoice, and is never a
      hard delete — the cancelled invoice and its number stay in history (spec section 42)
- [x] GST tax split: CGST+SGST when the business and customer share a state, IGST otherwise (or when either
      party's state isn't on file). This is a simplification for MVP — flagged in the PDF service comments
      as something an accountant should verify for businesses doing both intra- and inter-state sales
- [x] Professional PDF invoice generation (OpenPDF) matching spec section 20's layout: business header with
      GSTIN, invoice/customer details, item table, subtotal/discount/CGST/SGST/IGST/grand-total breakdown,
      a payment-status banner (paid/partially paid/payment due/cancelled), and notes/terms pulled from the
      invoice or falling back to `business_settings` defaults
- [x] Role-based authorization: all roles (including STAFF) can create/view/edit-while-draft invoices —
      billing customers is STAFF's core job; only OWNER/ADMIN/MANAGER can issue or cancel
- [x] Angular: invoice list (search/status filter/pagination), a line-item builder (product/service picker
      with a live client-side estimate — the server recalculates authoritatively on save), and a detail view
      showing the server's authoritative totals with Issue/Cancel/PDF-download actions
- [x] Tests: server-side calculation correctness (tax split, totals), concurrency-safe sequential numbering
      across two invoices, and tenant isolation for invoices

### Phase 4 — Payments and real dashboard metrics
- [x] Record full/partial payments against an invoice (CASH, UPI, BANK_TRANSFER, CARD, CHEQUE, OTHER);
      payment amount is validated against the invoice's balance due server-side, never trusted as-is
- [x] `invoices.amount_paid` is always recomputed as the authoritative sum of that invoice's non-voided
      payments (`PaymentRepository#sumActiveAmountByInvoiceId`) rather than incrementally adjusted, so it
      can never drift out of sync
- [x] Invoice status rolls forward automatically: `ISSUED` → `PARTIALLY_PAID` → `PAID` as payments come in
- [x] Payments are never hard-deleted (spec section 42) — a mistaken entry is voided instead
      (`voided`/`voided_at`/`voided_reason`), which reverts the invoice status/balance accordingly;
      voiding is restricted to OWNER/ADMIN/MANAGER
- [x] Cannot record a payment against a `DRAFT` (not yet issued), `CANCELLED`, or already-`PAID` invoice —
      clear, specific error messages for each case
- [x] Role policy: all roles (including STAFF) can record payments, per spec section 5
- [x] Real dashboard metrics replacing the Phase 1 placeholder: today's/month's/total sales, pending
      payments, paid amount, bills issued, active customers, active products — every figure is a live
      query against the tenant's own data, never hardcoded (spec section 7)
- [x] Angular: payment recording form and payment history (with void action) embedded directly in the
      invoice detail page; dashboard now shows real metric cards instead of a raw JSON dump
- [x] Tests: partial → full payment status transitions, payment-exceeds-balance rejection, and
      void-reverts-status-and-balance

### Phase 5 — Expenses and Reports
- [x] Expense tracking (spec section 17): description, amount, date, payment method, category, reference,
      notes; every new business is seeded with the standard categories (Rent, Electricity, Internet, Salary,
      Purchase, Transport, Maintenance, Other) via `categories.type = 'EXPENSE'` — reusing the same table
      Products/Services categories already live in, not a separate one
- [x] Categories now have a working frontend picker for the first time — the expense form's category
      dropdown fixes the gap flagged in the Phase 2/3 known-limitations list
- [x] Reports (spec section 18), all live-queried against the tenant's own data:
      - **Sales** — daily breakdown + totals for a date range, with CSV export (`/api/reports/sales/export.csv`)
      - **Invoices** — counts by status
      - **Payments** — totals by payment method
      - **Customers** — top customers by revenue in range, plus total outstanding across all open invoices
      - **Products** — top-selling products by revenue (aggregated from invoice line-item snapshots, so it
        reflects what was actually billed even if a product was later renamed)
      - **Expenses** — total and breakdown by category
- [x] Role policy: Expenses and Reports are both restricted to OWNER/ADMIN/MANAGER for the whole
      controller — unlike Customers/Products/Invoices, this is financial/overhead data STAFF don't need
      day-to-day access to
- [x] Angular: full Expenses CRUD (list/search/create/edit/delete) and a single Reports page with a
      date-range picker driving all six report sections plus a CSV download link for sales

## Known limitations (by design, for this phase)

- No email sending yet — password reset tokens are logged server-side only (`AuthService.forgotPassword`);
  wire up an email provider before relying on this in production.
- Account activation via email link is stubbed out — users are activated immediately on registration for
  MVP convenience. Swap `UserStatus.ACTIVE` → `PENDING_ACTIVATION` + email verification when ready.
- Fine-grained permissions (e.g. "MANAGER can edit products but not delete them") are not yet enforced;
  only the four roles exist. Add `@PreAuthorize` checks per endpoint as each module is built.
- Tokens are stored in `localStorage` on the frontend for simplicity. Consider httpOnly cookies for a
  production hardening pass (noted in `auth.service.ts`).
- No Docker Compose for the app itself yet (only for optional local Postgres) — added when deployment
  documentation (spec section 40) is tackled.
- Invoice-level `additionalDiscountAmount` is applied **after** tax (a flat rebate off the grand total),
  not proportionally distributed across line items before tax. This is a deliberate MVP simplification —
  proportional pre-tax discount allocation across mixed-tax-rate line items adds real complexity for
  limited benefit at this stage; documented in `InvoiceService` and the DB migration comments so a future
  phase can revisit it if a customer needs GST-compliant discount treatment
- No scheduled job yet to automatically flip `ISSUED`/`PARTIALLY_PAID` invoices to `OVERDUE` after their
  due date passes — `InvoiceRepository#findOverdueCandidates` is ready for a future `@Scheduled` task to use
- No dedicated `/payments` list page yet — payment history is viewable per-invoice on the invoice detail
  page; a global searchable/filterable payments list (`PaymentController#search` already supports it) is a
  small addition for a future phase
- The dashboard summary endpoint (`GET /api/dashboard`) is currently open to all roles, including STAFF —
  worth revisiting with a `@PreAuthorize` restriction if a business doesn't want staff seeing aggregate
  sales figures
- Report export only covers CSV, and only for the Sales report — PDF and Excel export (spec section 19),
  and CSV for the other five reports, are straightforward additions following the same pattern in
  `ReportController` but weren't all built out to keep this phase's scope contained
- Backend was not compiled in this environment (no Maven/Maven-Central network access here) — run
  `mvn clean install` locally to verify before deploying.

## Next recommended phase

**Phase 6: Users + Settings** — inviting/managing team members within a business (spec section 5's role
management), business profile and invoice settings screens (spec section 21 — logo upload, invoice prefix/
numbering/tax-mode configuration, currently only editable by direct DB/API access), and user account
settings (name/email/phone/password). A scheduled job to sweep overdue invoices
(`InvoiceRepository#findOverdueCandidates`, still unused) fits naturally alongside this phase too.
