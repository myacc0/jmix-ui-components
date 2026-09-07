-- Tables in the StarRocks `dwh` database, mirrored from the MAIN app database (jmix-postgres).
-- Apply:  docker exec -i jmix-starrocks mysql -u dwh_reporting -h 127.0.0.1 -P 9030 -pqweasd123 < starrocks-dwh-tables.sql
--
-- Type mapping from PostgreSQL: uuid -> VARCHAR(36), unbounded numeric -> DECIMAL(20,4),
-- text -> STRING, timestamp -> DATETIME. Primary Key model so UPDATE/DELETE are supported.
--
-- NOTE: StarRocks has no foreign keys; the `-> table.column` comments record the
-- relation that the main store enforces with a real FK constraint.

USE dwh;

-- mirrors main.DEMO_PRODUCT_CATEGORY (entity demo_ProductCategory)
DROP TABLE IF EXISTS product_categories;
CREATE TABLE product_categories (
  id          VARCHAR(36)    NOT NULL COMMENT "source: uuid",
  version     INT            NOT NULL DEFAULT "1",
  name        VARCHAR(255)   NULL,
  description STRING         NULL,
  parent_id   VARCHAR(36)    NULL COMMENT "source: uuid -> product_categories.id",
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME       NULL
) ENGINE=OLAP
PRIMARY KEY(id)
DISTRIBUTED BY HASH(id) BUCKETS 4
PROPERTIES("replication_num" = "1");

-- mirrors main.products (entity demo_Product)
DROP TABLE IF EXISTS products;
CREATE TABLE products (
  id          VARCHAR(36)    NOT NULL COMMENT "source: uuid",
  name        VARCHAR(255)   NOT NULL,
  description STRING         NULL,
  price       DECIMAL(20,4)  NOT NULL,
  sale        DECIMAL(20,4)  NULL,
  category_id VARCHAR(36)    NULL COMMENT "source: uuid -> product_categories.id",
  quantity    INT            NOT NULL DEFAULT "0",
  created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME       NULL
) ENGINE=OLAP
PRIMARY KEY(id)
DISTRIBUTED BY HASH(id) BUCKETS 4
PROPERTIES("replication_num" = "1");

-- mirrors main.orders (entity demo_Order)
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
  id               INT            NOT NULL,
  product_id       VARCHAR(36)    NOT NULL COMMENT "source: uuid -> products.id",
  total_sum        DECIMAL(20,4)  NOT NULL,
  sale_sum         DECIMAL(20,4)  NULL,
  delivery_sum     DECIMAL(20,4)  NULL,
  quantity         INT            NOT NULL,
  payment_method   VARCHAR(50)    NOT NULL,
  customer_phone   VARCHAR(50)    NULL,
  customer_email   VARCHAR(255)   NULL,
  customer_address VARCHAR(500)   NULL,
  customer_name    VARCHAR(255)   NULL,
  order_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes            STRING         NULL
) ENGINE=OLAP
PRIMARY KEY(id)
DISTRIBUTED BY HASH(id) BUCKETS 4
PROPERTIES("replication_num" = "1");
