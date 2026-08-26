# Deploying LearnX to Render

A step-by-step guide, written assuming you have not deployed anything before.
Read the two warnings first — they decide what you need to pay for.

---

## Before you start: two things that will bite you

### 1. Render's own free database is temporary

Render's free PostgreSQL plan is removed after a trial period. When it goes, so
does everything in it: every account, grade, routine and announcement.

That is the single most important thing to get right, and it is why the next
section exists. **If you want a free database that does not expire, do not use
Render's** — host the database elsewhere and point Render at it. Everything
needed for that is already built in.

See [Choosing a database](#choosing-a-database).

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

## Step 1 — The code is already on `main`

This is done: the work was merged in
<https://github.com/AQUA005/learnXHelp/pull/1>, so Render is deploying from it
already.

That first deploy comes up but **cannot be signed into**, because the settings in
step 3 are not there yet: no database of your own, and no administrator account.
That is expected, not a fault. Steps 2 to 5 fix it.

---

## Choosing a database

The application needs PostgreSQL. It does not care who runs it — it reads a
single `DATABASE_URL` and works out the rest, so any of these is a matter of
pasting one line into Render.

**Prices and free-tier terms change.** Check the current terms on the provider's
own pricing page before committing; what follows is about the shape of each
option, not its price this week.

| Option | Free tier | The catch |
|---|---|---|
| **Neon** | Yes, and it does not expire | Pauses when idle, so the first request after a quiet spell is slow |
| **Supabase** | Yes | Free projects pause after a period of inactivity and need restoring from the dashboard |
| **Aiven** | Yes, a small plan | Smaller allowance than the others |
| **Render** | Yes, but removed after a trial period | Simplest to set up, and the fastest, because it sits beside the application |

### The recommendation

**Use Neon for a free database you intend to keep.** It is real PostgreSQL, the
free tier is not time-limited, and this application is small — it stores text,
while uploaded files go to disk, so it will not come close to the storage
allowance.

**Use Render's own database if you are paying**, because a database in the same
region as the application is faster than one across the internet, and there is
one less account to manage.

### What "pauses when idle" means

A serverless database shuts down when nothing is using it and wakes on the next
query. In practice a student opening the site after a quiet night waits a second
or two longer, once. The connection pool is configured for this, so it waits
rather than failing.

If you are also on Render's free plan, the *application* sleeps too, and that
delay is the larger of the two.

### Using Neon

1. Sign up at <https://neon.tech> and create a project
2. **Pick the region closest to your Render region** — every query crosses the
   internet, so this is worth a moment's thought
3. Copy the connection string it gives you. It looks like:

   ```
   postgresql://learnx_owner:npg_xxxx@ep-cool-morning-a1b2c3-pooler.eu-central-1.aws.neon.tech/learnx?sslmode=require
   ```

4. In Render, set that as `DATABASE_URL` on the web service

That is the whole integration. Prefer the **pooled** connection string if Neon
offers you a choice — the host contains `-pooler`. Keep `?sslmode=require`
exactly as given; it is what encrypts the connection, and it is carried through
untouched.

Then continue from [Step 3](#step-3--point-the-application-at-the-database) and
skip step 2, since you already have a database.

### If you outgrow the free tier

Nothing in the application changes. Create the new database, take a backup of
the old one (see [Backups](#backups)), restore it, and update `DATABASE_URL`.
The migrations run against whatever they find.

---

## Step 2 — Create the database (Render's own)

Skip this if you followed [Using Neon](#using-neon) or another provider — you
have a database already. Go straight to step 3.

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
| `DATABASE_URL` | Your database connection string — see below |
| `LEARNX_STORAGE_ROOT` | `/var/lib/learnx/files` |
| `LEARNX_ADMIN_USERNAME` | A username for yourself, for example `principal` |
| `LEARNX_ADMIN_PASSWORD` | A strong password — at least 8 characters with a letter and a number |
| `LEARNX_ADMIN_EMAIL` | Your email address |

**About `DATABASE_URL`:**

- **Using Render's database?** Copy the **Internal Database URL**, not the
  External one. Internal is faster and never leaves Render's network.
- **Using Neon, Supabase or Aiven?** Copy the connection string they gave you,
  including everything after the `?`.

Either way it looks something like:

```
postgresql://learnx:LONGRANDOMPASSWORD@some-host-name/learnx?sslmode=require
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
- **People** — everyone at the university, and the way back in for someone who
  cannot sign in. Resetting a password here shows the new one once, for you to
  pass on. This works whether or not email is set up.
- **Classes** — class groups, and promoting a cohort to the next semester.

Then tell students to sign up, and approve them as they arrive.

---

## Optional: sending email

Email is used for two things: telling someone their sign-up is being reviewed,
and letting people reset their own password. **Everything else works without
it** — sign-up, approval, routines, notes, announcements, exams and grades are
all unaffected, and an unreachable mail server deliberately does not mark the
service unhealthy, so it will never restart your site.

The only thing you lose is self-service password recovery, and an administrator
can reset anyone's password directly from **Administration > People**. So this
is genuinely optional; set it up when convenient.

### What you actually need

The application only ever **sends**. It never receives, so a full mailbox is
more than it requires. What it needs is a *transactional email* service: an SMTP
host, a username and a password.

That matters because a mailbox and a sending service are priced very
differently. If your mailbox subscription lapsed, you do not have to renew it
just to make password recovery work again.

**Terms and free allowances change** — check the provider's current pricing
rather than trusting this table.

| Service | Shape of the free tier | Notes |
|---|---|---|
| **Brevo** | A few hundred messages a day | Straightforward SMTP credentials |
| **Resend** | A few thousand a month | Simple to set up; SMTP as well as an API |
| **Mailjet** | A few thousand a month | Long-standing SMTP provider |
| **Amazon SES** | Not free, but priced per thousand and very cheap | Cheapest at volume; the most setup, and new accounts start restricted |
| **Zoho Mail** | Free tier for a custom domain | Choose this if you also want a *mailbox* at your domain, not only sending |
| **Gmail** | Free | Works, but sends from your personal address rather than your domain, and needs an App Password |

For a single university this is well inside every free tier: a handful of
messages a day at most.

### Setting it up

Whichever you choose, they give you a host, a username and a password. Put them
in Render under **Environment**:

| Key | Value |
|---|---|
| `SPRING_MAIL_HOST` | The SMTP host they give you |
| `SPRING_MAIL_PORT` | `587` |
| `SPRING_MAIL_USERNAME` | The username they give you |
| `SPRING_MAIL_PASSWORD` | The password or API key they give you |
| `LEARNX_MAIL_FROM` | The address recipients should see — see below |

Providers come in two kinds, and they differ in one respect.

A **mailbox provider** (Zoho, Gmail, Namecheap Private Email) logs you in with
an address, so leave `LEARNX_MAIL_FROM` unset and mail is sent from the
username.

A **relay** (Brevo, Resend, Mailjet, SES) logs you in with an API key or your
account's own address instead. Set `LEARNX_MAIL_FROM` to the address you want
on the From line — something like `noreply@yourdomain.com`. Without it the
login value ends up on the From line, and the message is rejected.

With Gmail specifically, turn on 2-step verification and create an **App
Password**. Gmail rejects your ordinary password.

### Making the mail arrive

A message sent from a new service to a domain that has not authorised it tends
to land in spam. Every provider above will ask you to add one or two records to
your domain's DNS — usually SPF and DKIM — and will show you exactly what to
add. It takes a few minutes and is worth doing; without it, password-recovery
codes quietly go missing.

If you no longer control the DNS for the domain, send from an address at a
domain you do control instead.

---

---

## If the deploy fails with "non-empty schema but no schema history table"

The full message reads:

```
Found non-empty schema(s) "public" but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

**This is the migrations protecting you, not a fault.**

The database already contains tables, but none that Flyway created. That happens
when the database was used by an earlier version of LearnX, which let Hibernate
build the schema by itself. This version owns the schema through the numbered
files in `src/main/resources/db/migration`, and it will not touch tables it did
not create.

### Do not set `baselineOnMigrate`

The message suggests it, and it is the wrong advice here. It tells Flyway to
assume whatever is present is already version 1, so `V1` — the file that creates
every table correctly — is skipped, and `V2` and `V3` are applied on top of the
old structure instead.

The two are not interchangeable. The old schema has a `system_admins` table that
no longer exists, keeps uploaded files in a `resources.file_data` column that has
been removed, and lacks `profile_pic_key`. The result is either a refusal to
start, or a half-migrated database that fails later and less clearly.

### The fix: start from an empty database

Point `DATABASE_URL` at a database with nothing in it. The migrations then build
all 19 tables, seed the university and the dropdown lists, and create your
administrator.

**This needs no SQL and no terminal.** If you are moving to Neon or another
provider anyway, you already have an empty database:

1. Create the database at the provider
2. Copy the connection string it gives you
3. In Render, open the `learnx` service > **Environment**, edit `DATABASE_URL`,
   paste, and **Save Changes**
4. Render redeploys by itself

The old database is simply left behind. Everything below is only needed if you
want to inspect or reuse it.

### Where to run SQL

The commands further down are SQL, and Render has no box in the browser to type
them into. You need something that can connect to a PostgreSQL database. In
rough order of least effort:

- **Your provider's own SQL editor.** Neon and Supabase both include one in their
  dashboard — look for *SQL Editor*. Nothing to install. This is the easiest
  option, but it only reaches *their* database, not one hosted on Render.
- **A free desktop client**, such as [DBeaver](https://dbeaver.io) or
  [pgAdmin](https://www.pgadmin.org). Install it, create a connection, and paste
  in the **External Database URL** from the Render database page. Use External,
  not Internal — Internal only works from inside Render.
- **The `psql` command line**, if you have PostgreSQL installed locally. The
  database page in Render shows a ready-made `psql` command to copy.

Whichever you use, the connection string contains the database password. Do not
paste it into a website that offers to run queries for you.

### First, check whether the old database holds anything you want

Only you can answer this. Connect using one of the methods above, then run:

```sql
SELECT
  (SELECT COUNT(*) FROM users)           AS accounts,
  (SELECT COUNT(*) FROM schedule_items)  AS routine_entries,
  (SELECT COUNT(*) FROM resources)       AS uploads;
```

- **All zeros, or only test accounts?** Nothing to keep. Use a fresh database.
- **Real students and real routines?** Take a backup before anything else:

  ```bash
  pg_dump "PASTE_EXTERNAL_DATABASE_URL" > learnx-old.sql
  ```

  Keep that file. Moving it into the new schema is a separate job — the two
  structures differ — but with the dump in hand nothing is lost while you decide.

### Reusing the same database instead

If you would rather keep the same database than create a new one, empty it using
one of the tools in [Where to run SQL](#where-to-run-sql). This **erases
everything in it**, so take the backup above first.

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Then redeploy. The log should show the three migrations running in order.

## When something goes wrong

Open **Logs** on the web service and look for the first line containing `ERROR`.

| What you see | What it means | Fix |
|---|---|---|
| `The connection attempt failed` / `UnknownHost` | Cannot reach the database | Check `DATABASE_URL`. Use the **Internal** URL, and put the database in the same region as the service |
| `password authentication failed` | Wrong credentials | Re-copy the Internal Database URL; it changes if you recreate the database |
| `SSL connection is required` or `no pg_hba.conf entry` | The provider requires TLS | Keep `?sslmode=require` on the end of `DATABASE_URL`; do not trim it |
| First request after a quiet period is slow, then fine | A serverless database waking up | Normal on a free tier. Nothing to fix |
| `too many clients already` | The pool is larger than the free tier allows | Set `DB_POOL_SIZE` to `3` and redeploy |
| `No accounts exist and no bootstrap administrator is configured` | Locked out | Set `LEARNX_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL`, then deploy again |
| `The bootstrap administrator password was rejected` | Password too weak | At least 8 characters, with a letter and a number |
| `Found non-empty schema(s) "public" but no schema history table` | The database was used by an older version of LearnX | See [the section above](#if-the-deploy-fails-with-non-empty-schema-but-no-schema-history-table). Do not set `baselineOnMigrate` |
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
2. With PostgreSQL tools installed locally (see [Where to run SQL](#where-to-run-sql)):

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
