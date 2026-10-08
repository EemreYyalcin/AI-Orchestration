# AI orchestration foundation — Checkpoint 6

> Historical creation/read foundation. Execution, AI/MCP/Temporal and additional
> migrations have since been implemented; see [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md)
> for the canonical current architecture.

An LLM is a semantic reasoning component: it receives bounded context and
generates classification, hypotheses and conclusions. It does not own the
investigation lifecycle. A tool is a typed capability such as searchLogs,
queryDatabase, searchDocumentation or inspectMetrics. Java executes tools;
the model only requests them.

A workflow defines reliable operations:

```text
Classify → select evidence sources → collect evidence → analyze → report
```

The orchestrator coordinates that lifecycle in Java, owning authorization,
transitions, persistence, retries, timeouts and approval policy. An agent is a
runtime pattern where a model repeatedly selects actions based on earlier
results. The whole investigation must not be an unrestricted agent loop:
semantic suggestions cannot redefine permissions, execute arbitrary SQL or
URLs, mutate infrastructure, or create unlimited work. Ordinary Java workflow
logic is the default whenever it supplies reliable control.

Checkpoint 6 adds only persisted investigation creation/read. UUID identity,
owner UUID FK to app_users, a bounded original question, enum status and UTC
Instant timestamps live in incident_investigations. Flyway V2 owns the schema;
Hibernate still validates it. There are no serialized Java objects.

POST /api/investigations derives owner identity from ApplicationPrincipal,
requires authentication and CSRF, and returns a no-store DTO with Location.
GET /api/investigations/{id} queries by id AND owner: absent and foreign-owned
records both return 404. Clients cannot set ownership. Application services own
transactions; controllers only adapt HTTP. Input is limited to 4000 characters.

No LLM dependency or call is added here. Status values reserve the user-specified
lifecycle but no speculative transition engine is implemented. Subsequent
checkpoints introduce orchestration only when needed. No long-term memory yet.
