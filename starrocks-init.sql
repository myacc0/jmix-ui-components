-- Bootstrap for the StarRocks additional data store (service `starrocks` in docker-compose.yml).
--
-- StarRocks keeps users and databases in the FE metadata volume
-- (jmix-starrocks-fe-meta), NOT in docker-compose env vars. If that volume is
-- removed, re-apply this script:
--
--   docker exec -i jmix-starrocks mysql -u root -h 127.0.0.1 -P 9030 < starrocks-init.sql

CREATE DATABASE IF NOT EXISTS dwh;

CREATE USER IF NOT EXISTS 'dwh_reporting'@'%' IDENTIFIED BY 'qweasd123';

GRANT ALL ON DATABASE dwh TO USER 'dwh_reporting'@'%';
GRANT ALL ON ALL TABLES IN DATABASE dwh TO USER 'dwh_reporting'@'%';
GRANT ALL ON ALL VIEWS IN DATABASE dwh TO USER 'dwh_reporting'@'%';
GRANT ALL ON ALL MATERIALIZED VIEWS IN DATABASE dwh TO USER 'dwh_reporting'@'%';
