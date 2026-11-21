-- Runs once, the first time the postgres volume is created.
-- One database per service (no cross-database foreign keys).
CREATE DATABASE user_db;
CREATE DATABASE catalog_db;
CREATE DATABASE dealer_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
