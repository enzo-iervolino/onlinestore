package com.delta.store.product.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Installa e verifica il totale atomico delle varianti sul PostgreSQL del progetto. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProductStockSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductStockSchemaInitializer.class);
    private final DataSource dataSource;

    public ProductStockSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String sql;
        try (var input = new ClassPathResource("db/product-stock.sql").getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // PostgreSQL interpreta direttamente lo script PL/pgSQL completo.
                statement.execute(sql);
                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*) FROM pg_trigger
                        WHERE NOT tgisinternal AND tgenabled = 'O'
                          AND ((tgrelid = 'products'::regclass AND tgname = 'trg_product_stock_guard')
                            OR (tgrelid = 'product_variants'::regclass
                                AND tgname IN ('trg_variant_stock_lock', 'trg_variant_stock_total')))
                        """)) {
                    if (!result.next() || result.getInt(1) != 3) {
                        throw new IllegalStateException("Installazione trigger stock varianti incompleta");
                    }
                }
                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*) FROM products p
                        JOIN (SELECT product_id, sum(coalesce(stock_qty, 0)) AS qty
                              FROM product_variants GROUP BY product_id) v ON v.product_id = p.id
                        WHERE p.stock_qty IS DISTINCT FROM v.qty
                        """)) {
                    if (!result.next() || result.getLong(1) != 0) {
                        throw new IllegalStateException("Riallineamento stock varianti non riuscito");
                    }
                }
                connection.commit();
                log.info("Stock varianti: 3 trigger attivi verificati, totali prodotti riallineati.");
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
