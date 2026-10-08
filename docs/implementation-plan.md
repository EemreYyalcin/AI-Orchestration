# Checkpoints

> Historical foundation plan; its deferred scopes/numbering were superseded by
> the implemented Checkpoints 6–14. See [implementation progress](implementation-progress.md)
> for that history and [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for current architecture.

| Checkpoint | Scope | Status |
| --- | --- | --- |
| 0 | Architecture review | Approved before this implementation. |
| 1 | Monorepo, minimal MVC backend, React/TypeScript frontend, wrapper, environment template, documentation | Considered complete by the user. |
| 2 | Local PostgreSQL and Redis Compose services, health checks, local connectivity | Considered complete by the user. |
| 3 | Application persistence and user provisioning | Implemented; current verification results are in persistence.md. |
| 4 | Session security, CSRF, logout, and security tests | Implemented; verification documented in security.md. |
| 5 | Google OIDC, current-user API, authenticated frontend | Implemented; see authentication.md and checkpoint-5-verification.md. |
| 6–7 | Further application capabilities | Deferred; scope requires explicit requirements. |
| 8 | Backend/frontend containers and complete stack | Deferred explicitly. |
| 9 | Baseline hardening and deployment preparation | Deferred. |
| 10 | First actual incident capability | Requires defined business requirements. |

Do not treat authored configuration as proof of successful runtime startup.
See [verification](verification.md) for executed checks and required next steps.
