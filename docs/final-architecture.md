# Final architecture — Checkpoints 6–14

> Capstone architecture review retained as a checkpoint reference. Read
> [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the canonical current architecture,
> exact profile behavior, policy boundaries, nested retry limits and recovery limitations.

```text
React browser (same-origin cookie + CSRF)
    │ create/read/approve/deny
    ▼
Spring MVC + Spring Security + owner boundary
    │ Redis atomic admission limit
    ▼
Investigation application services ─────────────── PostgreSQL
    │ stable workflow ID / persisted start request   │ users, state, JSONB
    ▼                                               │ report, audit, action ledger
Temporal service + durable history                  │ Flyway owns schema
    │ task queue incident-investigations-v1          │ Hibernate validates/maps
    ▼                                               │
Java workflow / worker                              │
    ├─ classify Activity → provider-neutral model → typed semantic classification
    ├─ Java route / required-first context selection
    ├─ collect Activity → bounded parallel read-only tool policy/execution
    │      ├─ logs adapter (simulation)
    │      ├─ constrained database adapter (simulation, no free-form SQL)
    │      ├─ metrics adapter (simulation)
    │      └─ MCP client → private Streamable HTTP server → local runbooks
    ├─ sanitize / filter / budget / fan-in → persisted evidence
    ├─ synthesis Activity → Spring AI/OpenAI OR labelled offline model
    │      └─ typed schema / citation validation / conservative fallback
    ├─ conditional owner approval Update + durable deadline
    └─ idempotent mock action Activity → final report/projection

Micrometer / OTel: HTTP → Temporal → Activities → model/tools/MCP
Persistent metadata audit; no prompt/tool-content export by default
```

1. **Where is the LLM used?** Classification and evidence synthesis, plus a
   separately bounded tool-calling demonstration. The optional ai profile uses
   Spring AI 2.0.1/OpenAI; the default fixture labels every conclusion SIMULATED.
2. **Where does Java retain control?** Ownership, CSRF, input/schema/citation
   validation, tool policy, routing, concurrency, budgets, retries/deadlines,
   state transitions, persistence, admission and approval/action rules.
3. **How is context selected?** Required sources first, then approved optional
   sources. Default limits: four tools, twelve items, 2,000 characters per source
   result, 8,000 total evidence characters. Formatting is bounded overhead;
   the question separately has a 4,000-character bound. These are not tokens.
4. **How are tools selected?** Typed semantic categories map through
   WorkflowRoutingPolicy. Java adds required evidence/documentation and rejects
   tool sets outside the classification's approved route. Model schemas are
   restricted to those tools, and execution rechecks policy and owner.
5. **Where does MCP participate?** Documentation only: discovery plus the fixed
   searchDocumentation capability against packaged runbooks. Client request and
   initialization deadlines sit below the overall tool deadline.
6. **Why is MCP not the orchestrator?** It exposes a capability protocol. It
   does not own investigation state, ordering, retries, approval or lifecycle.
7. **Where is workflow state stored?** Temporal event history is execution
   authority; PostgreSQL stores readable phases, classification, sanitized
   evidence, final report, decisions, audit and the unique mock action ledger.
   Optimistic versioning detects concurrent projection updates.
8. **Do we need long-term memory?** NO LONG-TERM MEMORY YET. Independent incident
   investigations do not need a user-memory/vector store. Retained reports are
   investigation records, not an automatically replayed conversation memory.
9. **Why Temporal?** An approval wait must survive worker restarts, preserve
   completed Activity outcomes and resume safely. Ordinary HTTP request threads
   cannot supply durable timers/history. The local CLI SQLite service demonstrates
   this without claiming to be a production Temporal cluster.
10. **Where are retries?** Structured schema correction (one repeat), semantic
    validation (two attempts), read-only tools (one repeat), Temporal Activities
    (three attempts), stable-ID start dispatch (twelve attempts/request).
11. **Which retries are safe?** Read-only model/evidence calls are side-effect
    safe but can incur provider cost. Projection phases are replay-aware. Mock
    mutation uses a unique ledger row and atomic completion. Future real actions
    need their own idempotency protocol; arbitrary mutating tools are not retried.
12. **Where are timeouts?** Browser 10s, Temporal start/Update RPC overall 5s,
    OpenAI transport 20s, tool 2s, collection 6s, MCP request/init 1s, Activity 3m
    execution/10m overall, approval default 1h, workflow overall 2d. Failure policies
    are described in capstone.md; no infinite hidden approval wait.
13. **Where is human approval?** A cited proposal becomes persisted PENDING and
    WAITING_APPROVAL. Owner-only CSRF POST sends a validated durable Temporal
    Update. Timeout/deny complete without mock execution; approve writes the
    idempotent mock ledger. No real machine or service is restarted.
14. **How is tool authorization enforced?** Per-investigation trusted ToolContext,
    owner-scoped database lookup, route allowlist, typed allowlisted service/topic
    inputs and output bounds. Discovery or a model request grants no authority.
15. **How do we observe executions?** Micrometer logical counts/durations,
    model-stage observations, provider/model/prompt versions, Spring AI real token
    metadata, tool failures/retries/timeouts and approval duration. OTel HTTP,
    Temporal interceptors and executor scope propagation correlate tool/MCP
    spans. UUIDs appear only in traces/audit; raw prompts/results are disabled.
16. **How do we evaluate?** Five deterministic classification/routing scenarios,
    citations/report contracts, schema rejection/correction, malicious/unknown
    capabilities, ownership/CSRF, real PostgreSQL/Redis/MCP and Temporal tests.
    Optional live evaluation is needed to assess an actual model's quality.
17. **What if the provider changes?** Replace/configure the model adapter behind
    classification/reasoning/synthesis interfaces. Java workflow, tool policy,
    evidence and report contracts remain. Native structured output is opt-in
    only for a compatible configured model; schema validation remains required.
18. **What happens after a crash?** Durable start flags are redispatched with
    stable IDs; accepted histories replay on another worker. Completed Activities
    are not repeated on normal replay; retried external work is projection/action
    idempotent. PostgreSQL retains readable state. HttpSession is local, so the
    browser must sign in again after backend restart. Restore both persistent
    volumes together; backup/HA/versioned worker deployment remain operational work.
19. **How is prompt injection constrained?** Versioned system policy is separate
    from untrusted evidence; sanitization, bounded context, schemas/citations,
    no arbitrary execution, Java owner/tool policy and approval constrain impact.
    React escapes text. MCP is private and rejects browser Origin access. These
    controls cannot guarantee perfect injection resistance or factual accuracy.
20. **Why not one giant agent?** The graph is known, operations are independent
    read-only collection steps and the mutation is a single supervised mock.
    Java plus durable workflow handles reliability directly. An unrestricted
    model loop or multi-agent framework would add authority and failure modes
    without a demonstrated need.

Production-minded limits: simulated operational adapters; one private educational
runbook corpus; no real remediation; conservative fallback is explicit manual
review, not a second live vendor. No circuit breaker/cache without measured need.
Character bounds do not guarantee provider context-window fit. Redaction is
best-effort, not a complete data-loss-prevention system. Collector/export/alerts,
retention/backup/TLS, model-specific quality/cost controls and a production
Temporal deployment require deployment decisions. Current local topology runs
one backend/worker and local HttpSession; it does not claim horizontal-session HA.
