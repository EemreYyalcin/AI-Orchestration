# Checkpoint 1–2 verification

> Historical verification, not current prerequisites/results. The final capstone
> gate is [capstone-verification.md](capstone-verification.md); canonical current
> architecture is [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

Current Checkpoint 5 results are in [checkpoint-5-verification.md](checkpoint-5-verification.md).

This is the historical verification record from Checkpoints 1–2. Checkpoints 1
and 2 are considered complete by the user. The latest live PostgreSQL, build,
and startup results are recorded in [Checkpoint 3 persistence](persistence.md#verification-results)
and [Checkpoint 4 security](security.md).

Verified on 2026-10-07 in the existing Windows workspace
`C:\Users\Emre\Documents\Spring-Login-Project`.

## Installed tools

| Tool | Observed state |
| --- | --- |
| Java | Oracle JDK 23 (`23+37-2369`) selected; JDK 17 also present. JDK 25 not found in the inspected Java installation directories. |
| Node.js | Bundled Node v24.19.0, compatible with the frontend engines. |
| npm | Not on PATH and absent from the bundled Node installation. Official npm 12.2.0 was downloaded to a temporary tools directory for verification only. |
| Maven | No global Maven. The committed wrapper successfully downloaded and ran Maven 3.9.16. |
| Docker | Not on PATH; Docker Desktop executable also absent at its standard installation location. |
| Git | 2.53.0.windows.3. Initialized this previously empty workspace as a repository on branch `main`; no commit created. |

Temporary npm lives outside the repository at
`$env:TEMP\ai-incident-orchestrator-checkpoint-tools\package\bin\npm-cli.js`.
Its archive integrity was checked against the official npm registry's SHA-512
integrity metadata. No global software was installed. Maven Wrapper scripts came
from the official Apache wrapper 3.3.4 distribution; the pinned Maven archive's
SHA-256 is recorded in wrapper properties.

## Commands and results

| Command/check | Result |
| --- | --- |
| `java -version`, `node --version`, Git version and command discovery | Tool inventory above. |
| `git init`, `git branch -m main` | Passed. |
| `backend\mvnw.cmd --version` | Passed: Maven 3.9.16 with Java 23. |
| `backend\mvnw.cmd --batch-mode validate` | Passed; Boot 4.1.1 parent and dependency management resolved. |
| `backend\mvnw.cmd --batch-mode --no-transfer-progress verify` | Failed at compilation: `error: release version 25 not supported`. Java baseline was not lowered. No backend tests exist yet. |
| Portable `npm install --no-fund --no-audit` in frontend | Passed; generated `package-lock.json`. |
| Portable `npm ci --no-fund --no-audit` | Passed; reproduced the locked installation. |
| Portable `npm run build` | First failed with TS2882 for the CSS side-effect import. Added `src/vite-env.d.ts`; the subsequent TypeScript check and Vite production build passed. |
| Portable `npm run dev` | Passed; Vite 8.3.3 started on loopback port 5173. Stopped after verification. |
| HTTP GET `/` and `/src/App.tsx` through Vite | Both returned 200; title and transformed React module served. No browser-rendered UI assertion was performed. |
| HTTP GET `/api/health` through Vite | Returned 502 with a connection-refused proxy error, as expected with no backend listening. This verifies routing to the unavailable target, not backend health. |
| YAML parsing for Compose and both Spring configuration files | Passed with unique-key validation using temporary `yaml@2.8.1`. |
| Static Compose checks | Exactly PostgreSQL/Redis services, loopback published ports, and health checks present. PostgreSQL volume targets `/var/lib/postgresql`. |
| Ignore checks | `.env`, backend `target`, frontend `node_modules`, and frontend `dist` are ignored; template and lockfile are included. |
| `docker compose config --quiet` | Not run: Docker unavailable. YAML parsing does not replace Compose interpolation/schema validation. |
| PostgreSQL/Redis live health, backend startup and direct `/api/health` | Not verified: Docker and JDK 25 prerequisites unavailable. |

An auxiliary Python YAML-parser probe failed because PyYAML was not installed.
It was not added; the temporary JavaScript YAML parser performed the static
validation instead. Generated `node_modules`, `dist`, and backend `target` are
ignored local outputs, not repository source. No real `.env` was created.

## Exact next actions

1. Install JDK **25**, set `JAVA_HOME` to its installation directory, put its
   `bin` first on PATH, and select it in the IDE. Confirm both `java -version`
   and `backend\mvnw.cmd --version` report Java 25.
2. Install Docker Desktop with Linux containers and Compose v2, then start it.
   Confirm `docker version` can contact the engine and `docker compose version`
   succeeds. See [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/).
3. For ordinary terminal use, install Node **24** (at least 24.15) with npm, or
   make a compatible existing Node/npm installation available on PATH. Select
   npm **12.2.0** with `npm install --global npm@12.2.0` if needed. Confirm
   `node --version` and `npm --version`. The temporary npm above is only a
   verification tool, not a required project path.
4. Follow the root README: populate `.env`, run `docker compose config --quiet`,
   `docker compose up -d --wait`, and `docker compose ps`.
5. Run `backend\mvnw.cmd --batch-mode verify` under JDK 25, then start the backend
   with the loaded environment and `local` profile. Run `npm ci` and
   `npm run dev` in frontend.
6. Verify both container health checks, aggregate/readiness Actuator responses,
   the direct and proxied `/api/health` responses, and the page's UP state.

Checkpoint source/configuration is implemented. Full runtime verification is
pending these prerequisites; do not claim that the complete stack has started.
No persistence or authentication implementation should begin until approved.
