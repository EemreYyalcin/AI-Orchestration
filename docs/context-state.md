# Context, state and memory — Checkpoint 10

> Checkpoint-specific context guide. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) describes
> the current complete system. The final paragraph's future Temporal work is now
> implemented; source ranking means required-first ordering, not semantic ranking.

ContextBuilder selects allowed sources with required evidence first and bounded
tool count. Evidence is typed, source-labelled and cited by stable ids. Raw
results are sanitized before truncation in tool execution (including model tool
loops), filtered by selected sources, then constrained by per-source, total
character and item budgets. Control characters, common key/password/token forms
and email addresses are redacted. This is defense in depth, not complete secret
detection or perfect prompt-injection protection.

Default configurable bounds under app.context: 12 items, 2000 characters per
tool result/source, 8000 evidence characters total, 4 exposed tools. These are
character/item limits, not token counts. Provider token usage must be recorded
separately. Fixed source/citation formatting adds bounded overhead to rendering;
the original question has its own 4000-character cap. Missing sources have typed
failure records; truncation/degradation are explicit.

Request state is HTTP/session identity and CSRF. Investigation state is the
persisted status, typed classification and sanitized evidence. Conversation
context is transient messages for one bounded model call/loop. Long-term memory
would retain knowledge across independent interactions: **NO LONG-TERM MEMORY
YET** because investigations can use their own evidence and packaged runbooks.
No prompts, raw logs, provider tokens or serialized Java objects are persisted.

Flyway V3 adds JSONB classification/evidence and optimistic versioning. JPA maps
typed records; transactions persist phases without enclosing external calls.
Explicit lifecycle transitions reject illegal/terminal moves. Only owner reads
expose safe persisted state. Durable scheduling/recovery is added in Checkpoint
12; current persistence alone does not claim a running workflow resumes itself.
