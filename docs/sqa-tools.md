# SQA tools (draft)

Tools chosen for the project's software quality assurance, and why. It feeds sections 3.3 (static analysis and quality gates), 5.1 and 5.2 of the QA Manual. Status: **adopted** means configured in the repository; **planned** means chosen, to be configured when the related story starts.

## Choices

| Area | Tool | Status | Why |
|------|------|--------|-----|
| Static analysis and dashboard | SonarCloud | adopted | The guidelines require a dashboard with a blocking quality gate (Q1, Q2). Hosted, so there is no server to run; analyses pull requests and `main`; free for public repositories |
| Code coverage | JaCoCo | adopted | Standard for Maven; one report covers unit and integration tests and feeds SonarCloud. A Maven check also fails the build below 90 % line and branch coverage of `domain` and `application` |
| Mutation testing | PIT (with the JUnit 5 plugin) | adopted | Coverage shows which lines ran, not whether the tests would notice a bug. PIT changes the code and checks that a test fails. It also exposes tautological tests. Slow, so it runs on request and on pull requests, with a minimum mutation score of 80 % on `domain` |
| Architecture tests | ArchUnit | adopted | Turns the hexagonal rules (domain knows no framework or adapter, modules form no cycles, application knows no adapter) into tests that fail the build when broken |
| Code formatting | Spotless with google-java-format | adopted | One style (Google Java Style) applied by a tool, so reviews are not about formatting. `spotless:check` runs in `verify` |
| Unit and integration tests | JUnit 5, AssertJ, Mockito (Spring Boot test starter), Surefire and Failsafe | adopted | Unit tests (`*Test`) and integration tests (`*IT`) run in separate Maven phases (T2, T3) |
| Integration with real services | Testcontainers | planned | Real PostgreSQL, MQTT broker and simulated payment gateway in tests (T3). Added with the first persistence story |
| Acceptance tests (BDD) | Cucumber | planned | Gherkin scenarios for core stories, tagged with business rule IDs (T1) |
| Test management | Jira with Xray | planned | Required (PM5): tests linked to stories, CI results imported |
| Load testing | k6 | planned | Thresholds on a critical flow, checked against the SLOs (T5) |
| End-to-end tests | Playwright | planned | Web UI journeys, run on demand and not on every build (T4). Pairs with the React frontend |
| Dependency vulnerabilities | Dependabot | planned | Zero setup on GitHub, runs on every dependency (Q3) |

Not adopted for now: SpotBugs (SonarCloud already reports the same bug patterns, so it would add a plugin and build time without new findings), container image scanning (Trivy, valued practice, revisited when images are built), accessibility checks (axe or Lighthouse, valued practice).

## Quality gate

SonarCloud judges every pull request from this repository and every push to `main`. Dashboard: [SonarCloud project overview](https://sonarcloud.io/project/overview?id=Ad-Qualquer-Coisa_TQS-Group-P11). The README shows the gate and coverage of `main` as badges.

The project uses a custom gate:

| Scope | Condition |
|-------|-----------|
| New code | rating A for reliability (no new bugs), security (no new vulnerabilities) and maintainability (technical debt of new code at most 5 % of its development cost); coverage at least 80 %; duplicated lines at most 3 %; security hotspots 100 % reviewed |
| Overall | coverage at least 80 %; duplicated lines at most 3 %; security hotspots 100 % reviewed; ratings A for reliability, security and maintainability |

"New code" in a pull request is what the pull request changes; on `main` it is what changed since the previous analysis. The JaCoCo check in Maven (90 % on `domain` and `application`) is a second, stricter coverage gate that fails `verify` itself.

How the gate blocks a merge:

1. The CI `backend` job runs `./mvnw verify` (tests and JaCoCo report), then `./mvnw sonar:sonar -Dsonar.qualitygate.wait=true`. That step waits for SonarCloud's verdict and fails when the gate fails.
2. The `protect-main` ruleset requires the `backend`, `frontend` and `dependency-review` checks to pass before a pull request can be merged, so a failed gate blocks the merge.
3. When the gate has to run but `SONAR_TOKEN` is missing, the job fails instead of skipping the analysis, so the gate cannot be switched off by losing the secret. Dependabot pull requests read the token from the Dependabot secrets, so both secrets must hold it. Pull requests from forks get no secrets: they skip the analysis with a notice and are judged again on `main` after the merge.

Failure notifications: GitHub e-mails the person who triggered a failed run (the pusher, or whoever merged into `main`). Each member enables it under GitHub *Settings → Notifications → Actions* ("Only notify for failed workflows"). SonarCloud also posts the gate result on each pull request.

Not covered yet:

- **Frontend analysis.** SonarCloud analyses only the backend (the Maven scanner sees `proj/backend`). CI already runs the frontend's `test:coverage` script once it exists. Planned: one SonarCloud project for both parts, with the generic scanner (`SonarSource/sonarqube-scan-action`) run from the repository root in place of the Maven scanner, reading the JaCoCo and the Vitest (lcov) reports.
- **E2E and load tests** are in `.github/workflows/extended-tests.yml`, outside the gate: Playwright (`proj/e2e`) on `main` and manual runs, k6 (`proj/load`) on manual runs. Each job is skipped with a notice until its folder has code.

## Running the tools

```bash
cd proj/backend
./mvnw verify                  # unit tests, integration tests, JaCoCo report and check, Spotless check
./mvnw spotless:apply          # fix formatting
./mvnw verify -Pmutation       # also run mutation testing (target/pit-reports/index.html)
```

The JaCoCo report is `target/site/jacoco/index.html`. In CI, `verify` runs on every push and pull request, mutation testing on pull requests, and the SonarCloud analysis on pull requests from this repository and on `main`. The E2E and load jobs start the whole stack with Docker Compose and a throwaway `.env` made by `scripts/ci-env.sh`.
