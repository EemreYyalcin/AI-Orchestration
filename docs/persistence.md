# Checkpoint 3 — Persistence foundation

> Specialized user-persistence foundation with dated Checkpoint 3 verification.
> The current schema now includes V1–V6 and investigation/audit/action tables;
> see [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for canonical current architecture.

## Schema and identity

Flyway applies `backend/src/main/resources/db/migration/V1__create_app_users.sql`:

| Column | PostgreSQL type | Constraint/meaning |
| --- | --- | --- |
| id | UUID | Primary key, application-generated internal identity. |
| provider | VARCHAR(32) | NOT NULL; enum persisted as text, initially GOOGLE. |
| provider_subject | VARCHAR(255) | NOT NULL; exact opaque external subject. |
| email | TEXT | Nullable profile information; no uniqueness constraint. |
| display_name | TEXT | Nullable profile information. |
| avatar_url | TEXT | Nullable profile information. |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL; creation instant. |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL; last persisted-change instant. |

`uk_app_users_provider_subject` enforces uniqueness on `(provider, provider_subject)`.
The enum value GOOGLE represents the one trusted Google issuer; an eventual
OIDC adapter must validate that issuer before constructing an ExternalIdentity.
No email linking, tokens, raw claims, roles, or other application tables exist.

## Packages

```text
dev.orchestrationlab.incident.user
├── application
│   ├── ExternalIdentity
│   └── UserProvisioningService
├── domain
│   ├── AppUser
│   └── UserProvider
└── repository
    └── AppUserRepository
```

The application input record has no Google SDK or Spring Security types.
Both the record and entity reject missing/blank/oversized subject values and a
null provider. Subjects are not trimmed, lowercased, or derived from email.
Optional profile fields are a current snapshot: null clears an old value.
Future adapters must deliberately construct that snapshot; partial profile
patches are not implied by this API.

## Flyway versus Hibernate

Flyway owns schema lifecycle through reviewed SQL migrations and records applied
versions in `flyway_schema_history`. Hibernate owns runtime ORM mapping and
validates the existing schema (`ddl-auto: validate`); it never creates or updates
tables. V1 has no database-generated UUID or timestamp defaults: JPA generates
UUIDs and entity callbacks set timestamps. Future SQL writers must supply them.

`Instant` models an absolute UTC instant. `@PrePersist` initializes both timestamps
from the same instant, while `@PreUpdate` changes updatedAt for a dirty entity.
Identical profile snapshots do not generate an update. PostgreSQL timestamptz
stores the instant, not the original timezone; display depends on session timezone.
Tests reload timestamps to account for PostgreSQL's microsecond precision.

## Provisioning and concurrency

```text
ExternalIdentity → UserProvisioningService → AppUserRepository
                                        → JPA / Hibernate → PostgreSQL

Flyway → PostgreSQL schema → Hibernate validation on startup
```

The service finds by provider plus subject, updates allowed profile fields on
the existing entity, or persists a new entity with a UUID. It flushes changes
inside the use-case transaction and returns after that transaction commits.
Identity fields and createdAt have no public setters and are not updatable in
the JPA mapping. Returned entities are not API response objects.

Two first requests can both observe an absent row. A pre-insert lookup alone
cannot prevent that race. PostgreSQL's unique constraint makes one insert win
and rejects the other. The service recognizes only SQLSTATE `23505` together
with constraint name `uk_app_users_provider_subject`, lets the losing transaction
roll back, and makes **one** new attempt. That attempt normally reloads the
committed winning row and returns the same UUID. A second failure propagates.
Unrelated integrity errors are not retried. Concurrent profile writes use the
last committed update; optimistic locking is not introduced at this stage.

`TransactionTemplate` uses `REQUIRES_NEW` for every attempt. This small explicit
boundary keeps retry handling outside the aborted transaction without another
service bean or self-invocation pitfalls. It also intentionally means provisioning
commits independently of any caller's surrounding transaction; callers should
treat it as a standalone use case, not part of a larger atomic workflow. Future
workflow composition should revisit this choice if needed.

Transactions belong at the application-service level because finding, creating,
or updating is one use case, independent of HTTP or authentication frameworks.
There are no network calls within it. The future OIDC adapter must finish network
requests and identity validation before calling this service.

## Integration tests

`UserPersistenceIntegrationTest` uses a Spring-managed PostgreSQL 18 Testcontainer
with `@ServiceConnection`. The container's lifecycle follows the application
context; no H2, local database credentials, or Redis container is required.
Missing Docker fails the suite rather than silently skipping it. Hibernate's
normal validation setting stays active while Flyway applies V1.

Coverage includes:

- Migration version/history, schema types, and the named unique constraint.
- New user persistence, independent UUID, repository lookup, and nullable profile.
- Returning user/profile changes with the same UUID and unchanged createdAt.
- Identical profiles leaving updatedAt unchanged; null snapshots clearing profile.
- Shared email with distinct, case-sensitive subjects.
- Duplicate identity and null identity fields rejected by PostgreSQL.
- Failed profile changes rolled back; unrelated constraints not retried.
- Concurrent direct inserts constrained to one row.
- A forced first-provisioning race recovered in one retry with the same UUID.
- A repeated real uniqueness violation stopping after two attempts.

The concurrency test uses a repository spy only to coordinate both initial reads;
queries and inserts still execute against PostgreSQL. One bounded-retry test
simulates missing lookup results while leaving both failed inserts real.

Run from `/backend`, with JDK 25 and a running Docker engine:

```powershell
.\mvnw.cmd --batch-mode test
.\mvnw.cmd --batch-mode verify
```

Inspect the local Compose database after starting the backend with the `local`
profile and environment described in the README:

```powershell
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\d app_users"'
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM flyway_schema_history;"'
```

## Scope

Frontend, Compose, Redis behavior, health endpoint, and authentication are unchanged.
No CRUD endpoints, security dependencies, OAuth, tokens, sessions, AI integrations,
generic base classes, mapping frameworks, or speculative infrastructure layers.
Work stops at Checkpoint 3.

## Verification results

Verified on 2026-10-07:

| Check | Result |
| --- | --- |
| Maven validate | Passed. |
| Compilation with default Java 23 | Failed with `release version 25 not supported`; Java baseline was preserved. |
| Maven verify with temporary Temurin JDK 25.0.4.1 | Passed, including main/test compilation and executable JAR packaging. |
| PostgreSQL Testcontainers integration suite | **11 tests, 0 failures, 0 errors, 0 skipped**, using PostgreSQL 18.6 and Testcontainers 2.0.5. |
| Fresh Flyway migration and Hibernate validation | Passed in both tests and local-profile startup. |
| Compose configuration validation | Passed. |
| Compose PostgreSQL/Redis health checks | Both healthy, loopback ports 5432/6379. |
| Local-profile executable JAR startup | Passed with the existing configuration. |
| Direct `/api/health` | HTTP 200, `{"status":"UP"}`. |
| Aggregate Actuator and readiness | Both UP. |
| Vite proxy `/api/health` | HTTP 200, `{"status":"UP"}`; frontend source unchanged. |
| Direct PostgreSQL inspection | Confirmed `app_users` columns, UUID primary key, named provider/subject unique constraint, and successful Flyway V1 history. |

The environment initially exposed Java 23 and no `docker` command on PATH.
An official, checksum-verified Temurin JDK 25 archive was extracted outside the
repository to `$env:TEMP\ai-incident-orchestrator-jdk25\jdk-25.0.4.1+1` and selected
only for verification processes. No global Java installation or default changed.
Docker Desktop was found at
`C:\Users\Emre\AppData\Local\Programs\DockerDesktop\resources\bin`:
engine 29.8.2, Compose 5.5.1. Adding that directory to the verification process's
PATH also resolved an initial `docker-credential-desktop` lookup error.

The first real test run had one concurrency-test error: Mockito cannot call an
abstract Spring Data finder through `callRealMethod()`. The spy was corrected to
use its original delegate answer. The subsequent complete `verify` run passed.
Expected uniqueness/check-constraint warnings in tests exercise negative paths.
Mockito also emits a non-fatal dynamic-agent warning under JDK 25; it did not
prevent compilation or the suite from passing.

Commands executed included:

```text
mvnw.cmd --batch-mode --no-transfer-progress validate
mvnw.cmd --batch-mode --no-transfer-progress test
mvnw.cmd --batch-mode --no-transfer-progress verify
docker version / docker compose version / docker ps
docker compose --env-file <temporary-file> -p ai-incident-orchestrator-checkpoint3-verify config --quiet
docker compose --env-file <temporary-file> -p ai-incident-orchestrator-checkpoint3-verify up -d --wait
docker compose --env-file <temporary-file> -p ai-incident-orchestrator-checkpoint3-verify ps
java -jar target/ai-incident-orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
HTTP requests to direct health, Actuator, readiness, and the Vite health proxy
docker compose exec ... psql: \d app_users; SELECT version, description, success FROM flyway_schema_history
```

For local startup verification, the existing Compose file ran under the isolated
project name `ai-incident-orchestrator-checkpoint3-verify` with generated credentials
in a temporary environment file outside Git. After verification the application
and Vite were stopped, and that verification project's containers, network,
disposable volume, and environment file were removed. No existing developer
database or real repository `.env` was modified.

For ordinary development, select JDK 25 in the IDE/terminal and add the existing
Docker Desktop `resources\bin` directory to PATH, then follow the README.
