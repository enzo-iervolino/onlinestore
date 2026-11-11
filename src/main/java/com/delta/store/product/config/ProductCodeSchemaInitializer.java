package com.delta.store.product.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProductCodeSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCodeSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ProductCodeSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
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
                    )
                    """);

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_product_codes_product ON product_codes(product_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_product_codes_variant ON product_codes(variant_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_product_codes_type ON product_codes(code_type)");

            removeOrphanCodes();
            createForeignKeys();

            log.info("Tabella product_codes verificata correttamente.");
        } catch (DataAccessException ex) {
            log.error(
                    "Impossibile creare o verificare product_codes. Il salvataggio dei prodotti resta disponibile, "
                            + "ma i codici alternativi non saranno utilizzabili finché il database non verrà corretto.",
                    ex
            );
        }
    }

    private void removeOrphanCodes() {
        jdbcTemplate.update("""
                DELETE FROM product_codes pc
                WHERE pc.product_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM products p WHERE p.id = pc.product_id
                  )
                """);

        jdbcTemplate.update("""
                DELETE FROM product_codes pc
                WHERE pc.variant_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM product_variants v WHERE v.id = pc.variant_id
                  )
                """);
    }

    private void createForeignKeys() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'fk_product_codes_product'
                          AND conrelid = 'product_codes'::regclass
                    ) THEN
                        ALTER TABLE product_codes
                            ADD CONSTRAINT fk_product_codes_product
                            FOREIGN KEY (product_id)
                            REFERENCES products(id)
                            ON DELETE CASCADE;
                    END IF;
                END $$
                """);

        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'fk_product_codes_variant'
                          AND conrelid = 'product_codes'::regclass
                    ) THEN
                        ALTER TABLE product_codes
                            ADD CONSTRAINT fk_product_codes_variant
                            FOREIGN KEY (variant_id)
                            REFERENCES product_variants(id)
                            ON DELETE CASCADE;
                    END IF;
                END $$
                """);
    }
}
