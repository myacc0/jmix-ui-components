-- Copies DEPARTMENT, EMPLOYEE, DEMO_JOB_TITLE, DEMO_POSITION from the MAIN database
-- (jmix-postgres) into the StarRocks `dwh` tables created by starrocks-dwh-tables.sql.
--
-- Runs in PostgreSQL: each SELECT below emits the StarRocks TRUNCATE + INSERT statements
-- for one table, which are piped straight into StarRocks. Every run is a full refresh.
--
--   docker exec -i jmix-postgres psql -U postgres -d postgres -X -q -At -v ON_ERROR_STOP=1 < starrocks-copy-orgstructure.sql \
--     | docker exec -i jmix-starrocks mysql -u dwh_reporting -h 127.0.0.1 -P 9030 -pqweasd123 --default-character-set=utf8mb4 dwh
--
-- To only look at the generated statements, drop the second half of the pipe.

-- StarRocks string literal: MySQL-style escaping of \ and ', NULL for NULL. Session-scoped.
CREATE FUNCTION pg_temp.sr_lit(v text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce('''' || replace(replace(v, '\', '\\'), '''', '\''') || '''', 'NULL')
$$;

SELECT 'TRUNCATE TABLE department;';
SELECT 'INSERT INTO department (id, version, name, parent_department_id, ord_no) VALUES' || E'\n'
    || string_agg(format('(%s, %s, %s, %s, %s)',
                         pg_temp.sr_lit(id::text),
                         version,
                         pg_temp.sr_lit(name),
                         pg_temp.sr_lit(parent_department_id::text),
                         coalesce(ord_no::text, 'NULL')),
                  E',\n' ORDER BY id) || ';'
FROM department;

SELECT 'TRUNCATE TABLE employee;';
SELECT 'INSERT INTO employee (id, version, first_name, email) VALUES' || E'\n'
    || string_agg(format('(%s, %s, %s, %s)',
                         pg_temp.sr_lit(id::text),
                         version,
                         pg_temp.sr_lit(first_name),
                         pg_temp.sr_lit(email)),
                  E',\n' ORDER BY id) || ';'
FROM employee;

SELECT 'TRUNCATE TABLE demo_job_title;';
SELECT 'INSERT INTO demo_job_title (id, name) VALUES' || E'\n'
    || string_agg(format('(%s, %s)',
                         pg_temp.sr_lit(id::text),
                         pg_temp.sr_lit(name)),
                  E',\n' ORDER BY id) || ';'
FROM demo_job_title;

SELECT 'TRUNCATE TABLE demo_position;';
SELECT 'INSERT INTO demo_position (id, department_id, job_title_id, employee_id, lvl, ishead, status) VALUES' || E'\n'
    || string_agg(format('(%s, %s, %s, %s, %s, %s, %s)',
                         pg_temp.sr_lit(id::text),
                         pg_temp.sr_lit(department_id::text),
                         pg_temp.sr_lit(job_title_id::text),
                         pg_temp.sr_lit(employee_id::text),
                         coalesce(lvl::text, 'NULL'),
                         coalesce(ishead::text, 'NULL'),
                         pg_temp.sr_lit(status)),
                  E',\n' ORDER BY id) || ';'
FROM demo_position;
