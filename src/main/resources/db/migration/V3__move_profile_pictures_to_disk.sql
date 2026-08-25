-- Profile pictures move out of the users row.
--
-- They were stored as base64 data URLs in a TEXT column, which meant an
-- unbounded, unvalidated string travelled with the user on every fetch that
-- touched an account. Images now live under the storage root like any other
-- upload, and this column holds only the key.

ALTER TABLE users ADD COLUMN profile_pic_key VARCHAR(200);

-- Any base64 already stored is dropped rather than migrated: it cannot be
-- validated retrospectively, and a missing avatar is harmless. Anything that
-- is a plain URL is left alone.
UPDATE users
SET profile_pic_url = NULL
WHERE profile_pic_url IS NOT NULL
  AND profile_pic_url LIKE 'data:%';
