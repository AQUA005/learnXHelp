# LearnX

Academic management system for USTC — class routines, a shared notes library,
announcements and class tests, online exams, and a gradebook.

## Stack

- Java 21, Spring Boot 4.0.6 (Web, Security, Data JPA, Validation, Mail, Flyway, Actuator)
- PostgreSQL, with the schema owned by Flyway migrations
- React 19, TypeScript and Vite under `frontend/`, built into the same jar

## Running locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on <http://localhost:8080>. With no datasource variables set it uses a
local H2 file at `~/.learnx` running in PostgreSQL compatibility mode, so the same
migrations apply as in production. The `dev` profile also seeds demo accounts and
enables the H2 console at `/h2-console`.

Accounts sign in with an **email address**, not a username. Demo accounts (dev profile
only, all with password `password`): `master@learnx.com` (the platform owner),
`admin@learnx.help`, `teacher@learnx.help`, `cr@learnx.help`, `student@learnx.help`.

To run against a real PostgreSQL instead:

```bash
docker compose up -d
```

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/learnx SPRING_DATASOURCE_USERNAME=learnx SPRING_DATASOURCE_PASSWORD=learnx ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testing

```bash
./mvnw verify
```

This also builds, type-checks and lints the frontend.

Tests run the real Flyway migrations against H2 in PostgreSQL compatibility mode and
boot with `ddl-auto=validate`, so a mismatch between an entity and the schema fails the
build. `AuthorizationMatrixTest` asserts what each role may call on every sensitive
endpoint.

## Deploying

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Put a TLS-terminating reverse proxy in front: the `prod` profile marks the session
cookie `Secure`. Two volumes must persist — `pgdata` for the database and
`learnx-files` for uploaded study material.

## Configuration

All settings are environment variables. No secrets live in the repo.

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP listen port | `8080` |
| `SPRING_PROFILES_ACTIVE` | `dev`, `prod`, or unset | *(none)* |
| `SPRING_DATASOURCE_URL` | JDBC URL (also accepts `JDBC_DATABASE_URL`) | local H2 file |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Database credentials | `sa` / *(empty)* |
| `LEARNX_STORAGE_ROOT` | Directory holding uploaded files | system temp dir |
| `SPRING_MAIL_HOST` / `_PORT` | SMTP endpoint | `smtp.gmail.com` / `587` |
| `SPRING_MAIL_USERNAME` / `_PASSWORD` | SMTP credentials | *(empty)* |

## Operations

`GET /actuator/health` answers without a session, for container and load
balancer probes. Everything else under `/actuator` requires an administrator.
Mail is excluded from the health check on purpose: the application serves every
other feature without it, and an unreachable SMTP host should not restart a
working service.

Every response carries an `X-Request-Id`, and that id plus the caller's username
are attached to each log line written while handling the request. Under the
`prod` profile logs are emitted as structured JSON. Sign-ins and failed sign-in
attempts are written to the audit table alongside schedule changes; the
attempted password never is.

## Architecture notes

**Schema.** Owned by `src/main/resources/db/migration`. Hibernate never alters it —
`ddl-auto` is `validate`. Add a new `V<n>__description.sql` rather than editing an
applied migration.

**Uploaded files** are written under `LEARNX_STORAGE_ROOT` and referenced from the
`resources` row by a server-generated key; they are streamed on download. File bytes
are never held in the database or buffered in the heap.

**Authorization** is enforced server-side with `@PreAuthorize` on every controller,
over a role hierarchy: `STUDENT` → `CR` → `TEACHER` → `ADMIN` → `SYSTEM_ADMIN`, each
implying the ones before it. Endpoints name only the minimum role they require.
Ownership and university scoping are checked through `CurrentUserService`.

**Tenancy** is derived from the authenticated account, never from a request header.
The platform hosts many universities: migration `V2` seeds the first, a `SYSTEM_ADMIN`
adds the rest, and each is listed publicly and open for signup only once `published`
is set. Every tenant-owned row carries a `university_id`, and any endpoint taking an
id in its path asserts ownership through `CurrentUserService` before acting on it.

**Identity.** Email is the sign-in credential and is globally unique. `username` is
generated from it and never typed — it remains the Spring Security principal, and two
columns reference it as a string, so it cannot simply be dropped.

**Associations are lazy.** Queries that feed an endpoint declare what they need with
`@EntityGraph`, so a listing costs a fixed number of queries rather than one per row.

**The frontend** lives in `frontend/` and is built by the Maven build, so
`./mvnw package` produces one jar containing both the API and the interface.
For frontend work with hot reloading, run the backend and then:

```bash
cd frontend && npm run dev
```

Vite serves on port 5173 and proxies `/api` to the application on 8080, so the
session cookie and CSRF token behave exactly as they do in production.

Text is rendered as text by React rather than assembled into markup, and a lint
rule rejects `dangerouslySetInnerHTML`, so untrusted content cannot become
markup.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw verify` (which applies the migrations,
validates every entity mapping against them, and builds the frontend), then a
Playwright smoke test against the packaged jar, and on `main` publishes a
container image to GHCR.
