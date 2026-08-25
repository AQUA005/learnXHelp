# Deploying LearnX to Render

A step-by-step guide, written assuming you have not deployed anything before.
Read the two warnings first — they decide what you need to pay for.

---

## Before you start: two things that will bite you

### 1. The free database is temporary

Render's free PostgreSQL plan is removed after a trial period. When it goes, so
does everything in it: every account, grade, routine and announcement.

- **Trying things out?** Free is fine.
- **Real students relying on it?** Pay for the smallest paid database plan.

You can start on free and upgrade, but **upgrading is not automatic** — take a
backup first (see [Backups](#backups)).

### 2. Uploaded files need a disk

A container's own filesystem is wiped every time you deploy. Without a disk,
every PDF and set of notes anyone uploaded disappears on the next deploy.

A disk requires a paid instance type. On the free plan, uploads will not
survive. Class routines, announcements, exams and grades live in the database
and are unaffected — only uploaded files are at risk.

---

## What you will end up with

| Piece | What it does |
|---|---|
| **Web service** `learnx` | The application itself |
| **PostgreSQL** `learnx-db` | All the data |
| **Disk** `learnx-files` | Uploaded notes and profile pictures |

---

## Step 1 — Get this code onto `main`

Your Render service deploys from a branch, almost certainly `main`. This work is
on a branch and needs merging first.

1. Open the pull request: <https://github.com/AQUA005/learnXHelp/pull/1>
2. Read through it if you like, then click **Merge pull request**, then
   **Confirm merge**.

If Render is already watching `main`, merging starts a deploy immediately. That
deploy **will fail** until you finish step 3, which is expected and harmless.

---

## Step 2 — Create the database

1. Go to <https://dashboard.render.com>
2. **New +** > **Postgres**
3. Fill in:
   - **Name**: `learnx-db`
   - **Database**: `learnx`
   - **User**: `learnx`
   - **Region**: pick the one nearest your users, and **use this same region for
     the web service** — the two talk to each other constantly and a mismatch
     makes everything slow
   - **Plan**: see the warning above
4. **Create Database**, then wait until the status reads **Available**

Leave the page open; you need it in the next step.

---

## Step 3 — Point the application at the database

In the Render dashboard, open your existing `learnx` web service (the one
already connected to your repository), then **Environment**.

Add these. **Add** > type the key > paste the value > **Save Changes**.

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | The **Internal Database URL** from your database page |
| `LEARNX_STORAGE_ROOT` | `/var/lib/learnx/files` |
| `LEARNX_ADMIN_USERNAME` | A username for yourself, for example `principal` |
| `LEARNX_ADMIN_PASSWORD` | A strong password — at least 8 characters with a letter and a number |
| `LEARNX_ADMIN_EMAIL` | Your email address |

**About `DATABASE_URL`:** on your database page, copy **Internal Database URL**,
not External. Internal is faster and does not leave Render's network. It looks
like:

```
postgresql://learnx:LONGRANDOMPASSWORD@dpg-xxxxx-a/learnx
```

Paste it exactly. It is not a JDBC URL and does not look like one — the
application converts it at startup, so you do not need to change anything.

**About `LEARNX_ADMIN_*`:** these create your first administrator account. They
work **only once**, on a database with no accounts in it. Once an account
exists, these are ignored and cannot change or overwrite it.

---

## Step 4 — Add the disk (paid plans only)

Skip if you are on the free plan and accept that uploads are lost on deploy.

1. On the `learnx` service: **Settings** > **Disks** > **Add Disk**
2. **Name**: `learnx-files`
3. **Mount Path**: `/var/lib/learnx/files` — this must match `LEARNX_STORAGE_ROOT`
   exactly
4. **Size**: 1 GB is plenty to begin with
5. **Save**

---

## Step 5 — Deploy

**Manual Deploy** > **Deploy latest commit**.

The first build takes a while: it compiles Java, downloads Node and builds the
frontend. Ten minutes is normal.

Watch the **Logs** tab. You are looking for:

```
Migrating schema "public" to version "1 - baseline"
Migrating schema "public" to version "2 - seed reference data"
Migrating schema "public" to version "3 - move profile pictures to disk"
Created the first administrator 'principal'. Sign in and change this password...
Started LearnxApplication in 12.5 seconds
```

That is the application creating its own tables, seeding the university and
dropdown lists, and creating your account.

---

## Step 6 — Sign in

Open your Render URL, something like `https://learnx.onrender.com`.

Sign in with the username and password you set in step 3.

**Do this straight away:**

1. Go to **Profile** and change your password.
2. Back in Render: **Environment**, delete `LEARNX_ADMIN_PASSWORD`, save.
   It has done its job and there is no reason to leave a password sitting in
   your configuration.

---

## Step 7 — Set the university up

As the administrator, open **Administration**:

- **Dropdown options** — the lists students pick from when signing up.
  Departments, semesters, batches, sections and staff designations are
  pre-filled with sensible defaults; change them to match USTC.
- **Account approvals** — every sign-up lands here. Nobody can sign in until you
  approve them. This is the main thing you will do day to day.
- **Classes** — class groups, and promoting a cohort to the next semester.

Then tell students to sign up, and approve them as they arrive.

---

## Optional: sending email

Email is used for two things: telling someone their sign-up is being reviewed,
and password recovery. **Everything else works without it.** If you skip this,
users who forget their password will need you to help them.

To enable it, add:

| Key | Value |
|---|---|
| `SPRING_MAIL_HOST` | Your provider's SMTP host |
| `SPRING_MAIL_PORT` | `587` |
| `SPRING_MAIL_USERNAME` | The mailbox address |
| `SPRING_MAIL_PASSWORD` | An **app password**, not your normal login password |

With Gmail you must turn on 2-step verification and create an App Password;
Gmail rejects your ordinary password. Deliberately, an unreachable mail server
does **not** mark the service unhealthy — it will not restart your site.

---

## When something goes wrong

Open **Logs** on the web service and look for the first line containing `ERROR`.

| What you see | What it means | Fix |
|---|---|---|
| `The connection attempt failed` / `UnknownHost` | Cannot reach the database | Check `DATABASE_URL`. Use the **Internal** URL, and put the database in the same region as the service |
| `password authentication failed` | Wrong credentials | Re-copy the Internal Database URL; it changes if you recreate the database |
| `No accounts exist and no bootstrap administrator is configured` | Locked out | Set `LEARNX_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL`, then deploy again |
| `The bootstrap administrator password was rejected` | Password too weak | At least 8 characters, with a letter and a number |
| `Schema-validation: missing table` | Migrations did not run | Check the log for a Flyway error above this line |
| Site loads but every sign-in fails | Usually a stale session | Try a private browsing window |
| Uploaded files vanish after a deploy | No disk attached | Step 4 |

**Free instances sleep.** After a period of no traffic Render stops a free
service, and the next visit takes up to a minute while it starts. That is the
free plan, not a fault.

---

## Backups

The database holds everything that matters. Paid Render plans take automatic
backups; on free, take your own before any risky change:

1. Database page > **Connect** > copy the **External Database URL**
2. With PostgreSQL tools installed locally:

```bash
pg_dump "PASTE_EXTERNAL_URL_HERE" > learnx-backup.sql
```

Keep that file somewhere safe. Do it before upgrading a plan, and before
recreating a database.

---

## Making changes later

Render redeploys automatically whenever you push to `main`. The safe routine:

1. Make changes on a branch
2. Let the checks in `.github/workflows/ci.yml` run — they build everything, run
   114 backend tests and drive a real browser through the site
3. Merge to `main` only when they pass

Database changes go in `src/main/resources/db/migration/` as a new numbered
file, for example `V4__add_something.sql`. **Never edit a migration that has
already run** — Render will refuse to start, because the file no longer matches
what was applied. Always add a new one.
