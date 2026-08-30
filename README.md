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

## What's implemented in Phase 1

- [x] Business registration (creates tenant + OWNER user + default settings, atomically)
- [x] Login with account lockout after 5 failed attempts (15 min)
- [x] JWT access tokens (15 min) + rotating opaque refresh tokens (7 days, hashed at rest, revocable)
- [x] Logout (revokes refresh token server-side)
- [x] Forgot password / reset password / change password
- [x] Multi-tenant isolation via `TenantContext`, enforced independently of anything the client sends
- [x] Role field on users (`OWNER`, `ADMIN`, `MANAGER`, `STAFF`) — fine-grained per-module permission checks
      are added as each domain module is built
- [x] Centralized structured error handling (`ApiError`), no stack traces leaked to clients
- [x] Async audit logging wired into register/login/password events
- [x] CORS locked to explicit origins, BCrypt password hashing, no secrets in source
- [x] Angular auth flow: login/register forms, JWT interceptor, 401 handling, route guards
- [x] Tenant isolation test

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
- Backend was not compiled in this environment (no Maven/Maven-Central network access here) — run
  `mvn clean install` locally to verify before deploying.

## Next recommended phase

**Phase 2: Customers + Products + Services modules** — full CRUD, tenant-scoped, with search/pagination/
validation, per spec sections 8–10, plus the `PreAuthorize` role checks per endpoint that this phase's
`UserRole` enum sets up but doesn't yet enforce per-action.
