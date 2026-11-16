-- Eseguire con psql -v ON_ERROR_STOP=1 -f docs/test-stock-varianti.sql
-- Tabelle temporanee e rollback: non modifica prodotti reali.
BEGIN;
CREATE TEMP TABLE products (id bigint PRIMARY KEY, stock_qty integer);
CREATE TEMP TABLE product_variants (id bigint PRIMARY KEY, product_id bigint REFERENCES products(id), stock_qty integer);
SET LOCAL search_path = pg_temp;
INSERT INTO products VALUES (1, 999), (2, 7), (3, 0);
INSERT INTO product_variants VALUES (10, 1, 3), (11, 1, 5);
\ir ../src/main/resources/db/product-stock.sql
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=1) <> 8 THEN RAISE EXCEPTION 'Allineamento iniziale'; END IF;
END $$;
UPDATE product_variants SET stock_qty=stock_qty-2 WHERE id=10;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=1) <> 6 THEN RAISE EXCEPTION 'Scarico'; END IF;
END $$;
UPDATE product_variants SET stock_qty=stock_qty+2 WHERE id=10;
UPDATE products SET stock_qty=800 WHERE id=1;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=1) <> 8 THEN RAISE EXCEPTION 'Ripristino o protezione totale'; END IF;
END $$;
INSERT INTO product_variants VALUES (12, 2, 4);
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=2) <> 4 THEN RAISE EXCEPTION 'Prima variante'; END IF;
END $$;
UPDATE product_variants SET product_id=3 WHERE id=12;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=2) <> 0 OR (SELECT stock_qty FROM products WHERE id=3) <> 4 THEN RAISE EXCEPTION 'Cambio padre'; END IF;
END $$;
DELETE FROM product_variants WHERE product_id=1;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=1) <> 0 THEN RAISE EXCEPTION 'Eliminazione ultima variante'; END IF;
END $$;
UPDATE products SET stock_qty=12 WHERE id=2;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=2) <> 12 THEN RAISE EXCEPTION 'Stock manuale senza varianti'; END IF;
END $$;
INSERT INTO product_variants VALUES (13, 2, NULL);
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=2) <> 0 THEN RAISE EXCEPTION 'Stock nullo'; END IF;
END $$;
INSERT INTO products VALUES (4, 999);
INSERT INTO product_variants VALUES (20, 4, 3), (21, 4, 5);
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=4) <> 8 THEN RAISE EXCEPTION 'Inserimento multiplo'; END IF;
END $$;
UPDATE product_variants SET stock_qty=stock_qty+2 WHERE product_id=4;
DO $$ BEGIN
 IF (SELECT stock_qty FROM products WHERE id=4) <> 12 THEN RAISE EXCEPTION 'Movimento multiplo'; END IF;
END $$;
ROLLBACK;
