# TQS-Group-P11 — [Product name]

[![CI](https://github.com/Ria-Ride/TQS-Group-P11/actions/workflows/ci.yml/badge.svg)](https://github.com/Ria-Ride/TQS-Group-P11/actions/workflows/ci.yml)

## 1. Project abstract

**[Product name]** is a light-mobility fleet-as-a-service platform developed for the
*Teste e Qualidade de Software* (TQS) course at Universidade de Aveiro, 2026.

**Concretization — tourist mobility.** A specialized operator provides a fleet of e-bikes and
e-scooters to [fictional hotel group] in [fictional city]. Visitors buy day or multi-day passes
(discounted when staying at a partner hotel), find available vehicles at hubs near points of
interest and follow suggested routes between hubs, without an institutional account. The operator
manages the fleet, its maintenance and the rebalancing of vehicles between hubs, while the client
defines the rules of use and follows SLAs and usage KPIs. Tourism-specific challenges include pass
pricing and validity, guest users, seasonal demand peaks and a multilingual interface.

## 2. Project team

| Member           | Role                         |
|------------------|------------------            |
| Miguel Neto      | Product Owner / Developer    |
| Diogo Silva      | Team Coordinator / Developer |
| Fernando Santos  | QA Engineer / Developer      |
| Bernardo Reis    | DevOps Master / Developer    |

Roles: Team Coordinator, Product Owner, QA Engineer, DevOps Master. Every member is also a Developer.

## 3. Project bookmarks

| Resource                         | Link |
|----------------------------------|------|
| Jira backlog                     | [Click here](https://tqs-group11.atlassian.net/jira/software/projects/KAN/boards/1/backlog) |
| Related repositories             | [Fleet telemetry simulator](https://github.com/o-reis/tqs-telemetry-simulator) (own CI, image on GHCR) |
| API documentation (OpenAPI/Swagger) | [TBD] |
| Static analysis dashboard (SonarQube) | [Click here](https://sonarcloud.io/project/overview?id=Ad-Qualquer-Coisa_TQS-Group-P11) |
| Observability dashboard (Grafana)   | [TBD] |
| Deployed application             | [TBD] |
| Product Specification report     | [docs/](docs/) |
| QA Manual                        | [docs/](docs/) |

## Repository structure

```
README.md
AGENTS.md            agent instructions (AI2)
.github/             workflows, pull request template
docs/                reports (PDF), presentation
docker-compose.yml   whole stack as code: backend, frontend, PostgreSQL
.env.example         variable names for .env (copy, then set the values)
proj/backend/        Spring Boot (Java 21) backend
proj/frontend/       web UI (React, Vite, TypeScript; nginx in the container)
proj/simulator/      fleet telemetry simulator: separate repository, cloned here, git-ignored
```

## Running locally

Requires Docker with Docker Compose. Nothing else (no local Java, Maven or Node).

```bash
cp .env.example .env          # then edit .env and set your own password
docker compose up --build     # backend, frontend and PostgreSQL
```

Open http://localhost (or the `FRONTEND_PORT` set in `.env`). The page shows `Backend status: UP` when the
frontend reaches the backend and the backend reaches the database. `docker compose down` stops the
stack and keeps the data; `docker compose down -v` also deletes the database volume.

To work on the fleet telemetry simulator, clone its repository into `proj/simulator` (that folder
is git-ignored here, so the two repositories stay separate):

```bash
git clone https://github.com/o-reis/tqs-telemetry-simulator.git proj/simulator
```

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