-- A bug report says who filed it, and the server decides who that is.
--
-- `reported_by` was written from the request body, so any signed-in account
-- could file a report under somebody else's name -- and the platform owner,
-- who is the only reader, had no way to tell. These columns are all set from
-- the authenticated session instead, and none of them is accepted from the
-- client.
--
-- Every statement here also runs against H2 in PostgreSQL mode, because the
-- test suite applies these same scripts.

-- The address the reporter signs in with. `reported_by` keeps holding their
-- display name, which is what the console shows; this is how you reach them.
ALTER TABLE bug_reports ADD COLUMN reporter_email VARCHAR(255);

-- What the reporter was at the time of filing. Stored rather than looked up,
-- because a student who later becomes a CR did not file it as one, and a
-- report outlives the account that raised it.
ALTER TABLE bug_reports ADD COLUMN reporter_role VARCHAR(32);

-- Which university the reporter belonged to, as a name rather than a foreign
-- key. A key would tie the report's lifetime to the tenant's: deleting a
-- university would either fail on the constraint or take its reports with it,
-- and a bug reported from a campus that has since left is still a bug in
-- LearnX. NULL means the platform owner, who belongs to no university.
ALTER TABLE bug_reports ADD COLUMN university_name VARCHAR(255);

-- The screen the report was filed from, captured by the form rather than
-- typed, so a report can be reproduced without asking where it happened.
ALTER TABLE bug_reports ADD COLUMN page_path VARCHAR(255);

-- The console lists newest first and filters by status; both are answered off
-- this index rather than by sorting the whole table.
CREATE INDEX idx_bug_reports_status_created ON bug_reports (status, created_at DESC);
