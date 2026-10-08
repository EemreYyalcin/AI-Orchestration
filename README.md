# AI Incident Orchestrator

AI incident investigation with deterministic Java orchestration, bounded
evidence tools, optional Spring AI/OpenAI, documentation MCP and durable
Temporal approval workflows. Existing Google OIDC, HttpSession and CSRF remain.
Operational evidence and remediation are explicitly simulations.

Start with [PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) for the canonical current
architecture, runtime contracts, limitations and maintenance rules. Executable
code and tests take precedence; checkpoint guides retain specialized or historical detail.

For the six-service container stack and credentials-free verification, read
[capstone startup](docs/capstone.md), [final architecture](docs/final-architecture.md)
and [checkpoint verification](docs/implementation-progress.md). Default container
profiles need no OpenAI/Google credentials; browser login needs Google configured.

## Prerequisites

- JDK **25**, selected by both `JAVA_HOME` and the IDE. Verify with `java -version`.
- Node.js **24.15 or newer within major 24**, with npm **12**. Verify with
  `node --version` and `npm --version`.
- Docker Desktop with Linux containers and Docker Compose v2. Verify with
  `docker version` and `docker compose version`.
- Maven is provided by the committed Maven Wrapper (3.9.16); no global Maven
  installation is required. The first run needs internet access.

## Local startup (PowerShell)

Open a terminal at the repository root. Start Docker Desktop and wait for its
engine to become ready.

1. Copy the environment template:

   ```powershell
   Copy-Item .env.example .env
   ```

   Replace infrastructure placeholders in `.env` with local values. For Google
   login also replace the Google placeholders; see [setup](docs/authentication.md).
   Use database/user
   names containing lowercase letters, digits, and underscores. Use distinct
   generated passwords. Keep values on one line, without surrounding quotes or
   inline comments, for the PowerShell loader below. Never commit `.env`.

2. Validate and start infrastructure:

   ```powershell
   docker compose config --quiet
   docker compose up -d postgres redis temporal documentation-mcp-server --build
   docker compose ps
   ```

   Wait until the infrastructure services report `healthy`. Alternatively,
   `docker compose up -d --wait` waits for the health checks.

   The Compose MCP service is private and does not publish port 8081. For the
   host backend's `mcp` profile, also run the standalone documentation server on
   localhost:8081 as described in [MCP setup](docs/mcp.md), or deliberately
   configure a reachable MCP URL. The full container stack connects privately.

3. In the backend terminal, load the same environment values and run Spring Boot:

   ```powershell
   Get-Content -LiteralPath .env | ForEach-Object {
       $line = $_.Trim()
       if ($line -and -not $line.StartsWith('#')) {
           $pair = $line -split '=', 2
           if ($pair.Count -ne 2) { throw 'Invalid environment file line' }
           [Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1], 'Process')
       }
   }
   Set-Location backend
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,temporal,mcp'
   ```

   Compose reads root `.env` automatically; Maven and your IDE do **not**. For IDE
   startup, select JDK 25, run `AiIncidentOrchestratorApplication`, activate the
   `local,temporal,mcp` profiles, and supply `POSTGRES_DB`, `POSTGRES_USER`,
   `POSTGRES_PASSWORD`, and `REDIS_PASSWORD` in the run configuration. Do not
   commit a run configuration containing credentials.

   For Google login, configure Google Console, load `GOOGLE_CLIENT_ID` and
   `GOOGLE_CLIENT_SECRET`, and add `google` to `local,temporal,mcp`.
   `local` alone starts without Google credentials and leaves login unconfigured.
   Protected APIs still require authentication. See [exact setup](docs/authentication.md).

4. In a separate terminal at the repository root, start the frontend:

   ```powershell
   Set-Location frontend
   npm ci
   npm run dev
   ```

5. Open <http://localhost:5173>. The page should show
   **AI Incident Orchestrator — Backend status: UP** and a Google sign-in button.
   Sign-in requires the `google` backend profile and real credentials.
   If the backend was started after the page loaded, refresh the page.

## Verification

With the applications running:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:5173/api/health
```

`/api/health` returns `{"status":"UP"}` as an application smoke test. It does not
query infrastructure. Actuator's aggregate health checks PostgreSQL and Redis;
its `readiness` group checks PostgreSQL only. A Redis outage may make aggregate
health `DOWN` but must not make readiness or `/api/health` depend on Redis.
Health details and component names are hidden.

Build commands (from the relevant directory):

```powershell
# backend, requires JDK 25 and a running Docker engine for PostgreSQL integration tests
.\mvnw.cmd --version
.\mvnw.cmd --batch-mode verify

# frontend
npm ci
npm run build
```

For macOS/Linux use `sh ./mvnw` instead of `mvnw.cmd`, export the environment
variables, and use `--spring.profiles.active=local` in your IDE or the Maven
profile argument shown above. The Vite proxy applies to `npm run dev`;
`npm run preview` does not provide the development API proxy.

## Infrastructure operations

Run these commands from the root:

```powershell
docker compose ps
docker compose logs --tail=100 postgres redis
docker compose logs -f postgres redis
docker compose stop
docker compose down
```

`stop` preserves containers and data. `down` removes the Compose containers and
network while preserving named PostgreSQL and Temporal volumes. Redis admission
counters are intentionally ephemeral.

**Destructive database reset:** the next command permanently removes the local
PostgreSQL and Temporal data volumes. Run it only when you intentionally want
to erase investigations, workflow history and migration history:

```powershell
docker compose down --volumes
docker compose up -d
```

PostgreSQL initialization variables apply only to an empty volume. Editing a
password in `.env` does not change a password in an existing database. Update the
database deliberately, or intentionally reset disposable local data as above.

Published developer ports are bound to `127.0.0.1`: 5432 (PostgreSQL), 6379
(Redis), 7233/8233 (Temporal/API UI), and 5173 (frontend). Developer-mode backend
uses localhost:8080; the container backend is private. If a port is
already in use, stop the conflicting service or deliberately update both ends
of its configuration. Vite fails instead of silently selecting another port.

## Request flow

```text
Browser → Vite :5173 → /api/health proxy → Spring Boot :8080
                                      → PostgreSQL :5432 (JPA/Flyway/health)
                                      → Redis :6379 (configured connection/health)
```

The browser fetches the relative URL `/api/health`. That endpoint has no database
or Redis access. Spring Boot connects to PostgreSQL during startup and checks
both infrastructure services through Actuator when aggregate health is queried.
Redis supplies atomic investigation admission when enabled; it holds no
authentication sessions or cached evidence.

## Scope and documentation

- [Architecture and decisions](docs/architecture.md)
- [Canonical current architecture and future development rules](docs/PROJECT_CONTEXT.md)
- [Session security, CSRF, logout, and verification](docs/security.md)
- [Google OIDC and Google Console setup](docs/authentication.md)
- [Checkpoint 5 verification](docs/checkpoint-5-verification.md)
- [Checkpoint plan](docs/implementation-plan.md)
- [Verification record and environment limitations](docs/verification.md)

The persistence layer now includes `app_users`, an external identity record,
and a transactional provisioning service. See [persistence](docs/persistence.md)
for schema, transaction, concurrency, and PostgreSQL test details. Flyway applies
V1–V6 on backend startup before Hibernate validates the mappings; do not manually
create `app_users` or change an applied migration.

Tests use their own disposable PostgreSQL 18 container and do not touch the
Compose database. Run `mvnw.cmd test` or `mvnw.cmd verify` from `/backend` with
JDK 25 and Docker available. No `.env`, local profile, or running Compose stack
is needed for those tests; Testcontainers supplies database connection details.

Spring Security now protects `/api/**`, except GET health and CSRF bootstrap.
The intentionally exposed Actuator health GET endpoints remain public.
Unauthenticated protected API requests return 401 without a login redirect;
missing/invalid CSRF on unsafe requests returns 403 before final authorization.
`GET /api/csrf` returns a session-backed token/header DTO. `POST /logout` is CSRF
protected and returns 204 after clearing the session and cookie.

Sessions have a 30-minute idle timeout, HttpOnly/SameSite=Lax cookies, and cookie-only
tracking. Secure defaults to true; the local HTTP profile sets it false. There is
no generated production user. Google login provisions a PostgreSQL user before
saving authentication. React checks `/api/me`, displays the safe profile and
logs out with CSRF. Tests use a loopback provider, requiring no real credentials.

There are no user-facing CRUD endpoints, application JWTs or Redis sessions.
Incident orchestration, optional AI, MCP, Temporal and application containers are
documented in [the capstone](docs/capstone.md). This local stack is not configured
for public deployment. Work stops at Checkpoint 14.
