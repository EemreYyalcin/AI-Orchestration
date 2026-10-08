# Foundation architecture

> Historical foundation scope through Checkpoint 5. The canonical current system
> is [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md). The host-only application topology,
> unused Redis and user-only schema below have since evolved into the capstone's
> six-service stack, admission control and V1–V6 schema.

## Current boundaries

The monorepo has `/backend`, `/frontend`, and `/docs`, plus root Compose and
environment configuration. The existing workspace directory is the repository
root; there is no extra nested repository directory. Backend packages use
`dev.orchestrationlab.incident`. Alongside the application entry point and health
controller, the `user` feature now contains application, domain, and repository
packages. There are no empty future-feature packages.

The backend uses Java 25, Spring Boot 4.1.1, Maven Wrapper 3.9.16, and MVC.
Boot's parent manages Spring, database-driver, and Flyway dependency versions.
The frontend uses React, TypeScript, Vite, native fetch, and an npm lockfile.
Frontend versions are exact pins selected from the npm registry, with compatible
Node 24 and npm 12 engines. Frontend authentication uses native fetch and React
state with no extra state/authentication library. A small OIDC infrastructure
adapter, application principal and current-user service provide authentication;
see [authentication](authentication.md).

## Decisions

| Decision | Problem solved and reason | Alternative deferred |
| --- | --- | --- |
| Single repository, independent builds | Keep related work and documentation together without coupling npm to Maven. | Separate repositories or root build orchestration add coordination overhead. |
| MVC and JPA | Familiar synchronous HTTP and JDBC foundation. | WebFlux does not help a blocking persistence stack. |
| Applications on host, infrastructure in Docker | Fast IDE debugging and Vite hot reload with reproducible databases. | Application containers are a later checkpoint. |
| Relative API URL and Vite proxy | One browser-facing origin for local requests. | Separate browser origins would require CORS and later complicate session setup. |
| Hibernate validation, Flyway enabled | Prevent unreviewed entity-driven schema changes. | Hibernate create/update is unsuitable for a migration-owned schema. |
| Minimal user persistence | Provision a trusted Google subject into an internal UUID during login. | CRUD and account linking are unnecessary for this use case. |
| Session + OIDC adapter | Use framework validation and keep Google types outside application use cases. | Application JWTs and a browser Google SDK add unnecessary responsibilities. |
| Redis configured, excluded from readiness | Verify connectivity without making requests depend on an unused service. | Redis caches and shared sessions require actual use cases. |
| PostgreSQL named volume, Redis persistence off | Preserve durable database state without pretending Redis has durable data. | Redis volumes/AOF become relevant only after a storage responsibility exists. |
| Loopback ports and environment credentials | Limit local network exposure and keep credentials out of source control. | Public bindings and deployment-grade secrets delivery belong to a deployment decision. |

PostgreSQL uses `postgres:18`; Redis uses `redis:8`, never `latest`. Major tags
receive patch updates on pull; they do not guarantee byte-for-byte image
reproducibility. PostgreSQL 18's named volume mounts at `/var/lib/postgresql`,
matching the version-specific data layout of its official image. Redis requires
an environment-provided password. Redis health checks use `REDISCLI_AUTH` and
require an actual `PONG`, so an authentication error cannot pass the check.

The local PostgreSQL initialization account is also the application's account
for this development checkpoint. Separate migration/runtime privileges remain
a deployment hardening decision.

Flyway owns `app_users` through `V1__create_app_users.sql` and its schema-history
metadata. Hibernate validates and maps that schema without generating DDL.
Open Session in View is disabled. Spring Data Redis
repository scanning is disabled because there are no Redis repositories.

Only Actuator health is exposed, without details or components. Aggregate health
includes Redis connectivity; readiness includes PostgreSQL only. The custom
`/api/health` endpoint reports that the HTTP application can respond, not that
all dependencies are healthy. The frontend checks it on mount, times out after
five seconds, rejects non-success responses, and displays DOWN for malformed
responses or failures. No polling or business services are introduced.

## Future direction, not implementation

The user feature uses an `ExternalIdentity` input record, `UserProvisioningService`,
an `AppUser` JPA entity, `UserProvider` enum, and Spring Data repository.
Business logic stays out of controllers. No speculative interfaces, adapter
layers, or feature placeholders have been introduced. See [persistence](persistence.md).

Spring Security now provides session access rules, a CSRF bootstrap endpoint,
and POST logout. See [security](security.md) for paths, cookies, filter ordering,
and verification. Google OIDC login is backend-owned with server-side sessions;
its adapter provisions an internal user before saving authentication. Google
registration uses environment credentials under the explicit `google` profile.
See [authentication](authentication.md) for framework protocol handling,
principal/DTO boundaries, frontend flow and manual Console setup. No secrets are
committed and no application JWT or Google API token store is introduced.

## References

- [Boot 4.1.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Boot starter catalog](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [PostgreSQL official image and volume layout](https://hub.docker.com/_/postgres)
- [Redis official image](https://hub.docker.com/_/redis)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [Vite requirements](https://vite.dev/guide/)
