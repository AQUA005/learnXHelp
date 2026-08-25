-- Reference data every deployment needs in order to function.
--
-- This is not demo data: it is the single university the deployment serves and
-- the dropdown values the signup form depends on. Demo accounts and sample
-- content are seeded separately by DataInitializer, which only runs under the
-- dev profile.

INSERT INTO universities (name, domain)
VALUES ('University of Science and Technology Chittagong', 'ustc.ac.bd');

-- Signup and class administration read these lists.
INSERT INTO system_metadata (type, meta_value, university_id)
SELECT v.type, v.meta_value, u.id
FROM universities u
CROSS JOIN (
    SELECT 'DEPARTMENT' AS type, 'CSE - Computer Science & Engineering' AS meta_value
    UNION ALL SELECT 'DEPARTMENT', 'EEE - Electrical & Electronic Engineering'
    UNION ALL SELECT 'DEPARTMENT', 'CE - Civil Engineering'
    UNION ALL SELECT 'DEPARTMENT', 'BBA - Business Administration'
    UNION ALL SELECT 'DEPARTMENT', 'PHARM - Pharmacy'

    UNION ALL SELECT 'SEMESTER', '1st Semester'
    UNION ALL SELECT 'SEMESTER', '2nd Semester'
    UNION ALL SELECT 'SEMESTER', '3rd Semester'
    UNION ALL SELECT 'SEMESTER', '4th Semester'
    UNION ALL SELECT 'SEMESTER', '5th Semester'
    UNION ALL SELECT 'SEMESTER', '6th Semester'
    UNION ALL SELECT 'SEMESTER', '7th Semester'
    UNION ALL SELECT 'SEMESTER', '8th Semester'

    UNION ALL SELECT 'BATCH', 'Batch 20'
    UNION ALL SELECT 'BATCH', 'Batch 21'
    UNION ALL SELECT 'BATCH', 'Batch 22'
    UNION ALL SELECT 'BATCH', 'Batch 23'
    UNION ALL SELECT 'BATCH', 'Batch 24'

    UNION ALL SELECT 'SECTION', 'Section A'
    UNION ALL SELECT 'SECTION', 'Section B'
    UNION ALL SELECT 'SECTION', 'Section C'

    UNION ALL SELECT 'DESIGNATION', 'Professor'
    UNION ALL SELECT 'DESIGNATION', 'Associate Professor'
    UNION ALL SELECT 'DESIGNATION', 'Assistant Professor'
    UNION ALL SELECT 'DESIGNATION', 'Lecturer'
) v
WHERE u.domain = 'ustc.ac.bd';
