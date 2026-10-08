# Capstone verification — 2026-10-07

> Dated executed verification record; statements about credentials/services below
> describe that run, not their current state. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md)
> is the canonical living architecture. Documentation-only changes do not rerun this gate.

Environment: JDK 25.0.4.1 selected for Maven 3.9.16; Boot 4.1.1;
Node 24.19.0/npm 12.2.0; Docker 29.8.2/Compose 5.5.1, Linux containers.

| Gate | Actual result |
| --- | --- |
| Backend `mvnw.cmd --batch-mode clean verify` | 83 tests, 0 failures/errors/skips |
| MCP module `../../backend/mvnw.cmd --batch-mode clean verify` | 2 tests, 0 failures/errors/skips |
| Frontend `npm run build` | TypeScript + Vite production build passed |
| Explicit `-Dtest=TemporalRestartSmoke test` | 1 passed, real Temporal worker restart/replay/approval |
| Explicit `-Dtest=McpInvestigationSmoke test` | 1 passed, real remote runbook persisted into PostgreSQL investigation |
| Explicit `-Dtest=ContainerStackSmoke test` | 1 passed, real six-service pipeline and backend process restart |
| Compose config/build/up --wait | Passed; six healthy services |
| Fresh PostgreSQL | V1–V6 applied; Hibernate validated; tables/history/constraints inspected |
| Browser-origin HTTP checks | Frontend/health 200; protected me/investigation 401 |
| MCP browser Origin rejection | 403 |

**88 passed test executions across the final standard suites and three explicit
smokes.** The optional smokes are not normal build prerequisites. All existing
persistence/security/OIDC tests remain. New contracts cover schemas/tool loop,
classification/routing, bounded context, fan-out/fan-in, retries/timeouts/fallback,
approval authorization/CSRF, idempotent ledger, durable Activity retry/deadline,
approval timer/approve/deny, metrics/prompts, real Redis and deterministic evaluation.
One regression test confirms start intent and investigation commit together.
Required evidence failure persists typed details and becomes FAILED, not an
apparently successful report.

The full-stack smoke seeded an isolated synthetic owner/investigation, collected
actual private MCP documentation, waited for approval, restarted the backend
container, resumed from Temporal history, approved with the trusted fixture owner,
and verified PostgreSQL report/decision plus exactly one mock ledger row. It
removed its database fixtures. Separate MockMvc tests exercise actual security,
CSRF and authenticated create/approve/read with the supported Temporal test server.
Real Google authentication was not claimed for this fixture-based smoke.

Failures encountered and fixed: Spring AI 2 API/options adaptations and validation
advisor retry multiplication; test imports/options; Micrometer callable wrapper
API mismatch; Temporal SQLite volume ownership; Docker wrapper tar fallback
breaking ZIP checksum validation (installed unzip, preserved checksum); optional
smoke checked TimeoutException declaration; Windows clean blocked by the temporary
MCP JAR process (stopped only that process, reran clean successfully). Final audit
also fixed required-evidence failure persistence and the atomic durable-start flag.
There are no remaining failed verification commands. Upstream JDK/Mockito/protobuf
deprecation warnings and test-server heartbeat-capability warnings are non-fatal.

No OPENAI_API_KEY or Google credentials existed. No live-provider smoke/login was
performed. Generated infrastructure passwords are only in ignored root `.env`,
excluded from Docker build contexts and Git. No credentials or tokens are printed
here. The six Compose services remain running with named PostgreSQL/Temporal
volumes; the temporary standalone MCP process has been stopped.
