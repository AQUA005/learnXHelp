# LearnX

Academic management system for USTC — class routines, a shared notes library,
announcements and class tests, online exams, and a gradebook.

## Stack

- Java 21, Spring Boot 4.0.6 (Web, Security, Data JPA, Validation, Mail)
- PostgreSQL (H2 for local dev until the Phase 2 migration lands)
- Frontend: static SPA under `src/main/resources/static` (React + Vite rewrite planned — Phase 4)

## Running locally

```bash
./mvnw spring-boot:run
```

The app starts on <http://localhost:8080>. With no datasource env vars set it uses a
file-backed H2 database at `~/.learnx/learnxdb`.

Build a runnable jar:

```bash
./mvnw clean package
```

Run the container:

```bash
docker build -t learnx . && docker run -p 8080:8080 learnx
```

## Configuration

All settings are environment variables with local-dev defaults — no secrets live in the repo.

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP listen port | `8080` |
| `SPRING_DATASOURCE_URL` | JDBC URL (also accepts `JDBC_DATABASE_URL`) | H2 file at `~/.learnx` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | *(empty)* |
| `DATABASE_DIR` | Directory for the H2 file | `~/.learnx` |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | SMTP endpoint | `smtp.gmail.com` / `587` |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | SMTP credentials | *(empty)* |

Never commit real credentials — `.gitignore` blocks `.env`, `*.pem`, `*.key`, `*.jks`, `*.p12`.

## Roles

`STUDENT` → `CR` → `TEACHER` → `ADMIN` → `SYSTEM_ADMIN`, in increasing order of privilege.
New signups require administrator approval before they can log in.

## Project layout

```
src/main/java/com/ustc/learnx/
  config/      security, seed data
  controller/  REST endpoints
  entity/      JPA entities
  repository/  Spring Data repositories
  service/     domain logic
src/main/resources/static/   current SPA (app.js, index.html, style.css)
```

## Refactor in progress

This codebase is mid-refactor toward production readiness: security lockdown,
PostgreSQL + Flyway migrations, a service/DTO layer, and a React + TypeScript frontend.
