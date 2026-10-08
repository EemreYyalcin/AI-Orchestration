# Implementation progress

> Historical checkpoint log: counts and limitations apply to their individual gates.
> [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is the canonical current architecture;
> [capstone verification](capstone-verification.md) records the final executed gate.

## Checkpoint 6 — investigation foundation

- Purpose: persist a minimal investigation and establish authenticated ownership.
- Added: investigation domain/repository/application/controller packages,
  Flyway V2, InvestigationIntegrationTest, ai-orchestration-foundation.md.
- Changed: persistence test current migration expectation advances to V2 while
  retaining schema/constraint assertions. Foundations 1–5 remain in place.
- Architecture: UUID ownership, bounded question, safe DTOs, service transactions,
  owner-filtered reads with non-disclosing 404 responses.
- Dependencies: none.
- Tests: real PostgreSQL migration/JPA/API ownership, CSRF/authentication,
  invalid question and stale owner; all existing tests retained.
- Verification: JDK 25.0.4.1, Maven 3.9.16; `mvnw.cmd --batch-mode verify`
  passed 40 tests (0 failures/errors/skips), including clean PostgreSQL migrations
  and four new API/persistence boundary tests. Checkpoint gate passed.
- Decisions: no LLM calls or agent loop, no generic ticketing model, no serialized
  objects, no future adapter abstractions.
- Limits: creates and reads only; no execution yet.
- Next: Checkpoint 7 Spring AI and constrained tool calling.

## Checkpoint 7 — Spring AI and tools

- Purpose: bounded tool calling with provider-neutral model and typed evidence.
- Added: reasoning/application and infrastructure adapters, tool/application
  contracts, simulated adapter, ReasoningConfiguration, application-ai.yml,
  tool-calling.md; tests follow below.
- Changed: POM Spring AI BOM 2.0.1 and official OpenAI starter; default model
  auto-configuration disabled; environment example gains optional key/model.
- Architecture: four schemas → per-request ToolSession → authorized execution
  service → replaceable read-only adapter. Model loop remains bounded in Java.
- Dependencies: Spring AI 2.0.1, official compatible stable docs verified.
- Verification: JDK 25 `mvnw.cmd --batch-mode verify` passed 49 tests (0
  failures/errors/skips). Nine new tests cover schemas, dispatch, validation,
  policy, timeout, outputs, fake/real framework loops and call budgets. Test
  import ambiguity and a fake ChatModel missing required options were fixed.
- Decisions/limits: simulated evidence explicitly labelled; no live key required,
  no arbitrary SQL/URLs/code; only the optional ai profile calls OpenAI.
- Next: Checkpoint 8 typed semantic classification and deterministic routing.

## Checkpoint 8 — structured output and routing

- Added: classification enums/record/model contract/service, WorkflowRoutingPolicy,
  structured-routing.md and focused classification tests.
- Changed: model adapters implement classification; optional native output.
- Architecture: schema/record validation → bounded retry/degraded fallback →
  Java route with Java-selected required evidence.
- Dependencies: none beyond pinned Spring AI 2.0.1.
- Verification: JDK 25 `mvnw.cmd --batch-mode verify` passed 55 tests,
  0 failures/errors/skips. Six new schema/routing tests cover valid output,
  malformed correction, enums/unknown tools, bounded fallback and route policy.
  A real retry multiplication regression was found and fixed by using one
  explicit schema advisor without enabling a second automatic advisor.
- Limits: offline heuristics are simulation; no arbitrary workflow commands.
- Next: Checkpoint 9 real documentation MCP boundary.

## Checkpoint 9 — documentation MCP

- Added: independent documentation-server Maven application, fixed corpus,
  real Streamable HTTP client/server tests, McpDocumentationAdapter, mcp.md.
- Changed: backend official MCP client starter; default auto-client disabled;
  mcp profile swaps documentation adapter only.
- Architecture: tool policy → approved remote documentation capability. MCP is
  transport/discovery, not workflow ownership. Lazy connection tolerates outages.
- Dependencies: Spring AI 2.0.1 MCP starters, BOM-managed Java SDK 2.0.0;
  current official Spring/MCP transport docs verified before changes.
- Tests: real server discovery/call, fixed corpus bounds, unavailable adapter.
- Verification: both modules `verify` passed on JDK 25: 56 backend tests and
  2 MCP server tests, all green; actual Streamable HTTP startup/discovery/call
  verified with the supported Java SDK, plus bounded unavailable-server behavior.
- Limits: educational runbooks, private/loopback service, no extra MCP features.
- Next: Checkpoint 10 bounded selected context and persisted workflow state.

## Checkpoint 10 — context engineering/state

- Added: typed evidence/bundle/selection/budget, sanitizer and ContextBuilder;
  InvestigationStateService, Flyway V3 and context-state.md.
- Changed: investigation optimistic version + typed JSONB classification/evidence;
  safe owner DTO includes state; tool outputs sanitize before truncation.
- Architecture: required-first selection → sanitize/filter/rank/budget → context;
  strict persisted phases with short transactions outside external calls.
- Dependencies: none.
- Tests: selection/character/item/source limits, redaction, typed failures,
  PostgreSQL JSONB roundtrip and illegal/foreign-owner transition rejection.
- Verification: JDK 25 Maven verify passed 61 tests, zero failures/errors/skips;
  fresh V1–V3 PostgreSQL migrations and Hibernate validation passed.
- Decisions/limits: NO LONG-TERM MEMORY YET; no raw prompts/logs persisted,
  application bounds are characters/items, not invented token counts.
- Next: Checkpoint 11 parallel orchestration, fallback and owner approval.

## Checkpoint 11 — orchestration patterns

- Added: parallel bounded EvidenceCollector, typed synthesis and validating
  primary/fallback boundary, IncidentOrchestrator, persisted report/approval,
  idempotent mock action ledger (V4), run/approve/deny endpoints, orchestration.md.
- Architecture: sequential/conditional workflow, parallel fan-out/fan-in,
  safe read retries/deadlines, conservative model fallback and owner approval.
- Dependencies: none. Circuit breaking deferred with rationale in orchestration.md.
- Tests: PostgreSQL report/approval/idempotency, unauthorized and CSRF decisions,
  denial, malformed citations/retry/fallback; prior timeout/authorization tests retained.
- Verification: JDK 25 Maven verify passed 64 tests; a subsequent focused
  fan-out/fan-in contract gate verifies simultaneous reads and optional degradation.
- Limits: synchronous execution for this gate; no real action; durable waits next.
- Next: Checkpoint 12 Temporal.

## Checkpoint 12 — durable workflow

- Added: official Temporal workflow/Activities/Update/query, explicit worker
  profile/configuration, durable start-request V5, idempotent phase projections,
  temporal.md and supported Java test environment scenarios.
- Changed: execution boundary selects synchronous development or Temporal runtime;
  no network work inside workflow code or database transactions.
- Dependencies: official Temporal starter/testing 1.40.0; current official docs
  and releases checked before POM change; official CLI 1.9.1 planned locally.
- Tests: success, retry, failure, deadline, pending/approve/deny, owner rejection;
  existing PostgreSQL ledger duplicate-execution tests retained.
- Verification: JDK 25 Maven verify passed 72 tests (zero failures/errors/skips).
  Compose validation and actual Temporal CLI service health returned SERVING.
  Initial SQLite permission failure fixed by mounting the non-root home directory;
  the previously created root-owned volume was corrected explicitly.
- Limits: private local development service; no child workflows or production
  Temporal cluster. Actual worker restart verification follows in capstone.
- Next: Checkpoint 13 observability/security/evaluation.

## Checkpoint 13 — production AI controls

- Added: telemetry/propagation, versioned prompt resources, V6 audit metadata,
  atomic Redis start limiter, deterministic evaluation TSV/contracts, metrics
  and real Redis tests, production-ai.md.
- Changed: model adapters use observed ChatClients; executors propagate context;
  tool/MCP boundaries instrumented; persistent approval and terminal audit.
- Dependencies: Boot-managed OpenTelemetry starter/shim, official Temporal
  OpenTracing integration 1.40.0. Official observability APIs checked.
- Tests: five evaluation scenarios/citations, prompt/capability policy, real
  Redis expiry/isolation/fail-closed, bounded-label metrics and prior boundaries.
- Verification: backend Maven verify passed 77 tests and MCP module verify
  passed 2 tests, zero failures/errors/skips. Real Redis expiry/isolation tested.
  Initial context-wrapper API mismatch fixed using the current callable overload.
- Limits: no prompt content export, no invented usage/pricing; no cache or
  long-term memory. External collector and real model evaluation are optional.
- Next: Checkpoint 14 integrated API/frontend/container capstone.

## Checkpoint 14 — integrated capstone

- Added: minimal investigation React UI; backend container profile; non-root
  multi-stage Dockerfiles, same-origin nginx proxy, full six-service Compose;
  durable authenticated API integration, real MCP/worker-restart optional smokes,
  capstone.md and final architecture review.
- Changed: durable-profile create automatically starts and returns ID; outage
  returns saved ID plus 503 rather than pretending a start succeeded.
- Architecture: same-origin session/CSRF → owned admission → durable workflow →
  bounded evidence → typed cited report → owner Update → unique mock ledger.
- Dependencies: no new application frameworks. Container images provide JDK/JRE
  25, Node 24, unprivileged nginx and official Temporal CLI 1.9.1.
- Verification: final backend clean verify 83 tests; MCP clean verify 2;
  frontend production build; three explicit real integration smokes passed (88
  total test executions, no failures/errors/skips). Six containers healthy,
  fresh V1–V6 schema/history/constraints validated, anonymous APIs rejected.
  Real backend process restart resumed approval and executed one mock action.
  See capstone-verification.md for commands, errors/fixes and credential limits.
- Final review: durable start intent commits atomically with the investigation;
  required evidence failures remain readable; lifecycle/admission stay in services,
  constructor injection preserved; nginx refreshes Docker DNS after replacement.
- Limits: real provider credentials absent; optional live calls not claimed.
- Next: final consolidated verification/report; no unrelated features.
