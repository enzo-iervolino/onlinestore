CREATE TABLE IF NOT EXISTS product_codes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NULL,
    variant_id BIGINT NULL,
    code_type VARCHAR(20) NOT NULL,
    code_value VARCHAR(120) NOT NULL,
    normalized_code VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_codes_normalized UNIQUE (normalized_code),
    CONSTRAINT ck_product_codes_owner CHECK (
        (product_id IS NOT NULL AND variant_id IS NULL)
        OR (product_id IS NULL AND variant_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_product_codes_product ON product_codes(product_id);
CREATE INDEX IF NOT EXISTS idx_product_codes_variant ON product_codes(variant_id);
CREATE INDEX IF NOT EXISTS idx_product_codes_type ON product_codes(code_type);
