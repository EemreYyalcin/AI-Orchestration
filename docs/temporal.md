# Durable investigation execution

> Specialized Temporal guide. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is the
> canonical current architecture, including start dispatch, profile differences,
> failure-projection limits and deterministic workflow rules.

Official Java SDK/starter/testing **1.40.0** selected after checking
[Java releases](https://github.com/temporalio/sdk-java/releases) and
[Spring Boot integration](https://docs.temporal.io/develop/java/integrations/spring-boot-integration)
(Boot 4 supported). Use the `temporal` profile; explicit worker registration
serves task queue `incident-investigations-v1`. Workflow ID is
`investigation-<UUID>` with duplicate reuse rejected.

The workflow only sequences Activities and a durable `Workflow.await` timer.
Classification, tools/MCP, synthesis, all PostgreSQL projections and mock ledger
writes are Activities. History records Activity outcomes; replay does not redo
completed external work. Each Activity has three attempts, one-to-three-second
retry intervals, three-minute execution and ten-minute overall deadlines.
The complete execution has a two-day upper bound; approval defaults to one hour
(configurable up to one day). Timeout ends approval without running a restart.

Approval uses an **Update**: the caller gets acceptance/rejection instead of
fire-and-forget. A deterministic validator checks owner, pending phase and typed
decision. The accepted event is durable before resumption. Activity projections
may lag the acknowledged Update briefly; poll the API. Conflicting decisions
are rejected. No database/network calls occur in the handler. API ownership and
CSRF checks precede it. Temporal remains a private trusted infrastructure service.

Activity retries are safe: completed phases are detected in PostgreSQL, and
mock remediation inserts a unique investigation ledger row in the same
transaction as completion. The real action is deliberately absent.

A persisted start-request flag closes the save/start crash window; a dispatcher
reissues requests using the stable workflow ID. Temporal outage returns 503,
never a false successful-start claim. Dispatcher work per tick is limited to
ten requests and twelve attempts per start request. Exhaustion leaves a visible
CREATED investigation pending; explicit owner POST /run renews that request.
In production alert on stalled requests; there is no infinite hidden retry.

Official local service uses CLI **1.9.1** `server start-dev --ip 0.0.0.0
--db-filename /home/temporal/temporal.db` in the official image, with a named
volume. See [official CLI setup](https://github.com/temporalio/documentation/blob/main/docs/cli/setup-cli.mdx)
and [CLI releases](https://github.com/temporalio/cli/releases). This SQLite
development service preserves local history but is not a production deployment.
Application sessions remain local HttpSession and require re-login after restart.

Supported `TestWorkflowEnvironment` verifies success, retries, permanent failure,
durable approval deadline, pending queries, foreign-owner rejection, approve
and deny resumption. PostgreSQL tests verify action idempotency. Final stack
verification exercises a real service and worker restart when feasible.
