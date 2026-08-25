# LearnX

Academic management system for USTC — class routines, a shared notes library,
announcements and class tests, online exams, and a gradebook.

## Stack

- Java 21, Spring Boot 4.0.6 (Web, Security, Data JPA, Validation, Mail, Flyway)
- PostgreSQL, with schema owned by Flyway migrations
- Frontend: static SPA under `src/main/resources/static` (React + Vite rewrite planned)

## Running locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on <http://localhost:8080>. With no datasource variables set it uses a
local H2 file at `~/.learnx` running in PostgreSQL compatibility mode, so the same
migrations apply as in production. The `dev` profile also seeds demo accounts and
enables the H2 console at `/h2-console`.

Demo accounts (dev profile only, all with password `password`): `master`, `admin`,
`teacher`, `cr`, `student`.

To run against a real PostgreSQL instead:

```bash
docker compose up -d
```

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/learnx SPRING_DATASOURCE_USERNAME=learnx SPRING_DATASOURCE_PASSWORD=learnx ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testing

```bash
./mvnw test
```

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
The deployment serves one university, seeded by migration `V2`, but rows carry a
`university_id` so multi-tenancy remains possible.

**Associations are lazy.** Queries that feed an endpoint declare what they need with
`@EntityGraph`, so a listing costs a fixed number of queries rather than one per row.
