-- PostgreSQL: eseguito automaticamente, in transazione, all'avvio.
LOCK TABLE products, product_variants IN SHARE ROW EXCLUSIVE MODE;

-- Serializza i movimenti delle varianti dello stesso padre, anche al primo inserimento.
CREATE OR REPLACE FUNCTION store_variant_stock_lock() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM id FROM products WHERE id = NEW.product_id FOR UPDATE;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM id FROM products WHERE id = OLD.product_id FOR UPDATE;
    ELSE
        PERFORM id FROM products WHERE id IN (OLD.product_id, NEW.product_id) ORDER BY id FOR UPDATE;
    END IF;
    -- Il primo inserimento sostituisce lo stock manuale del padre.
    -- Eseguito BEFORE: funziona anche per INSERT di più righe insieme.
    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1 FROM product_variants WHERE product_id = NEW.product_id) THEN
            UPDATE products SET stock_qty = 0 WHERE id = NEW.product_id;
        END IF;
    ELSIF TG_OP = 'UPDATE' AND OLD.product_id IS DISTINCT FROM NEW.product_id THEN
        IF NOT EXISTS (SELECT 1 FROM product_variants WHERE product_id = NEW.product_id) THEN
            UPDATE products SET stock_qty = 0 WHERE id = NEW.product_id;
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_variant_stock_lock ON product_variants;
CREATE TRIGGER trg_variant_stock_lock
BEFORE INSERT OR UPDATE OF stock_qty, product_id OR DELETE ON product_variants
FOR EACH ROW EXECUTE FUNCTION store_variant_stock_lock();

CREATE OR REPLACE FUNCTION store_variant_stock_total() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.product_id = NEW.product_id THEN
        UPDATE products SET stock_qty = COALESCE(stock_qty, 0)
            + COALESCE(NEW.stock_qty, 0) - COALESCE(OLD.stock_qty, 0)
        WHERE id = NEW.product_id;
    ELSE
        IF TG_OP IN ('DELETE', 'UPDATE') THEN
            UPDATE products SET stock_qty = COALESCE(stock_qty, 0) - COALESCE(OLD.stock_qty, 0)
            WHERE id = OLD.product_id;
        END IF;
        IF TG_OP IN ('INSERT', 'UPDATE') THEN
            UPDATE products SET stock_qty = COALESCE(stock_qty, 0) + COALESCE(NEW.stock_qty, 0)
            WHERE id = NEW.product_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION store_product_stock_guard() RETURNS trigger AS $$
BEGIN
    -- Le variazioni atomiche del trigger varianti sono già il totale corretto.
    IF pg_trigger_depth() = 1 AND EXISTS (SELECT 1 FROM product_variants WHERE product_id = NEW.id) THEN
        SELECT COALESCE(sum(COALESCE(stock_qty, 0)), 0)::integer INTO NEW.stock_qty
        FROM product_variants WHERE product_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_variant_stock_total ON product_variants;
CREATE TRIGGER trg_variant_stock_total
AFTER INSERT OR UPDATE OF stock_qty, product_id OR DELETE ON product_variants
FOR EACH ROW EXECUTE FUNCTION store_variant_stock_total();

DROP TRIGGER IF EXISTS trg_product_stock_guard ON products;
CREATE TRIGGER trg_product_stock_guard BEFORE UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION store_product_stock_guard();

UPDATE products p SET stock_qty = totals.qty
FROM (SELECT product_id, COALESCE(sum(COALESCE(stock_qty, 0)), 0)::integer AS qty
      FROM product_variants GROUP BY product_id) totals
WHERE p.id = totals.product_id AND p.stock_qty IS DISTINCT FROM totals.qty;
