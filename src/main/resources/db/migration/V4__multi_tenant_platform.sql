-- Turns the schema from "one university that happens to have a table" into a
-- real multi-tenant one: universities gain a public profile and a published
-- flag, the platform gains its own branding row, and the three tables that
-- referenced a university without insisting on it are tightened.
--
-- Every statement here also runs against H2 in PostgreSQL mode, because the
-- test suite applies these same scripts. That rules out `UPDATE ... FROM`,
-- which is PostgreSQL-only; correlated subqueries are used instead.

-- ---------------------------------------------------------------------------
-- Universities: a public profile, and control over who is listed publicly.
-- ---------------------------------------------------------------------------

-- `slug` is the public URL key, not `domain`. A domain contains dots (which
-- complicate SPA route forwarding) and is editable by the platform owner, so
-- every shared link would break on a rename. A slug is assigned once at
-- creation and never changed.
ALTER TABLE universities ADD COLUMN slug          VARCHAR(64);
ALTER TABLE universities ADD COLUMN description   TEXT;
ALTER TABLE universities ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE universities ADD COLUMN contact_phone VARCHAR(64);
ALTER TABLE universities ADD COLUMN website       VARCHAR(255);
ALTER TABLE universities ADD COLUMN address       VARCHAR(500);

-- Mirrors users.profile_pic_key from V3: the key locates the file in storage,
-- the url is a path this application serves. A logo is never a remote URL —
-- the Content-Security-Policy is `img-src 'self' data:`, so a remote one would
-- simply not render.
ALTER TABLE universities ADD COLUMN logo_key      VARCHAR(200);

ALTER TABLE universities ADD COLUMN published     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE universities ADD COLUMN created_at    TIMESTAMP;
ALTER TABLE universities ADD COLUMN updated_at    TIMESTAMP;

UPDATE universities
SET slug       = REPLACE(REPLACE(LOWER(domain), '.', '-'), ' ', '-'),
    -- The university seeded by V2 is the live one; leaving it unpublished
    -- would empty the public homepage the moment this migration applies.
    published  = TRUE,
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE universities ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX uq_universities_slug ON universities (slug);
CREATE INDEX idx_universities_published ON universities (published, name);

-- V3 did the same for avatars. An unbounded base64 blob in this column would
-- now ride along in the public university list on every anonymous page load.
UPDATE universities SET logo_url = NULL WHERE logo_url LIKE 'data:%';

-- ---------------------------------------------------------------------------
-- Platform settings: one typed row, not a key/value bag.
-- ---------------------------------------------------------------------------
-- The setting set is small, fixed and typed, so a single row maps to a plain
-- entity that ddl-auto=validate checks and findById(1L) reads. A key/value
-- table would give untyped values, no NOT NULL enforcement and no validation
-- coverage. Seeding the row here means the service never handles "no row yet"
-- and two instances cannot race to insert it.
CREATE TABLE platform_settings (
    id            BIGINT       PRIMARY KEY,
    site_name     VARCHAR(120) NOT NULL DEFAULT 'LearnX',
    tagline       VARCHAR(255),
    logo_key      VARCHAR(200),
    logo_url      TEXT,
    icon_key      VARCHAR(200),
    icon_url      TEXT,
    support_email VARCHAR(255),
    updated_at    TIMESTAMP,
    CONSTRAINT chk_platform_settings_singleton CHECK (id = 1)
);

INSERT INTO platform_settings (id, site_name, updated_at)
VALUES (1, 'LearnX', CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------------
-- Audit logs: whose audit trail is this?
-- ---------------------------------------------------------------------------
-- Without this column the admin-reachable audit listing returns every row on
-- the platform, which is a cross-tenant leak as soon as a second university
-- exists. Nullable, because a failed sign-in is recorded before any account is
-- resolved and platform-level actions belong to no tenant.
ALTER TABLE audit_logs ADD COLUMN university_id BIGINT REFERENCES universities (id);
CREATE INDEX idx_audit_logs_university_timestamp ON audit_logs (university_id, timestamp);

UPDATE audit_logs
SET university_id = (
    SELECT u.university_id FROM users u WHERE u.username = audit_logs.changed_by
)
WHERE university_id IS NULL;

-- ---------------------------------------------------------------------------
-- Student classes: a class belongs to exactly one university.
-- ---------------------------------------------------------------------------
-- The old lookup index omitted university_id, matching a lookup in the approval
-- flow that matched on batch/department/section alone. Two universities each
-- running a "CSE / Batch 21 / Section A" would have shared one row, and with it
-- their routine, notes and announcements.
UPDATE student_classes
SET university_id = (
    SELECT MIN(u.university_id) FROM users u WHERE u.student_class_id = student_classes.id
)
WHERE university_id IS NULL;

-- Anything still unattached has no members and no university to infer one from.
DELETE FROM student_classes WHERE university_id IS NULL;

ALTER TABLE student_classes ALTER COLUMN university_id SET NOT NULL;

DROP INDEX idx_student_classes_lookup;
CREATE UNIQUE INDEX uq_student_classes_tenant_lookup
    ON student_classes (university_id, batch, department, section);

-- ---------------------------------------------------------------------------
-- System metadata: no more platform-global reference data.
-- ---------------------------------------------------------------------------
-- A row with a null university_id appears in every tenant's dropdowns, which is
-- the same kind of leak in a quieter form.
DELETE FROM system_metadata WHERE university_id IS NULL;
ALTER TABLE system_metadata ALTER COLUMN university_id SET NOT NULL;

ALTER TABLE system_metadata ADD CONSTRAINT chk_system_metadata_type
    CHECK (type IN ('SEMESTER', 'DEPARTMENT', 'BATCH', 'SECTION', 'DESIGNATION'));

CREATE UNIQUE INDEX uq_system_metadata_tenant_type_value
    ON system_metadata (university_id, type, meta_value);

-- ---------------------------------------------------------------------------
-- Email as the sign-in credential.
-- ---------------------------------------------------------------------------
-- users.email is already NOT NULL UNIQUE, so no structural change is needed.
-- The addresses are normalised because the unique constraint is case-sensitive:
-- 'Bob@x.test' and 'bob@x.test' are two rows today, and a case-insensitive
-- sign-in would not know which one was meant. If this collides, the constraint
-- aborts the migration — which is correct. A deploy that stops is better than
-- one that silently picks a winner.
--
-- Deliberately no functional index on LOWER(email): H2's support for expression
-- indexes in PostgreSQL mode is uneven, and it would behave differently in the
-- tests than in production. Normalisation happens on write and on lookup in Java.
UPDATE users SET email = LOWER(TRIM(email));
