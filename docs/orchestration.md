# Deterministic orchestration

> Checkpoint 11 guide retained for orchestration detail. See
> [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the canonical current system.
> Temporal durable waiting is now implemented; synthesis permits two attempts
> total, and read retries belong to EvidenceCollector rather than ToolExecutionService.

Classification → Java route → required-first selection → parallel read-only
collection → fan-in/bounded evidence → typed synthesis/citation validation →
conditional owner approval → mock ledger action/report. Transactions are short;
external calls happen outside them. Executors have four workers and 32 queued
tasks. Each tool has a two-second deadline, one retry on timeout/unavailable,
and collection has a six-second total deadline. Missing required evidence fails
the investigation; optional documentation failure yields a degraded report.

Synthesis uses only selected, budgeted evidence with no extra tools. The separate
bounded tool-calling demonstration remains available behind the model boundary.
Invalid synthesis retries twice then a provider-neutral conservative fallback
returns manual review and NONE. This does not fabricate a second provider.
OpenAI transport has an explicit 20-second timeout. No arbitrary mutation retries.

Only the owner can approve/deny via CSRF-protected POST. Pending approval is
persisted. The single mock action creates a unique PostgreSQL ledger row and
marks completion in one transaction. Repeated execution cannot duplicate the
logical action. There is no real restart command. Optimistic versioning detects
conflicting decisions. Temporal will provide the durable wait/deadline next.

Circuit breaking is deliberately deferred: the current adapters are simulations
and one small optional MCP corpus. Bounded timeout/retry and explicit degraded
evidence suffice here; when real traffic/outage measurements justify suppression,
a proven breaker can wrap that external adapter without changing tool policy.
Java owns every transition, limit, authorization and approval; no multi-agent
runtime is needed.
