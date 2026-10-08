# Run the capstone

> Operational capstone guide. Read [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for
> the canonical current architecture and [capstone verification](capstone-verification.md)
> for dated executed checks; described startup does not itself prove current health.

Copy `.env.example` to `.env`, replace infrastructure passwords/database names,
and run `docker compose config --quiet`, then `docker compose up --build -d --wait`.
Default profiles `container,temporal,mcp` run the visibly labelled offline model
without provider credentials. Backend, MCP, PostgreSQL and Redis are private
services; localhost publishes frontend 5173, development PostgreSQL/Redis and
Temporal 7233/UI 8233. Remove developer DB/Redis/Temporal ports in a deployment.
All application containers and Temporal run as non-root. PostgreSQL and Temporal
have named volumes; Redis admission counters are deliberately ephemeral.

To sign in through the existing Google flow, configure Google credentials and
set `BACKEND_PROFILES=container,temporal,mcp,google`. Add `ai` only with a real
`OPENAI_API_KEY` and account-supported `AI_CHAT_MODEL`. Callback remains
`http://localhost:5173/login/oauth2/code/google`; success/failure redirects are
fixed. The local container cookie is HTTP-only/SameSite=Lax but not Secure for
localhost HTTP. Production TLS must use Secure cookies and correct origins.
No fake login endpoint was added. Credential-free tests authenticate only inside
the test harness; normal application runtime still requires Google for login.

Authenticated POST `/api/investigations` validates CSRF/admission/input, commits
the owned investigation and requests a durable start. It returns 201 only after
Temporal accepts the stable workflow ID. When Temporal cannot acknowledge start,
it returns 503 with the saved investigation DTO and Location; the bounded
dispatcher retries. Read that ID instead of creating another investigation.
Owner POST `/{id}/run` renews a stalled start; GET `/{id}` reads the projection.
POST `/{id}/approve` or `/deny` accepts an owner decision via durable Update;
refresh/poll for the committed final projection. GET cannot mutate approval.

The intentionally small React UI submits questions, polls status, shows a typed
report and pending mock action, and sends CSRF-protected decisions. It renders
model text as React text, never HTML, and stores no provider tokens. New requests
use bounded browser deadlines; server execution continues independently.

Failure policies: model malformed/unavailable retries boundedly then returns
explicit conservative degraded conclusions and no action; optional tool/MCP
failure is visible in evidence, required evidence failure retries Activity then
marks FAILED; database failure cannot claim persistence; Temporal failure cannot
claim start/decision acceptance; approval expires after configured one hour.
Start dispatch stops after twelve attempts, remains visibly CREATED, and needs
owner retry. Optimistic conflicts return 409. Real production alerts, retention,
backup, TLS/private-network enforcement and worker-safe versioned deployment
are operational work, not supplied by this local development stack.

Verification commands (JDK 25): backend `mvnw.cmd clean verify`; frontend
`npm run build`; MCP `../../backend/mvnw.cmd clean verify`; Compose config/build/up.
Optional explicit smokes: backend `mvnw.cmd -Dtest=TemporalRestartSmoke test`
against local Temporal; `-Dtest=McpInvestigationSmoke test` against MCP localhost
8081. These smoke classes are excluded by normal Surefire naming and are run
explicitly when those services exist. Standard integration tests use real
PostgreSQL/Redis, real MCP SDK server/client and supported Temporal test server.

`-Dtest=ContainerStackSmoke test` is a separate explicit local-stack smoke with
POSTGRES_DB/USER/PASSWORD loaded from the ignored `.env`. It seeds disposable
database fixtures, waits for real MCP evidence/approval, restarts the backend
container, approves via the private Temporal client and verifies one mock action.
It removes only its own database fixtures. API authentication/CSRF/ownership are
verified separately by DurableApiIntegrationTest; no fake runtime login exists.
Nginx resolves the backend through Docker DNS, including after container replacement.
