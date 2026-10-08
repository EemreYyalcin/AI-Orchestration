# Production AI controls and evaluation

> Specialized controls guide; [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) is the
> canonical current architecture, including exact metric coverage, configuration
> precedence, approval policy and telemetry limitations.

Prompts are allowlisted classpath resources with explicit IDs and v1 versions;
classification, synthesis and tool investigation have separate system policies.
Evidence is user data, never instructions. Java enforces owner/tool allowlists,
typed inputs, no arbitrary SQL/URLs/code, budgets, citation validation and mock
action approval. This constrains impact; it cannot perfectly solve injection or
guarantee a model's conclusions are true.

Micrometer measures tool calls/duration/failure, retries/timeouts, investigation
terminal count/duration, approval waits and model-stage observations including
provider/model/prompt version. Spring AI 2.0.1's actual model observations record
provider token usage where supplied (`gen_ai.client.token.usage` input/output;
total usage in observation metadata). Simulation has no token usage, not zero
invented tokens. No money estimate or permanent pricing table is implemented.
See [official Spring AI observability](https://docs.spring.io/spring-ai/reference/observability/index.html).

Boot OpenTelemetry instruments HTTP; official Temporal OpenTracing interceptors
use an OTel shim for workflow/activity propagation. Micrometer context snapshots
carry scopes into parallel tool executors; MCP adds W3C propagation headers and
an explicit call observation. UUID correlation is high-cardinality trace data,
never a metric tag. Prompt/completion/tool content logging remains off. No
collector is required for local startup; configure an OTLP exporter separately
when a real telemetry collector exists. Metrics are in-memory per process;
persistent audit records are authoritative across restarts.

V6 records investigation, stage, workflow/prompt versions, typed selected tools,
outcome, decision and approved mock action metadata. No raw prompts, provider
tokens, secrets or sensitive raw logs are written to audit. The original user
question is stored as investigation input; deployment needs a retention policy
appropriate to its data classification.

Redis rate limiting is atomic INCR plus expiry in one Lua script, scoped to the
authenticated internal UUID. Default admission is five starts per one-minute
window beginning at the first request; rejected requests do not extend expiry.
Set `INVESTIGATION_RATE_LIMIT_ENABLED=true` with Redis configured (enabled by
the container stack). Redis failure fails admission closed with 503, protecting
provider cost. Existing reads and approval continue; authentication remains
HttpSession. Create and explicit run/retry are charged; internal durable dispatch
retries are not. Counters are ephemeral; Redis restart resets the window.

No documentation cache yet: the tiny local corpus does not justify stale copies.
No caching of live logs or metrics. A future versioned corpus cache can be added
at the adapter boundary with an explicit TTL and staleness contract.

The deterministic evaluation TSV covers pool exhaustion, dependency timeouts,
null pointers, latency degradation and ambiguity. Contracts check classification,
Java route, report structure, citations and constrained capability inputs;
separate authorization/schema tests reject unknown tools and malicious arguments.
Live-model quality evaluation remains optional and cannot be inferred from the
offline fixture. Real PostgreSQL, Redis, MCP and Temporal tests cover mechanisms.
