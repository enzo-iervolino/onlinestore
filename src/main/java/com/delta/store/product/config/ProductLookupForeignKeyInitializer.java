package com.delta.store.product.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Verifica e completa le foreign key facoltative dei prodotti verso le tabelle
 * lookup. I valori non più presenti nella tabella collegata vengono impostati
 * a NULL prima della creazione del vincolo, perché le relazioni sono opzionali.
 *
 * Il componente è idempotente: a ogni avvio controlla i vincoli già presenti
 * e crea esclusivamente quelli mancanti.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductLookupForeignKeyInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductLookupForeignKeyInitializer.class);

    private static final String PRODUCTS_TABLE = "products";
    private static final String TARGET_PRIMARY_KEY = "id";

    private static final List<LookupForeignKey> LOOKUP_FOREIGN_KEYS = List.of(
            new LookupForeignKey(
                    "group_id",
                    "product_groups",
                    "fk_products_group",
                    "gruppo"
            ),
            new LookupForeignKey(
                    "subgroup_id",
                    "product_subgroups",
                    "fk_products_subgroup",
                    "sottogruppo"
            ),
            new LookupForeignKey(
                    "brand_id",
                    "product_brands",
                    "fk_products_brand",
                    "brand"
            ),
            new LookupForeignKey(
                    "family_id",
                    "product_families",
                    "fk_products_family",
                    "famiglia"
            ),
            new LookupForeignKey(
                    "subfamily_id",
                    "product_subfamilies",
                    "fk_products_subfamily",
                    "sottofamiglia"
            )
    );

    private final DataSource dataSource;

    public ProductLookupForeignKeyInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = resolveSchemaName(connection);
            verifyRequiredTablesAndColumns(connection, schemaName);

            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                for (LookupForeignKey foreignKey : LOOKUP_FOREIGN_KEYS) {
                    normalizeOrphanValues(connection, schemaName, foreignKey);
                    createForeignKeyIfMissing(connection, schemaName, foreignKey);
                }

                connection.commit();
                LOGGER.info(
                        "Foreign key facoltative prodotti verificate correttamente nello schema {}.",
                        schemaName
                );
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Impossibile verificare o creare le foreign key facoltative della tabella products.",
                    ex
            );
        }
    }

    private void verifyRequiredTablesAndColumns(Connection connection, String schemaName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();

        requireTable(metadata, catalog, schemaName, PRODUCTS_TABLE);
        for (LookupForeignKey foreignKey : LOOKUP_FOREIGN_KEYS) {
            requireTable(metadata, catalog, schemaName, foreignKey.targetTable());
            requireColumn(metadata, catalog, schemaName, PRODUCTS_TABLE, foreignKey.sourceColumn());
            requireColumn(metadata, catalog, schemaName, foreignKey.targetTable(), TARGET_PRIMARY_KEY);
        }
    }

    private void requireTable(
            DatabaseMetaData metadata,
            String catalog,
            String schemaName,
            String tableName
    ) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(catalog, schemaName, tableName, new String[]{"TABLE"})) {
            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Tabella necessaria non trovata: " + schemaName + "." + tableName
                );
            }
        }
    }

    private void requireColumn(
            DatabaseMetaData metadata,
            String catalog,
            String schemaName,
            String tableName,
            String columnName
    ) throws SQLException {
        try (ResultSet resultSet = metadata.getColumns(catalog, schemaName, tableName, columnName)) {
            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Colonna necessaria non trovata: "
                                + schemaName + "." + tableName + "." + columnName
                );
            }
        }
    }

    private void normalizeOrphanValues(
            Connection connection,
            String schemaName,
            LookupForeignKey foreignKey
    ) throws SQLException {
        String productsTable = qualifiedName(connection, schemaName, PRODUCTS_TABLE);
        String targetTable = qualifiedName(connection, schemaName, foreignKey.targetTable());
        String sourceColumn = quoteIdentifier(connection, foreignKey.sourceColumn());
        String targetColumn = quoteIdentifier(connection, TARGET_PRIMARY_KEY);

        String countSql = "SELECT COUNT(*) FROM " + productsTable + " p "
                + "WHERE p." + sourceColumn + " IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM " + targetTable + " r "
                + "WHERE r." + targetColumn + " = p." + sourceColumn + ")";

        long orphanCount;
        try (PreparedStatement statement = connection.prepareStatement(countSql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            orphanCount = resultSet.getLong(1);
        }

        if (orphanCount == 0) {
            LOGGER.debug(
                    "Nessun riferimento {} non valido trovato in products.{}.",
                    foreignKey.displayName(),
                    foreignKey.sourceColumn()
            );
            return;
        }

        String updateSql = "UPDATE " + productsTable + " p SET " + sourceColumn + " = NULL "
                + "WHERE p." + sourceColumn + " IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM " + targetTable + " r "
                + "WHERE r." + targetColumn + " = p." + sourceColumn + ")";

        int normalizedRows;
        try (Statement statement = connection.createStatement()) {
            normalizedRows = statement.executeUpdate(updateSql);
        }

        LOGGER.warn(
                "Rilevati {} riferimenti {} non validi in products.{}: {} valori impostati a NULL.",
                orphanCount,
                foreignKey.displayName(),
                foreignKey.sourceColumn(),
                normalizedRows
        );
    }

    private void createForeignKeyIfMissing(
            Connection connection,
            String schemaName,
            LookupForeignKey foreignKey
    ) throws SQLException {
        if (foreignKeyExists(connection, schemaName, foreignKey)) {
            LOGGER.debug(
                    "Foreign key {} già presente.",
                    foreignKey.constraintName()
            );
            return;
        }

        String productsTable = qualifiedName(connection, schemaName, PRODUCTS_TABLE);
        String targetTable = qualifiedName(connection, schemaName, foreignKey.targetTable());
        String constraintName = quoteIdentifier(connection, foreignKey.constraintName());
        String sourceColumn = quoteIdentifier(connection, foreignKey.sourceColumn());
        String targetColumn = quoteIdentifier(connection, TARGET_PRIMARY_KEY);

        String sql = "ALTER TABLE " + productsTable
                + " ADD CONSTRAINT " + constraintName
                + " FOREIGN KEY (" + sourceColumn + ")"
                + " REFERENCES " + targetTable + " (" + targetColumn + ")"
                + " ON DELETE SET NULL";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }

        LOGGER.info(
                "Creata foreign key {}: products.{} -> {}.{}.",
                foreignKey.constraintName(),
                foreignKey.sourceColumn(),
                foreignKey.targetTable(),
                TARGET_PRIMARY_KEY
        );
    }

    private boolean foreignKeyExists(
            Connection connection,
            String schemaName,
            LookupForeignKey foreignKey
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();

        try (ResultSet resultSet = metadata.getImportedKeys(catalog, schemaName, PRODUCTS_TABLE)) {
            while (resultSet.next()) {
                String sourceColumn = resultSet.getString("FKCOLUMN_NAME");
                String targetSchema = resultSet.getString("PKTABLE_SCHEM");
                String targetTable = resultSet.getString("PKTABLE_NAME");
                String targetColumn = resultSet.getString("PKCOLUMN_NAME");

                if (equalsIgnoreCase(foreignKey.sourceColumn(), sourceColumn)
                        && equalsIgnoreCase(schemaName, targetSchema)
                        && equalsIgnoreCase(foreignKey.targetTable(), targetTable)
                        && equalsIgnoreCase(TARGET_PRIMARY_KEY, targetColumn)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String resolveSchemaName(Connection connection) throws SQLException {
        String schemaName = connection.getSchema();
        return schemaName == null || schemaName.isBlank() ? "public" : schemaName.trim();
    }

    private String qualifiedName(Connection connection, String schemaName, String tableName) {
        return quoteIdentifier(connection, schemaName)
                + "."
                + quoteIdentifier(connection, tableName);
    }

    private String quoteIdentifier(Connection connection, String identifier) {
        try {
            String quote = connection.getMetaData().getIdentifierQuoteString();
            if (quote == null || quote.isBlank()) {
                return identifier;
            }

            String normalizedQuote = quote.trim();
            return normalizedQuote
                    + identifier.replace(normalizedQuote, normalizedQuote + normalizedQuote)
                    + normalizedQuote;
        } catch (SQLException ex) {
            throw new IllegalStateException("Impossibile proteggere l'identificatore SQL.", ex);
        }
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private record LookupForeignKey(
            String sourceColumn,
            String targetTable,
            String constraintName,
            String displayName
    ) {
    }
}
