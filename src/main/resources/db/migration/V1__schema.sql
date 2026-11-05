CREATE TABLE products (
 id BIGSERIAL PRIMARY KEY, sku VARCHAR(80) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL, category VARCHAR(40) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK (price >= 0), created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX products_name_idx ON products(name text_pattern_ops);
CREATE TABLE orders (
 id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX orders_product_idx ON orders(product_id);
