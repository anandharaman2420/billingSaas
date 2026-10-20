# Billing & Business Management SaaS

Multi-tenant billing/invoicing SaaS for small Indian businesses.
**Scope:** Architecture, database schema, multi-tenancy, customers, catalog, invoicing, payments, expenses, reports, subscriptions, file storage, and Point of Sale (POS) fast billing.

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
For businesses with multiple locations, `X-Branch-Id` selects an active branch; the backend verifies that it
belongs to the authenticated business and otherwise uses the migrated default branch.

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
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Enable Razorpay checkout and signed webhooks |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Enable Stripe Checkout and signed webhooks |
| `PLAN_PRICE_FREE`, `PLAN_PRICE_BASIC`, `PLAN_PRICE_PROFESSIONAL`, `PLAN_PRICE_BUSINESS` | Monthly plan prices in INR |
| `PAYMENT_REMINDER_THROTTLE_DAYS` | Minimum days between invoice reminders (default 3) |

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

## Production deployment (Docker Compose)

The full stack — PostgreSQL, the Spring Boot backend, and the Angular frontend behind nginx — can be run as
three containers with Docker Compose:

```bash
cp .env.example .env
# edit .env: set DB_PASSWORD, JWT_SECRET (openssl rand -base64 64), and CORS_ALLOWED_ORIGINS
# to your real frontend domain
docker compose up --build
```

- Frontend: `http://localhost` (or `${FRONTEND_PORT}` if you changed it)
- Backend API: `http://localhost:8080/api` (also reachable through the frontend's nginx at `/api/`, which
  is what the built Angular app actually calls — see `frontend/nginx.conf`)
- Flyway applies all migrations automatically on the backend's first startup, same as local dev

**What each piece does:**
- `backend/Dockerfile` — multi-stage build: `maven:3.9-eclipse-temurin-21` compiles the jar, then a slim
  `eclipse-temurin:21-jre-alpine` runs it as a non-root user
- `frontend/Dockerfile` — multi-stage build: `node:20-alpine` builds the production Angular bundle, then
  `nginx:1.27-alpine` serves it and proxies `/api/*` through to the `backend` container
- `docker-compose.yml` — wires the three containers together; every secret (`DB_PASSWORD`, `JWT_SECRET`,
  `CORS_ALLOWED_ORIGINS`) is required via `${VAR:?error message}` syntax — compose refuses to start rather
  than silently falling back to an insecure default

**Prerequisites:** Docker and Docker Compose v2. No local Java/Node/Maven/npm installation needed for this
path — everything builds inside the containers.

**Backup / restore:** the Postgres data lives in the `billing_saas_pgdata` named volume. Back it up with
`docker exec billing-saas-postgres pg_dump -U <DB_USERNAME> <DB_NAME> > backup.sql` and restore with
`docker exec -i billing-saas-postgres psql -U <DB_USERNAME> <DB_NAME> < backup.sql`.

**Troubleshooting:**
- Backend won't start / exits immediately → check `docker compose logs backend`; almost always a missing or
  invalid `JWT_SECRET`/`DB_PASSWORD` in `.env`
- Frontend loads but API calls fail → confirm `CORS_ALLOWED_ORIGINS` in `.env` matches the exact origin
  you're loading the frontend from (scheme + host + port)
- Changes to backend/frontend code not showing up → `docker compose up --build` rebuilds; a plain
  `docker compose up` reuses cached images

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

### Phase 6 — Users and Settings
- [x] Team member management (spec section 5/6): OWNER/ADMIN can list, create, edit (name/phone/role), and
      disable/enable team members
- [x] Privilege-escalation guard: only an OWNER can create/edit another OWNER account or grant the OWNER
      role — an ADMIN managing users can touch STAFF/MANAGER/ADMIN accounts but never an OWNER's, closing
      off the obvious self-promotion path
- [x] Last-owner lockout guard: a business can never end up with zero active OWNERs — disabling or
      demoting the sole remaining OWNER is rejected server-side
- [x] Self-service profile: any authenticated user (any role) can update their own name/phone
      (`PUT /api/users/me`) and change their own password (reusing the `AuthService.changePassword`
      endpoint that's existed since Phase 1 but had no frontend UI until now)
- [x] Business profile settings (spec section 21): name, type, address, phone, email, website, logo URL,
      GSTIN — editable by OWNER/ADMIN. Added a `website` column (migration V7) that was missing since Phase 1
- [x] Invoice settings (spec section 21): prefix, number format, next sequence, financial year start month,
      default due days, currency, tax mode, default notes/terms, theme preference — editable by OWNER/ADMIN,
      with a guard against lowering the next-sequence counter below what's already been issued (which would
      risk a future invoice colliding with an existing invoice number)
- [x] Angular: Team list/create/edit pages, a Settings page combining both business-profile and
      invoice-settings forms, and a My Profile page (name/phone + change password) linked from the top bar
- [x] Tests: last-owner-cannot-be-disabled, admin-cannot-create-or-edit-an-owner, owner-can-create-an-admin

### Phase 7 — Global search, SaaS plan limits, deployment docs
- [x] Global search (spec section 22): a single top-bar search box fans out to the existing tenant-scoped
      search queries in Customers/Products/Services/Invoices and shows the top few hits from each in a
      dropdown — reuses each module's existing paginated query rather than standing up a separate
      full-text index
- [x] SaaS plan limits foundation (spec section 37): a `PlanLimits` record per plan tier
      (FREE/BASIC/PROFESSIONAL/BUSINESS, `-1` = unlimited) checked at the point of creating a user
      (`UserManagementService`), creating a customer (`CustomerService`), and issuing an invoice
      (`InvoiceService#issue`, checked against the current calendar month's count) — each rejected with a
      clear "upgrade your plan" message rather than a generic error
- [x] Full deployment documentation + Docker Compose (spec section 40): `backend/Dockerfile` (multi-stage
      Maven → slim JRE, runs as non-root), `frontend/Dockerfile` (multi-stage Angular build → nginx,
      proxying `/api/*` to the backend container), and a root `docker-compose.yml` wiring Postgres + backend
      + frontend together, with every secret required via `${VAR:?error}` syntax so compose refuses to start
      on a missing `JWT_SECRET`/`DB_PASSWORD` rather than falling back to an insecure default
- [x] Backup/restore instructions and a troubleshooting section added to the README's deployment guide

### Phase 8 — Email, subscription foundation, scheduled jobs
- [x] Real email delivery (`EmailService`, `spring-boot-starter-mail`): password reset, welcome emails
      (business registration and new team members), and "email this invoice to the customer" — all
      gracefully no-op with a log line instead of throwing when `SMTP_HOST` isn't configured, so the app
      keeps working in dev without real SMTP credentials
- [x] `POST /api/invoices/{id}/email` (spec section 38's "Email invoices") — generates the PDF and emails
      it to the customer's address on file; rejects DRAFT invoices and customers with no email set
- [x] Subscription foundation (spec section 38): a `subscriptions` table (migration `V8`) separate from
      `businesses.plan` (which stays as the fast-read copy `PlanLimits` and the rest of the app check).
      `SubscriptionService` supports manual changes and provider-backed checkout; paid plans only activate
      after a signed provider webhook is verified
- [x] `OverdueInvoiceSweepJob` — the scheduled job `InvoiceRepository#findOverdueCandidates` has been ready
      for since Phase 3 finally exists: runs daily, flips `ISSUED`/`PARTIALLY_PAID` invoices past due to
      `OVERDUE` across every active business, operating directly on repositories rather than through the
      tenant-scoped service layer (which assumes one request's `TenantContext` — a scheduled job spans
      every tenant)
- [x] `PaymentReminderJob` (spec section 38's "Payment reminders") — daily job emailing customers a
      due-soon nudge 2 days before an invoice's due date, with configurable throttling for repeat reminders
- [x] Angular: "Email to customer" button on the invoice detail page; a Billing plan card on Settings
      (OWNER-only plan management)
- [x] Docker Compose and `.env.example` updated with the new SMTP/mail environment variables

### Phase 9 — File uploads, HSN/SAC codes, Razorpay payment gateway
- [x] Real file uploads (spec section 38): `FileStorageService` abstraction with a working
      `LocalDiskFileStorageService` implementation (tenant-scoped directories, server-generated filenames,
      content-type allowlist, 10 MB cap, path-traversal guard). `/api/files/**` streams files back, gated by
      the caller's own `TenantContext` — the business id in the URL is never trusted for authorization, only
      used to locate the file. Business logo upload (`POST /api/business/profile/logo`) and expense
      attachment upload (`POST /api/expenses/{id}/attachment`) both wired end-to-end with frontend file
      pickers. Swapping to S3-compatible storage later only means a new `FileStorageService` implementation
      — no caller changes needed
- [x] Advanced GST — HSN/SAC codes (spec section 38): `hsn_code` on Products, `sac_code` on Services
      (migration `V9`), snapshotted onto each invoice line item at billing time (same rationale as
      item name/price/tax rate already being snapshotted), shown in the invoice detail page and the PDF's
      item table
- [x] Razorpay payment gateway scaffold (spec section 38): `RazorpayClient` talks to Razorpay's plain REST
      API directly via Spring's `RestClient` (no SDK dependency needed — the API is just HTTPS + Basic
      auth). `POST /api/subscription/checkout` creates a Razorpay order; `POST /api/webhooks/razorpay`
      receives the payment confirmation, with HMAC-SHA256 signature verification (constant-time comparison)
      guarding the public endpoint before any of its content is trusted. Webhook delivery is idempotent on
      the payment id, since Razorpay may redeliver the same event
- [x] Once Razorpay is configured, `SubscriptionService.changePlan` refuses to let an OWNER self-grant a
      paid plan for free — paid plans must go through checkout; the MANUAL plan-switch endpoint stays
      available for FREE and for testing when no gateway is configured
- [x] Tests: webhook signature verification (correct secret, wrong secret, tampered body, missing
      signature, unconfigured secret), provider plan-change idempotency, checkout-refuses-when-unconfigured

### Phase 10 — POS (Point of Sale) Fast Counter Billing & Stability Hardening
- [x] Dedicated Point of Sale API (`PosService`, `PosController` on `/api/pos`):
      - `GET /api/pos/catalog`: High-performance catalog endpoint querying active products & services with real-time stock levels, SKU, HSN/SAC codes, price, and category filtering.
      - `POST /api/pos/checkout`: Atomic checkout accessible to all roles including `STAFF`/Cashiers. Automatically resolves/creates walk-in customers or quick counter customer records, issues invoices with sequential invoice numbers (`{PREFIX}-{YEAR}-{SEQ}`), records payment, marks status as `PAID`, and automatically decrements product stock (`stockQuantity`).
      - `GET /api/pos/receipt/{invoiceId}`: Fast receipt endpoint for instant thermal slip reprint.
- [x] Instant Thermal Receipt Generator (80mm width):
      - Complete receipt layout including business header (name, address, phone, GSTIN), invoice metadata, cashier name, customer details, itemized table with rates and taxes, subtotal, CGST/SGST/IGST breakdown, payment mode, cash tendered, and change returned.
      - Thermal printer-ready `@media print` stylesheet that isolates the receipt paper roll for slip printers.
- [x] Angular POS Counter Terminal (`/pos`):
      - Responsive dual-pane layout: live product grid with fast-tap cards, stock indicator badges (in stock, low stock, out of stock), and category filter chips on the left.
      - High-speed Barcode / SKU scanner support: auto-focus search input with instant SKU match (pressing `Enter` on a barcode instantly adds the item to cart).
      - Rapid Cart management: quick `+` / `−` quantity buttons, line discounts, bill discount, and prominent grand total banner.
      - Quick 1-click payment triggers: **Cash (Exact)** and **Quick UPI** for instant single-click sale completion.
      - Custom Payment Modal (`F2`): Cash note denomination presets (`₹100`, `₹200`, `₹500`, `₹1000`, `₹2000`, `Exact`), cash tendered input, and live change-to-return calculator.
      - Keyboard shortcuts: `F2` to trigger payment, `Esc` to reset cart or dismiss modals.
- [x] Codebase Stability & Compilation Hardening:
      - `EmailService`: Replaced raw `ByteArrayInputStream` with Spring's `ByteArrayResource` implementing `InputStreamSource` for `MimeMessageHelper.addAttachment`.
      - `InvoicePdfService`: Added missing OpenPDF `com.lowagie.text.pdf.draw.LineSeparator` import.
      - `UserManagementService`: Reordered validation checks in `disable()` so `assertNotLastActiveOwner` runs prior to self-disable checks, ensuring last-owner safeguards remain strictly enforced.
      - Frontend Angular forms: Replaced constructor parameter `fb: FormBuilder` with Angular 18's `inject(FormBuilder)` across all form components, eliminating property initialization order errors (`TS2729`).
      - Frontend templates & imports: Fixed escaped quote issues in `settings.component.html` and normalized relative paths in `global-search.component.ts`.
      - Unit & Integration Tests: Added `PosServiceTest` testing walk-in customer checkout, inventory stock decrement, change calculation, and catalog filtering (26 of 26 tests passing).

  - **Phase 11 — Modern, User-Friendly & SEO-Optimized UI Overhaul:**
      - **Design Tokens & System (`styles.scss`):** Comprehensive CSS custom properties for theming (`--font: Inter`, `--color-primary`, `--color-surface-2`, `--shadow-md`, `--radius-lg`, etc.), full dark mode support via `[data-theme='dark']`, button system (`.btn`, `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.btn-icon`, `.btn-sm`), status pills (`.badge-success`, `.badge-warning`, `.badge-danger`, `.badge-info`), animated skeleton loaders (`.skeleton`, `shimmer`), form grids (`.form-row`), and notification toasts.
      - **SEO Optimization (`index.html` & `app.routes.ts`):** Complete SEO metadata (`description`, `keywords`, `robots`), Open Graph & Twitter Cards with `en_IN` locale, Schema.org `SoftwareApplication` JSON-LD structured data, preconnected Google Fonts, and route-level dynamic browser titles across every single page.
      - **App Shell & Navigation:** Clean responsive layout with brand emblem, grouped menu categories with emojis, fast collapsible sidebar, mobile drawer overlay with hamburger menu, dark/light mode toggle with localStorage persistence, and user avatar with initials.
      - **Auth Experience (Login & Register):** Two-column split-panel design with gradient brand showcase, value proposition checklist, structured forms with field validation hints, and accessible ARIA attributes.
      - **Dashboard Analytics:** Modern KPI grid with colored icon badges, live shimmer skeleton loaders, quick-action navigation cards, and INR currency formatting.
      - **Data Tables & Pagination:** Upgraded all list screens (`Customers`, `Products`, `Services`, `Invoices`, `Expenses`, `Team Users`, `Reports`) with unified `.data-table` layout, hover highlights, status badges, illustrated empty states, and styled pagination.
      - **Forms & Search:** Standardized all forms (`customer`, `product`, `service`, `expense`, `user`, `settings`, `profile`) using `.form-row` and `.form-label`; upgraded global search with `Ctrl+K` shortcut badge, grouped result categories, and keyboard navigation.
      - **Build Verification:** Tested with `ng build` — 100% clean compilation across all chunks.

## Known limitations (by design, for this phase)

- Tokens are stored in `localStorage` on the frontend for simplicity. Consider httpOnly cookies for a
  production hardening pass (noted in `auth.service.ts`).
- Invoice-level `additionalDiscountAmount` is applied **after** tax (a flat rebate off the grand total),
  not proportionally distributed across line items before tax. This is a deliberate MVP simplification —
  proportional pre-tax discount allocation across mixed-tax-rate line items adds real complexity for
  limited benefit at this stage; documented in `InvoiceService` and the DB migration comments so a future
  phase can revisit it if a customer needs GST-compliant discount treatment
- No dedicated `/payments` list page yet — payment history is viewable per-invoice on the invoice detail
  page; a global searchable/filterable payments list (`PaymentController#search` already supports it) is a
  small addition for a future phase
- The dashboard summary endpoint (`GET /api/dashboard`) is currently open to all roles, including STAFF —
  worth revisiting with a `@PreAuthorize` restriction if a business doesn't want staff seeing aggregate
  sales figures
- Report export only covers CSV, and only for the Sales report — PDF and Excel export (spec section 19),
  and CSV for the other five reports, are straightforward additions following the same pattern in
  `ReportController` but weren't all built out to keep that phase's scope contained
- Global search only covers Customers/Products/Services/Invoices, not Payments/Expenses — a straightforward
  addition to `SearchService` following the same pattern
- Payment-provider flows require valid provider keys and webhook secrets; configure each provider's webhook
  at `/api/webhooks/razorpay` or `/api/webhooks/stripe` before enabling paid checkout in production
- Local disk file storage doesn't survive a container redeploy and isn't shared across multiple backend
  replicas — fine for a single-instance deployment (matches this project's current Docker Compose setup),
  but a real multi-instance production deployment needs the S3-compatible `FileStorageService`
  implementation this phase's abstraction was built to support
- The backend unit/integration test suite and Angular production build pass locally; the migration has not
  been exercised against a production PostgreSQL database, and real payment, SMTP, and Docker deployment
  flows still need environment-specific end-to-end verification.

### Phase 12 — Razorpay Checkout, Stripe, multi-branch support, and billing configuration

- [x] Wired `razorpay-checkout.js` into the subscription plan checkout, with Stripe Checkout as a selectable
      provider; both use signed, idempotent webhook activation and require provider credentials plus a
      webhook secret before checkout is offered
- [x] Added branch CRUD and switching (migration `V12`), preserving existing data in a migrated default
      branch. Customers, catalog records, invoices, POS bills, expenses, settings, invoice numbering,
      dashboards, reports, and payment searches are scoped to the active branch; invoice/POS documents use
      the branch's contact and GST details
- [x] Added `invoices.last_reminder_sent_at` and a configurable reminder cooldown (3 days by default)
- [x] Moved plan prices into application configuration and exposed corresponding environment variables

## Next recommended phase

**Phase 13: Payments workspace and expanded reporting** — add a global payments list and extend report
exports beyond the current Sales CSV, while preserving the active-branch scope already used by the APIs.
