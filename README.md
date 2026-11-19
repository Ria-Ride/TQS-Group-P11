# TQS-Group-P11 — Ria Ride

[![CI](https://github.com/Ria-Ride/TQS-Group-P11/actions/workflows/ci.yml/badge.svg)](https://github.com/Ria-Ride/TQS-Group-P11/actions/workflows/ci.yml)

## 1. Project abstract

**Ria Ride** is a light-mobility fleet-as-a-service platform developed for the
*Teste e Qualidade de Software* (TQS) course at Universidade de Aveiro, 2026.

**Concretization — tourist mobility.** A specialized operator provides a fleet of e-bikes and
e-scooters to [fictional hotel group] in [fictional city]. Visitors buy day or multi-day passes
(discounted when staying at a partner hotel), find available vehicles at hubs near points of
interest and follow suggested routes between hubs, without an institutional account. The operator
manages the fleet, its maintenance and the rebalancing of vehicles between hubs, while the client
defines the rules of use and follows SLAs and usage KPIs. Tourism-specific challenges include pass
pricing and validity, guest users, seasonal demand peaks and a multilingual interface.

## 2. Project team

| Member          | Role                         |
|-----------------|------------------------------|
| Miguel Neto     | Product Owner / Developer    |
| Diogo Silva     | Team Coordinator / Developer |
| Fernando Santos | QA Engineer / Developer      |
| Bernardo Reis   | DevOps Master / Developer    |

Roles: Team Coordinator, Product Owner, QA Engineer, DevOps Master. Every member is also a Developer.

## 3. Project bookmarks

| Resource                              | Link |
|---------------------------------------|------|
| Jira backlog                          | [Click here](https://tqs-group11.atlassian.net/jira/software/projects/KAN/boards/1/backlog) |
| Related repositories                  | [Fleet telemetry simulator](https://github.com/o-reis/tqs-telemetry-simulator) (own CI, image on GHCR) |
| API documentation (OpenAPI/Swagger)   | [TBD] |
| Static analysis dashboard (SonarQube) | [Click here](https://sonarcloud.io/project/overview?id=Ad-Qualquer-Coisa_TQS-Group-P11) |
| Observability dashboard (Grafana)     | [TBD] |
| Deployed application                  | [TBD] |
| Product Specification report          | [docs/](docs/) |
| QA Manual                             | [docs/](docs/) |

## Repository structure

```
README.md
AGENTS.md            agent instructions (AI2)
.github/             workflows, pull request template
docs/                reports (PDF), presentation, testing notes (docs/testing/)
docker-compose.yml   whole stack as code: backend, frontend, PostgreSQL
.env.example         variable names for .env (copy, then set the values)
proj/backend/        Spring Boot (Java 21) backend
proj/frontend/       web UI (React, Vite, TypeScript; nginx in the container)
proj/simulator/      fleet telemetry simulator: separate repository, cloned here, git-ignored
```

## Running locally

Requires Docker with Docker Compose. Nothing else (no local Java, Maven or Node).

```bash
cp .env.example .env          # then edit .env: set your own database password and APP_JWT_SECRET
openssl rand -base64 48       # generates a value for APP_JWT_SECRET (at least 32 bytes)
docker compose up --build     # backend, frontend and PostgreSQL
```

Never commit `.env`. Compose refuses to start when `APP_JWT_SECRET` is not set.

The first platform admin comes from `ADMIN_EMAIL` and `ADMIN_PASSWORD` (and optionally
`ADMIN_NAME`) in `.env`. At startup the backend creates that ADMIN account if no account has that
e-mail; an existing account is never changed. Without the variables no admin is created and a
warning is logged. Admin roles are changed directly in the database, never through the API.

Open http://localhost (or the `FRONTEND_PORT` set in `.env`). Without a session the login page opens;
from there you can create a guest account. Nginx serves the app and forwards `/api/*` to the backend,
so the browser talks to one origin only (no CORS). `docker compose down` stops the stack and keeps the
data; `docker compose down -v` also deletes the database volume.

To work on the fleet telemetry simulator, clone its repository into `proj/simulator` (that folder
is git-ignored here, so the two repositories stay separate):

```bash
git clone https://github.com/o-reis/tqs-telemetry-simulator.git proj/simulator
```

## Tests

| Part | Command (run in the folder) | What runs |
| --- | --- | --- |
| Backend (`proj/backend`) | `./mvnw clean verify` | Unit tests, integration tests (Testcontainers), Cucumber scenarios, JaCoCo, Spotless |
| Frontend (`proj/frontend`) | `npm test` | Vitest + Testing Library (jsdom) |

Backend integration tests and Cucumber scenarios need Docker running. Gherkin features are in
`proj/backend/src/test/resources/features`.

`AccessMatrixApiIT` checks every (endpoint, caller) pair of the access matrix over HTTP and fails
when an endpoint of the API has no row in it. The pairwise-testing (T7) evaluation for that matrix
is in [`docs/testing/pairwise-access-matrix.md`](docs/testing/pairwise-access-matrix.md).

## API

### Authentication

| Endpoint | Body | Success | Errors |
| --- | --- | --- | --- |
| `POST /auth/guest` | `name`, `email`, `password` | `201` | `400` invalid field, `409` e-mail in use |
| `POST /auth/login` | `email`, `password` | `200` | `400` missing field, `401` invalid credentials |

Both return `{ "userId", "accessToken", "tokenType": "Bearer" }`. Errors are
[problem details](https://www.rfc-editor.org/rfc/rfc9457) with an `errors` map of field → message.

**Access token**: JWT signed with HS256. Claims: `sub` (user id, UUID), `role`
(`END_USER`, `OPERATOR`, `CLIENT_MANAGER`, `ADMIN`), `iat`, `exp`. Valid for
`app.jwt.time-to-live` (default `PT1H`, 1 hour), with no clock skew and no refresh token.
Send it as `Authorization: Bearer <token>`.
The signing key comes from the `APP_JWT_SECRET` environment variable (at least 32 bytes).
Never commit it.

Rules: e-mails are case-insensitive. Passwords have 8 characters to 72 bytes (BCrypt limit).
Unknown e-mail and wrong password give the same 401.

On every request the account is read from the database: a token of a deactivated or deleted
account gets `401`, and the role comes from the database, not from the token's `role` claim, so a
role change applies at once. A deactivated account cannot log in (same `401` as wrong credentials).

### Access control (BR23)

Which role may call which endpoint is defined in the access matrix,
[`proj/backend/src/test/resources/security/access-matrix.csv`](proj/backend/src/test/resources/security/access-matrix.csv)
(`Y` allowed, `N` denied, `OWN` own resource only), and enforced in `SecurityConfiguration`.
`ADMIN` is allowed everywhere. Paths that are not in the matrix are denied to everyone.

- No token, an invalid or expired token, or the token of a deactivated account: `401`, with a
  `WWW-Authenticate: Bearer` header.
- A valid token without permission: `403`.

Both use the same problem-details body as the other errors.

**Adding an endpoint:** add its row to the matrix, add its rule to `SecurityConfiguration` and, if
the resource belongs to a user, add its path to `OWNERSHIP_CHECKED` in `AccessMatrixApiIT`. CI
fails until the matrix and the code agree.

### Users

| Endpoint | Success | Errors |
| --- | --- | --- |
| `GET /users/{id}` | `200` with `id`, `name`, `email`, `role` | `401` missing, invalid or expired token; `403` not your own account, unless you are an `ADMIN` (BR23) |

Every endpoint except registration, login, `/actuator/health` and `/error` requires a bearer token.

### Admin accounts (US13)

Only for `ADMIN`. Responses never include the password.

| Endpoint | Body | Success | Errors |
| --- | --- | --- | --- |
| `GET /admin/users` | — | `200`, ordered by name | — |
| `POST /admin/users` | `name`, `email`, `password`, `role`, `fieldTechnician` (optional, default `false`) | `201` | `400` invalid field, `409` e-mail in use or role `ADMIN` |
| `PATCH /admin/users/{id}/role` | `role`, `fieldTechnician` (optional, default `false`) | `200` | `400` invalid field, `404` unknown account, `409` admin account or role `ADMIN` |
| `PATCH /admin/users/{id}/status` | `active` | `200` | `400` missing `active`, `404` unknown account, `409` admin account |

Each returns `{ "id", "name", "email", "role", "fieldTechnician", "active" }`. Only an `OPERATOR`
can be a field technician (BR20, also enforced by a database constraint). Admin accounts cannot be
created, changed or deactivated through the API.

## Frontend

React + Vite + TypeScript with React Router. Pages: guest registration (`/register`),
login (`/login`), home (`/`, requires a session) and account management (`/admin/users`, for
admins only: list, create, change role, activate or deactivate; the home page links to it for
admins). It uses the Ria Ride palette and the Outfit
font (served from our own origin with Fontsource). The colours are CSS variables in
`proj/frontend/src/styles/theme.css`, checked for WCAG AA contrast. The access token is kept in
`sessionStorage` for as long as the tab is open.

## Contributing

All changes go through a Pull Request from a feature branch named after the Jira story
(`KAN-XX-short-description`). Direct pushes to `main` are blocked; merging requires at least one
approval from another team member.

### Dependency vulnerability scanning

Dependencies are checked for known vulnerabilities (Q3) in two ways:

- **On every pull request**, the [Dependency review](.github/workflows/dependency-review.yml)
  workflow fails when the PR adds or upgrades to a runtime dependency with a known
  vulnerability of **high or critical** severity, and posts a summary comment on the PR.
  It is a required check, so such a PR cannot be merged.
- **On `main`**, Dependabot alerts report vulnerabilities already in the code, in the
  repository's *Security* tab. Dependabot also opens security-fix PRs, and, every Monday,
  grouped version-update PRs for Maven, npm and GitHub Actions
  ([configuration](.github/dependabot.yml)).

Limitation: inside a pull request, the review sees changes to direct Maven dependencies
in `pom.xml`. A vulnerable transitive dependency that comes in indirectly (for example,
with a Spring Boot upgrade) is reported by Dependabot alerts after the merge.
The frontend's `package-lock.json` lists every package, so npm has no such gap.