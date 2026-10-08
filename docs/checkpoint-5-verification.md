# Checkpoint 5 verification — 2026-10-07

> Historical authentication gate. See [capstone verification](capstone-verification.md)
> for the later integrated gate and [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for
> canonical current architecture. Results here remain dated checkpoint evidence.

## Environment and commands

The shell originally selected Oracle Java 23 with no JAVA_HOME. Existing
Temurin **25.0.4.1+1** was selected in process-local JAVA_HOME/PATH, and
`java -version`, JAVA_HOME, and `mvnw.cmd -version` confirmed JDK 25.0.4.1 and
Maven Wrapper **3.9.16**. No project downgrade or new JDK installation.

Verification used Node **24.19.0**, portable npm **12.2.0**, Docker Desktop's
engine **29.8.2** and Compose **5.5.1**. npm and Docker CLI are not on the default
shell PATH here; existing tools were selected for the verification processes.

Commands executed, with the environment/tool locations selected as above:

```text
java -version
mvnw.cmd -version
mvnw.cmd test
mvnw.cmd --batch-mode verify
node <existing npm-cli.js> run build              (frontend)
docker compose --env-file <temporary fixture> --project-name ai-incident-orchestrator-checkpoint5-verify config --quiet
docker compose --env-file <temporary fixture> --project-name ai-incident-orchestrator-checkpoint5-verify up -d --wait
docker compose --env-file <temporary fixture> --project-name ai-incident-orchestrator-checkpoint5-verify ps
java -jar target/ai-incident-orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
node node_modules/vite/bin/vite.js                (frontend)
Invoke-WebRequest <direct/proxied health, me, csrf and OAuth paths>
Invoke-WebRequest <logout> -Method Post <with/without CSRF>
docker exec <fixture postgres> psql <table/constraint/Flyway inspection>
```

Temporary random infrastructure passwords were created outside Git. The existing
Compose configuration ran under the separate project name above with its own
disposable PostgreSQL volume. No developer database or root `.env` was modified.
Tests independently used a Testcontainers PostgreSQL 18 instance via
`@ServiceConnection`.

## Results

| Check | Result |
| --- | --- |
| Maven tests / verify | Passed, 36 tests, 0 failures, 0 errors, 0 skipped. |
| Existing security tests | 15 passed, unchanged. |
| PostgreSQL integration tests | 12 passed, including the new adapter/provisioning test and existing uniqueness/concurrency/rollback tests. |
| OIDC adapter unit tests | 2 passed: mapping/internal identity and provisioning failure. |
| OAuth framework flow tests | 7 passed: actual google profile callback/scopes, safe me, fixed redirects, session/CSRF, provisioning failure, state/nonce/signature rejection. |
| Frontend TypeScript + Vite build | Passed. No added frontend dependencies. |
| Compose health checks | PostgreSQL and Redis healthy. |
| Packaged backend startup, local profile | Passed on JDK 25.0.4.1. Flyway applied V1 and Hibernate schema validation completed. |
| `/api/health`, direct + Vite | 200, UP. |
| Actuator aggregate/liveness/readiness, direct | 200, UP. |
| `/api/me`, anonymous via Vite | 401, no Location header. |
| `/api/csrf`, via Vite | 200; token/header DTO; HttpOnly local session cookie, Secure=false. |
| POST logout without CSRF | 403. |
| POST logout with real endpoint token | 204. |
| Stale token after logout | 403; fresh anonymous CSRF bootstrap returned 200. |
| Vite root page | 200, frontend index served. |
| OAuth entry/callback proxy without google profile | Backend JSON 404, not SPA fallback: login intentionally unconfigured without credentials. |
| PostgreSQL schema inspection | app_users and flyway_schema_history present; V1 successful; UUID PK and provider/subject unique constraint present; no token columns. |

OAuth tests load **application-google.yml** itself, supply clearly test-only
credential values, and replace Google protocol endpoints with a loopback HTTP
provider. The fixture signs RSA ID tokens and serves JWKS/UserInfo. Spring's
real token client/decoder/filter chain performs exchange and validation. Tests
prove failed provisioning never saves an authenticated SecurityContext; valid
login changes the session id, rejects pre-login CSRF, returns exactly the four
safe DTO fields, and does not retain an authorized client/access token in the
framework's default authorized-client service.

These are complementary checks: PostgreSQL persistence runs against a real
container, while filter-chain OAuth tests mock the application services and use
the fixture provider. They are not a live Google-to-PostgreSQL browser acceptance
test.

## Errors, warnings and limits

- No Maven tests, verify, frontend build, runtime smoke checks or schema checks
  failed. PostgreSQL warnings for deliberately triggered duplicate/check
  constraints are expected assertions of rollback/concurrency behavior.
- Mockito/Byte Buddy reports dynamic agent loading on JDK 25. This existing
  non-fatal warning did not affect the tests; no build bypass was added.
- A read of a nonexistent App.css was corrected to inspect the actual frontend
  files. A Windows rg wildcard argument was corrected to use `-g '*.txt'`.
  Two patch attempts failed validation (duplicate file operation and mismatched
  document heading); they made no partial changes and were reapplied correctly.
- Real `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` were absent. Real Google
  account login, consent, callback and returning-user browser verification were
  **not performed**. Setup and manual acceptance steps are in
  [authentication.md](authentication.md). No live credentials were invented.
- Public deployment remains outside scope: current callback/success/failure
  destinations are fixed localhost development URLs.

The verification backend and Vite processes were stopped afterward (Ctrl+C
produces an expected termination exit code). The disposable Compose project,
its volume/network and temporary credential file were removed. Work stops at
Checkpoint 5; no authentication-adjacent business features or AI were added.

## Files created and modified

Paths below are relative to the repository root.

Created:

```text
backend/src/main/java/dev/orchestrationlab/incident/authentication/application/ApplicationPrincipal.java
backend/src/main/java/dev/orchestrationlab/incident/authentication/controller/CurrentUserController.java
backend/src/main/java/dev/orchestrationlab/incident/authentication/infrastructure/oidc/ApplicationOidcUser.java
backend/src/main/java/dev/orchestrationlab/incident/authentication/infrastructure/oidc/GoogleOidcUserService.java
backend/src/main/java/dev/orchestrationlab/incident/authentication/infrastructure/oidc/DiscardingAuthorizedClientRepository.java
backend/src/main/java/dev/orchestrationlab/incident/user/application/CurrentUserService.java
backend/src/main/resources/application-google.yml
backend/src/test/java/dev/orchestrationlab/incident/authentication/GoogleOidcUserServiceTest.java
backend/src/test/java/dev/orchestrationlab/incident/authentication/OAuthSecurityFlowTest.java
docs/authentication.md
docs/checkpoint-5-verification.md
```

Modified:

```text
.env.example
README.md
backend/pom.xml
backend/src/main/java/dev/orchestrationlab/incident/configuration/SecurityConfiguration.java
backend/src/main/resources/application-local.yml
backend/src/test/java/dev/orchestrationlab/incident/user/UserPersistenceIntegrationTest.java
frontend/src/App.tsx
frontend/vite.config.ts
docs/architecture.md
docs/implementation-plan.md
docs/security.md
docs/verification.md
```

The migration/entity/repository/provisioning implementation and Compose topology
are unchanged. Build outputs remain ignored. This repository already contained
untracked foundation files before this checkpoint; no Git commit was requested
or created.
