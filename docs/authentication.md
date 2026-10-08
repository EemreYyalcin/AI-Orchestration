# Checkpoint 5 — Google OIDC authentication

> Specialized authentication/setup reference. For the canonical current system,
> read [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md). The deferred incident/AI/MCP/Temporal
> scope at the end describes Checkpoint 5, not the implemented capstone.

## Local setup and Google Console

The browser always uses **http://localhost:5173**. Vite proxies `/api`, `/oauth2`,
`/login/oauth2`, and `/logout` to **http://localhost:8080**. The callback has an
explicit browser-facing URI; it is not derived from Vite's upstream Host header.
Do not browse to the backend or switch hostnames during login.

1. In [Google Cloud Console](https://console.cloud.google.com/), select/create a
   project. Open Google Auth Platform (the OAuth consent screen).
2. Configure Branding: application name, support email, developer contacts.
   Configure Audience: External/Testing for personal local development, adding
   your testing Google accounts under Test users. Internal is suitable only for
   an eligible Workspace organization.
3. Under Data Access request only `openid`, `profile`, and `email`. No additional
   Google APIs, offline access, or refresh-token grants are needed.
4. Under Clients create an OAuth client of type **Web application**. Set its
   **Authorized redirect URI** to exactly:

   ```text
   http://localhost:5173/login/oauth2/code/google
   ```

   No trailing slash, port 8080, or alternate hostname. Authorized JavaScript
   origins are not required for this backend authorization-code flow; there is
   no browser Google SDK. The redirect URI is required.
5. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the backend environment
   or ignored root `.env`, loaded as described in README. Replace template
   placeholders; never commit values or use a `VITE_` prefix.
6. Start infrastructure using README. In backend, after loading root `.env`, run
   with JDK 25:

   ```powershell
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,google'
   ```

   In the IDE select JDK 25, profiles `local,google`, and supply the infrastructure
   and Google environment variables. Start `npm run dev` in frontend and open
   `http://localhost:5173`.

`local` alone deliberately remains usable without Google credentials: health,
CSRF and persistence start, protected APIs stay protected, and login is not
configured. `/oauth2/authorization/google` then has no login handler (404).
Activate `google` to register the client; missing credentials fail startup rather
than substituting fake values. Tests use an explicitly test-only loopback
provider. This profile is a configuration boundary, not a second authentication
mechanism. Current fixed redirects are for local development; public deployment
requires HTTPS and approved deployment URLs reviewed together.

## Framework and application flow

```text
Browser / React at localhost:5173
    ↓ top-level navigation /oauth2/authorization/google, via Vite
OAuth2AuthorizationRequestRedirectFilter
    ↓ creates authorization request, state and nonce; saves request in HttpSession
Google login / consent
    ↓ code + state to localhost:5173/login/oauth2/code/google, via Vite
OAuth2LoginAuthenticationFilter
    ↓ retrieves saved authorization request and matches state
Framework authorization-code exchange (backend → Google token endpoint)
    ↓ ID-token signature, issuer/audience/time and OIDC nonce validation
OidcUserService (framework; UserInfo loading when applicable)
    ↓ validated OidcUser
GoogleOidcUserService (our adapter)
    ↓ ExternalIdentity(GOOGLE, sub, email, name, picture)
UserProvisioningService → AppUserRepository → PostgreSQL
    ↓ transaction commits; existing/new internal UUID
ApplicationOidcUser
    ↓ successful Authentication; session fixation protection; previous CSRF cleared
SecurityContext saved in HttpSession
    ↓ fixed redirect http://localhost:5173/
React → GET /api/me → safe DTO; GET /api/csrf → fresh token
```

The framework performs the protocol. The adapter does not exchange codes, verify
JWTs or check state/nonce itself. Framework external I/O finishes before the
existing transactional provisioning service begins. Its PostgreSQL identity
constraint and bounded fresh-transaction retry remain unchanged.

The adapter accepts only the configured `google` registration. OIDC `sub` is an
opaque external subject. Email is nullable profile information, never an account
linking key. `ApplicationPrincipal.getUserId()` exposes the internal UUID without
OIDC imports in application/controller code. `ApplicationOidcUser` implements
that interface and delegates framework accessors at the authentication boundary;
its `getName()` returns the UUID, distinct from `getSubject()`.

Provisioning exceptions become an `OAuth2AuthenticationException` before the
login filter saves authentication. Failures redirect to the fixed
`http://localhost:5173/?login=failed`, showing only a generic message. Success
always redirects to `http://localhost:5173/`. Neither request redirect parameters
nor saved destinations can override these URLs. Provider/DB details and stack
traces are not exposed to the frontend. Avoid sensitive security debug logging
in deployed environments.

## Storage, API, React and session boundaries

The `app_users` schema is unchanged: no tokens or raw claims. An access token is
temporarily needed for framework UserInfo loading;
`DiscardingAuthorizedClientRepository` does not retain the authorized client
afterward because the app does not call Google APIs. No token store, JDBC
authorized-client service, refresh grant or SDK was added. The OIDC principal
contains framework ID-token/claims data in the server's in-memory session, never
in PostgreSQL or `/api/me`. The protocol ID token is not an application bearer
token; our API authenticates the session cookie.

`GET /api/me` requires an `ApplicationPrincipal` and uses `CurrentUserService`
to read by internal UUID in a read-only transaction. Its no-store DTO contains
only `id`, `email`, `displayName`, `avatarUrl`. Missing/deleted users or other
principal types return 401. No entity, provider subject, token or session id is
serialized. Unauthenticated `/api/**` still returns 401 without OAuth/HTML
redirects, even with Accept: text/html. Only the two exact OAuth GET paths are
added to the approved public endpoints.

React calls `/api/me` first. Only 401 means unauthenticated; connection failures,
other statuses, malformed responses and CSRF bootstrap failures show a separate
error/retry state. The Google button navigates the top-level browser. Profile
strings are rendered as React text; avatars allow HTTPS only and suppress the
referrer. No browser tokens, SDK, router or state library were added.

Login changes the session identifier and clears pre-login CSRF state. React
fetches fresh `/api/csrf` after its return. Logout fetches the current masked
token, POSTs it in the returned header to `/logout`, clears UI state only after
204, then bootstraps new anonymous CSRF state. Failed logout confirmation is an
error, not assumed success. Logout invalidates HttpSession and clears JSESSIONID;
old CSRF tokens fail. No Google-account logout is requested. HttpOnly,
SameSite=Lax, cookie-only tracking, 30-minute idle timeout, and local-only
Secure=false remain. No CORS/CSRF bypass was added.

## Verification and manual acceptance

Automated tests need no real Google credentials. See
[executed verification](checkpoint-5-verification.md).

- `GoogleOidcUserServiceTest`: claim mapping, separate UUID/subject, failed provisioning.
- `OAuthSecurityFlowTest`: servlet filters with loopback token/JWKS/UserInfo
  endpoints and RSA-signed test tokens; exact callback/scopes, state, nonce,
  signature rejection, safe `/api/me`, fixed redirect, session fixation, CSRF
  reset, logout and provisioning failure without an authenticated session.
- `UserPersistenceIntegrationTest`: adapter uses the existing service against
  real PostgreSQL; repeat identity returns the same UUID without duplicates,
  alongside existing migration, uniqueness and concurrency coverage.
- Existing Checkpoint 4 security tests remain intact.

After real Google setup, manually verify:

1. Anonymous `/api/me` is 401; sign in with a configured test account.
2. Return through the exact Vite callback; profile and internal UUID appear.
3. Refresh retains the session. Logout makes `/api/me` 401. Sign in again:
   same UUID and one PostgreSQL row for the subject.
4. Cancel Google consent: only the generic failure message appears.
5. Inspect responses: no tokens/subject in `/api/me`; fresh CSRF after login;
   logout without CSRF fails, and valid POST logout succeeds.

The automated provider validates framework configuration and application
boundaries; it cannot verify real Google Console settings, consent or accounts.

## Deliberately deferred

Application JWT/bearer auth, resource server, Redis sessions/caching, roles,
teams/tenants, CRUD, account linking, additional providers, AI/LLMs/agents,
MCP/Temporal, incidents and orchestration remain outside Checkpoint 5.

## References

- [Spring Security OAuth2 Login configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html)
- [Spring Security CSRF lifecycle](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Google OIDC setup and protocol](https://developers.google.com/identity/openid-connect/openid-connect)
