# Checkpoint 4 — Spring Security foundation

> Historical security baseline. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) documents
> current Google OIDC, investigation ownership and frontend CSRF behavior;
> both local and container HTTP profiles now override Secure cookies to false.

This records the Checkpoint 4 baseline. Checkpoint 5 extends it with OIDC and
`/api/me`; see [authentication](authentication.md) and
[current verification](checkpoint-5-verification.md). No-login/deferred
descriptions below refer to the earlier checkpoint.

## Scope and paths

The servlet application uses a lambda-configured `SecurityFilterChain` with
Spring Boot 4.1.1's managed Spring Security 7.1 dependencies. There is no login
mechanism yet. Boot's `UserDetailsServiceAutoConfiguration` is excluded to avoid
creating a generated in-memory user. Form login and HTTP Basic are disabled.
Security configuration loads only for servlet applications, leaving the
non-web PostgreSQL integration test context unchanged.

| Request | Policy |
| --- | --- |
| GET `/api/health` | Public application smoke test. |
| GET `/api/csrf` | Public CSRF/session bootstrap, including before authentication. |
| GET `/actuator/health` | Public aggregate health, details remain hidden. |
| GET `/actuator/health/liveness` | Public minimal liveness health. |
| GET `/actuator/health/readiness` | Public minimal readiness health. |
| Other `/api/**` | Authentication required. |
| POST `/logout` | Spring's logout filter handles it; valid session CSRF required. |
| Other requests | Denied; there are no HTML login/logout pages. |

Internal ERROR dispatches are permitted so servlet error handling can produce
the original error response. This does not permit client requests to `/error`.
No actuator wildcard, additional management endpoints, or role policy is added.
`NullRequestCache` avoids saving denied API requests into sessions for a future
browser redirect. Framework security headers remain enabled.

## Session strategy

The backend owns servlet `HttpSession` state with `IF_REQUIRED` session creation.
Health and ordinary denied GET requests do not need a new session. Reading the
CSRF token intentionally creates a session if necessary. Session timeout is
explicitly 30 minutes of inactivity. Session fixation uses `changeSessionId()`;
the future authentication filter will invoke the framework session strategy.
Authentication is simulated only in tests; the browser cannot log in yet.

`JSESSIONID` is host-only, HttpOnly, SameSite=Lax, and path `/`. Secure defaults to
true; only `application-local.yml` overrides it to false for loopback HTTP.
Sessions use cookie-only tracking, avoiding session identifiers in URLs. No
Redis session store or persistent servlet session storage is introduced.
Backend restart therefore ends local sessions. An HTTPS deployment must keep
Secure=true and deliberately configure any trusted proxy/TLS termination.

## CSRF contract

CSRF remains enabled with `HttpSessionCsrfTokenRepository`. The default XOR
request handler retains BREACH masking. `GET /api/csrf` returns only:

```json
{"token":"<masked-current-token>","headerName":"X-CSRF-TOKEN"}
```

The response uses `Cache-Control: no-store`. The framework token resolver handles
submitted headers; there is no custom token framework or readable CSRF cookie.
The endpoint only materializes the current token and maps it to a small DTO.

In the next checkpoint, React should fetch this endpoint with its session cookie,
keep the token in memory, and send the returned header on unsafe requests. Fetch
again after authentication and logout: Spring's authentication/logout strategies
clear the previous token. Masked token strings can change between GET requests
without changing the underlying session token; clients must not interpret each
different masked value as a session rotation. No frontend token handling exists
yet, and no authentication token belongs in localStorage.

Cookies are automatically attached by browsers. A malicious site may therefore
cause a browser to submit an authenticated state-changing request. HttpOnly
prevents JavaScript reading the session cookie, but does not prevent the browser
sending it. CSRF requires a token associated with the session that an unrelated
origin cannot read. SameSite complements this protection; it does not replace it.

## Status codes and logout

Authentication-required API requests return 401 with no login redirect, including
when the client accepts HTML. Authenticated access denial remains 403. Invalid or
missing CSRF also returns 403; Spring's CSRF filter runs before final authorization,
so an anonymous unsafe request missing CSRF returns 403, while the same request
with valid CSRF reaches the authentication check and returns 401. No stack traces
or security exception messages are returned by the configured entry point.

POST `/logout` validates CSRF, invalidates the session, clears the security context
and session cookie, and returns 204 without a redirect. GET `/logout` does not log
out or display a confirmation page. Spring's logout filter precedes authorization,
so a valid CSRF logout can also clear an anonymous session. There is no Google
logout or custom logout controller.

## Before a controller runs

```text
React / browser request
        ↓
Spring Security filter chain
        ↓
Restore identity from the session, if present
        ↓
Validate CSRF for unsafe methods
        ↓
Handle POST logout, if matched
        ↓
Require authentication / apply authorization rules
        ↓
Controller, only when the checks permit it
```

Authentication, CSRF, and authorization are separate checks. The chain can stop a
request before any controller exists or executes. The conceptual authentication
check does not imply final authorization precedes CSRF in the actual filter order.
This is why the production `/api/protected-probe` request returns 401 even though
no such controller was added.

## Tests

`SecurityRequestTest` is a focused `@WebMvcTest` with Spring Security enabled.
Protected endpoints and principals are test fixtures only, never production API
or login functionality. The slice explicitly excludes generated user configuration.
Tests cover:

- Public health without authentication/CSRF.
- 401 without HTML or redirects; authenticated protected GET access.
- Authenticated 403 access denial.
- Unsafe requests rejected for missing or invalid CSRF.
- Unsafe requests accepted with valid CSRF and authentication.
- Anonymous unsafe request ordering and CSRF not granting authentication.
- Anonymous CSRF bootstrap and a real endpoint token submitted as a header.
- CSRF-protected logout, session invalidation, cookie deletion, stale-token
  rejection, and fresh-token availability.
- GET logout refusal and absence of generated user details.

The existing 11 PostgreSQL integration tests and all persistence code/migrations
are unchanged. Test dependencies add Boot's MVC and Security test starters; the
only new production dependency is `spring-boot-starter-security`.

## Verification environment and commands

The initial `java -version` and Maven Wrapper used Oracle Java 23; JAVA_HOME was
unset. The project remains Java 25. Verification reused the existing local
Temurin JDK 25.0.4.1 at:

```text
C:\Users\Emre\AppData\Local\Temp\ai-incident-orchestrator-jdk25\jdk-25.0.4.1+1
```

It was selected through process-local JAVA_HOME/PATH only. No new JDK download,
global installation, or project downgrade. Maven Wrapper is 3.9.16; Docker engine
is 29.8.2 with Compose 5.5.1 from the existing user Docker Desktop installation.

Commands included Java/Maven environment inspection, `mvnw.cmd test`,
`mvnw.cmd --batch-mode --no-transfer-progress verify`, isolated Compose
`config --quiet`, `up -d --wait`, and `ps`, followed by the executable JAR with
`--spring.profiles.active=local` and HTTP health/protected/CSRF/logout requests.

The first test run passed its security tests but failed the non-web persistence
context because HttpSecurity is unavailable outside servlet applications. Adding
`@ConditionalOnWebApplication(type=SERVLET)` fixed that without changing persistence
tests. The initial MVC slice also generated Boot's test user despite the main
application exclusion; its explicit slice exclusion and regression test fixed
that. An intermediate verification run was interrupted before retrying the fixes.

Final Maven verify: **26 tests, 0 failures, 0 errors, 0 skipped** — 15 security
request tests plus 11 PostgreSQL Testcontainers tests. Compilation and executable
JAR packaging passed. Existing non-fatal Mockito dynamic-agent warnings remain;
expected PostgreSQL constraint warnings are negative-path test coverage.

Live startup against an isolated project using the unchanged Compose file also
passed. The unchanged Vite frontend's health proxy also returned UP.
Application health and all three allowed Actuator health endpoints
returned 200. An unauthenticated protected API probe returned 401 without a
Location header. CSRF bootstrap returned the expected DTO with no-store and a
JSESSIONID cookie verified as HttpOnly, SameSite=Lax, path `/`, Secure=false.
Logout without CSRF returned 403; valid-CSRF logout returned 204 and removed the
client's session cookie. An old CSRF token was rejected with 403, and fetching a
new token succeeded. The servlet serialized cookie deletion using a past Expires
date; an initial verification assertion incorrectly required a Max-Age header
and was corrected to verify actual expiration/removal.

The packaged JAR contains Spring Security **7.1.1** and no test controller.
Verification reused temporary credentials outside Git and project name
`ai-incident-orchestrator-checkpoint4-verify`, preserving any existing developer
database. Verification processes and that disposable project's containers,
network, volume, and credential file were removed afterward.

## Deferred work

No Google OAuth/OIDC, OAuth client dependency, JWT/resource server, passwords,
production users or login endpoints, roles, user CRUD API, frontend login/token
storage, Redis behavior, or AI orchestration. Work stops at Checkpoint 4.

## References

- [Spring Security session-backed CSRF and token refresh](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security logout behavior](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- [Spring Boot security auto-configuration](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
