package com.delta.store.utility.database;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DatabaseListBuilderService {

    public static final int MAX_RESULT_ROWS = 200;

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema",
            "pg_catalog",
            "pg_toast"
    );

    private static final List<String> PREFERRED_RELATION_COLUMNS = List.of(
            "name",
            "descri",
            "description",
            "title",
            "label",
            "denomination",
            "full_name",
            "fullname",
            "code",
            "email"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".gif",
            ".bmp",
            ".svg",
            ".avif"
    );

    private final DataSource dataSource;

    public DatabaseListBuilderService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<DatabaseTable> findTables() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<DatabaseTable> tables = new ArrayList<>();

            try (ResultSet resultSet = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String schemaName = clean(resultSet.getString("TABLE_SCHEM"));
                    String tableName = clean(resultSet.getString("TABLE_NAME"));

                    if (tableName.isBlank() || isSystemSchema(schemaName)) {
                        continue;
                    }

                    tables.add(createDatabaseTable(schemaName, tableName));
                }
            }

            tables.sort(Comparator
                    .comparing(DatabaseTable::schemaName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(DatabaseTable::tableName, String.CASE_INSENSITIVE_ORDER));

            return List.copyOf(tables);
        } catch (SQLException ex) {
            throw new IllegalStateException("Impossibile leggere le tabelle del database.", ex);
        }
    }

    public DatabaseTable requireTable(String tableKey) {
        String normalizedKey = clean(tableKey);
        return findTables().stream()
                .filter(table -> table.key().equals(normalizedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La tabella selezionata non è disponibile."));
    }

    public List<DatabaseColumn> findColumns(String tableKey) {
        DatabaseTable table = requireTable(tableKey);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<RawColumn> rawColumns = readColumns(metadata, catalog, table);
            Map<String, DatabaseRelation> relationsBySourceColumn = readRelations(
                    metadata,
                    catalog,
                    table
            );

            List<DatabaseColumn> columns = rawColumns.stream()
                    .map(column -> new DatabaseColumn(
                            column.name(),
                            column.typeName(),
                            column.nullable(),
                            column.ordinalPosition(),
                            column.jdbcType(),
                            relationsBySourceColumn.get(column.name())
                    ))
                    .sorted(Comparator.comparingInt(DatabaseColumn::ordinalPosition))
                    .toList();

            return List.copyOf(columns);
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Impossibile leggere i campi della tabella " + table.displayName() + ".",
                    ex
            );
        }
    }

    public DatabaseListResult buildList(
            String tableKey,
            List<String> requestedColumns,
            Map<String, String> requestedAliases,
            Map<String, String> requestedRelationColumns
    ) {
        DatabaseTable table = requireTable(tableKey);
        List<DatabaseColumn> availableColumns = findColumns(tableKey);

        Map<String, DatabaseColumn> columnsByName = new LinkedHashMap<>();
        for (DatabaseColumn column : availableColumns) {
            columnsByName.put(column.name(), column);
        }

        LinkedHashSet<String> selectedColumnNames = new LinkedHashSet<>();
        if (requestedColumns != null) {
            for (String requestedColumn : requestedColumns) {
                String columnName = clean(requestedColumn);
                if (!columnName.isBlank()) {
                    selectedColumnNames.add(columnName);
                }
            }
        }

        if (selectedColumnNames.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno un campo per costruire la lista.");
        }

        for (String columnName : selectedColumnNames) {
            if (!columnsByName.containsKey(columnName)) {
                throw new IllegalArgumentException("Il campo selezionato non appartiene alla tabella: " + columnName);
            }
        }

        List<SelectedOutputColumn> outputColumns = selectedColumnNames.stream()
                .map(columnsByName::get)
                .map(column -> createOutputColumn(column, requestedAliases, requestedRelationColumns))
                .toList();

        List<String> orderedColumns = outputColumns.stream()
                .map(outputColumn -> outputColumn.sourceColumn().name())
                .toList();

        List<String> headers = outputColumns.stream()
                .map(SelectedOutputColumn::header)
                .toList();

        try (Connection connection = dataSource.getConnection()) {
            String sql = buildSelectSql(connection, table, outputColumns);
            List<List<DatabaseCell>> rows = new ArrayList<>();

            try (Statement statement = connection.createStatement()) {
                statement.setMaxRows(MAX_RESULT_ROWS + 1);

                try (ResultSet resultSet = statement.executeQuery(sql)) {
                    while (resultSet.next() && rows.size() <= MAX_RESULT_ROWS) {
                        List<DatabaseCell> row = new ArrayList<>(outputColumns.size());
                        for (int index = 0; index < outputColumns.size(); index++) {
                            SelectedOutputColumn outputColumn = outputColumns.get(index);
                            row.add(formatCell(
                                    resultSet.getObject(index + 1),
                                    outputColumn.displayColumnName()
                            ));
                        }
                        rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
                    }
                }
            }

            boolean truncated = rows.size() > MAX_RESULT_ROWS;
            if (truncated) {
                rows = new ArrayList<>(rows.subList(0, MAX_RESULT_ROWS));
            }

            return new DatabaseListResult(
                    table,
                    List.copyOf(orderedColumns),
                    List.copyOf(headers),
                    List.copyOf(rows),
                    truncated,
                    MAX_RESULT_ROWS
            );
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Impossibile costruire la lista dalla tabella " + table.displayName() + ".",
                    ex
            );
        }
    }

    private SelectedOutputColumn createOutputColumn(
            DatabaseColumn sourceColumn,
            Map<String, String> requestedAliases,
            Map<String, String> requestedRelationColumns
    ) {
        String header = resolveHeader(sourceColumn.name(), requestedAliases);
        String requestedRelatedColumn = requestedRelationColumns == null
                ? ""
                : clean(requestedRelationColumns.get(sourceColumn.name()));

        if (requestedRelatedColumn.isBlank()) {
            return new SelectedOutputColumn(
                    sourceColumn,
                    null,
                    header,
                    sourceColumn.name()
            );
        }

        DatabaseRelation relation = sourceColumn.relation();
        if (relation == null) {
            throw new IllegalArgumentException(
                    "Il campo " + sourceColumn.name() + " non possiede una relazione selezionabile."
            );
        }

        DatabaseRelatedColumn displayColumn = relation.displayColumns().stream()
                .filter(column -> column.name().equals(requestedRelatedColumn))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Il campo correlato selezionato non è disponibile: " + requestedRelatedColumn
                ));

        return new SelectedOutputColumn(
                sourceColumn,
                displayColumn,
                header,
                displayColumn.name()
        );
    }

    private List<RawColumn> readColumns(
            DatabaseMetaData metadata,
            String catalog,
            DatabaseTable table
    ) throws SQLException {
        List<RawColumn> columns = new ArrayList<>();

        try (ResultSet resultSet = metadata.getColumns(
                catalog,
                emptyToNull(table.schemaName()),
                table.tableName(),
                "%"
        )) {
            while (resultSet.next()) {
                String resultSchemaName = clean(resultSet.getString("TABLE_SCHEM"));
                String resultTableName = clean(resultSet.getString("TABLE_NAME"));

                if (!table.schemaName().equals(resultSchemaName)
                        || !table.tableName().equals(resultTableName)) {
                    continue;
                }

                String columnName = clean(resultSet.getString("COLUMN_NAME"));
                if (columnName.isBlank()) {
                    continue;
                }

                int nullableCode = resultSet.getInt("NULLABLE");
                columns.add(new RawColumn(
                        columnName,
                        clean(resultSet.getString("TYPE_NAME")),
                        nullableCode != DatabaseMetaData.columnNoNulls,
                        resultSet.getInt("ORDINAL_POSITION"),
                        resultSet.getInt("DATA_TYPE")
                ));
            }
        }

        columns.sort(Comparator.comparingInt(RawColumn::ordinalPosition));
        return columns;
    }

    private Map<String, DatabaseRelation> readRelations(
            DatabaseMetaData metadata,
            String catalog,
            DatabaseTable sourceTable
    ) throws SQLException {
        List<ImportedKeyRow> importedKeys = new ArrayList<>();

        try (ResultSet resultSet = metadata.getImportedKeys(
                catalog,
                emptyToNull(sourceTable.schemaName()),
                sourceTable.tableName()
        )) {
            while (resultSet.next()) {
                String sourceColumnName = clean(resultSet.getString("FKCOLUMN_NAME"));
                String targetSchemaName = clean(resultSet.getString("PKTABLE_SCHEM"));
                String targetTableName = clean(resultSet.getString("PKTABLE_NAME"));
                String targetColumnName = clean(resultSet.getString("PKCOLUMN_NAME"));
                String foreignKeyName = clean(resultSet.getString("FK_NAME"));
                int keySequence = resultSet.getInt("KEY_SEQ");

                if (sourceColumnName.isBlank()
                        || targetTableName.isBlank()
                        || targetColumnName.isBlank()
                        || isSystemSchema(targetSchemaName)) {
                    continue;
                }

                String groupKey = foreignKeyName.isBlank()
                        ? sourceColumnName + "->" + targetSchemaName + "." + targetTableName
                        : foreignKeyName;

                importedKeys.add(new ImportedKeyRow(
                        groupKey,
                        sourceColumnName,
                        targetSchemaName,
                        targetTableName,
                        targetColumnName,
                        keySequence
                ));
            }
        }

        Map<String, List<ImportedKeyRow>> keysByConstraint = new LinkedHashMap<>();
        for (ImportedKeyRow importedKey : importedKeys) {
            keysByConstraint.computeIfAbsent(importedKey.groupKey(), ignored -> new ArrayList<>())
                    .add(importedKey);
        }

        Map<String, DatabaseRelation> relationsBySourceColumn = new LinkedHashMap<>();
        for (List<ImportedKeyRow> constraintRows : keysByConstraint.values()) {
            if (constraintRows.size() != 1) {
                continue;
            }

            ImportedKeyRow importedKey = constraintRows.get(0);
            if (importedKey.keySequence() != 1
                    || relationsBySourceColumn.containsKey(importedKey.sourceColumnName())) {
                continue;
            }

            DatabaseTable targetTable = createDatabaseTable(
                    importedKey.targetSchemaName(),
                    importedKey.targetTableName()
            );
            List<DatabaseRelatedColumn> targetColumns = readRelatedColumns(
                    metadata,
                    catalog,
                    targetTable
            );

            if (targetColumns.isEmpty()) {
                continue;
            }

            String suggestedDisplayColumn = chooseSuggestedRelationColumn(
                    targetColumns,
                    importedKey.targetColumnName()
            );

            relationsBySourceColumn.put(
                    importedKey.sourceColumnName(),
                    new DatabaseRelation(
                            importedKey.sourceColumnName(),
                            targetTable,
                            importedKey.targetColumnName(),
                            List.copyOf(targetColumns),
                            suggestedDisplayColumn
                    )
            );
        }

        return Map.copyOf(relationsBySourceColumn);
    }

    private List<DatabaseRelatedColumn> readRelatedColumns(
            DatabaseMetaData metadata,
            String catalog,
            DatabaseTable table
    ) throws SQLException {
        List<DatabaseRelatedColumn> columns = new ArrayList<>();

        try (ResultSet resultSet = metadata.getColumns(
                catalog,
                emptyToNull(table.schemaName()),
                table.tableName(),
                "%"
        )) {
            while (resultSet.next()) {
                String resultSchemaName = clean(resultSet.getString("TABLE_SCHEM"));
                String resultTableName = clean(resultSet.getString("TABLE_NAME"));

                if (!table.schemaName().equals(resultSchemaName)
                        || !table.tableName().equals(resultTableName)) {
                    continue;
                }

                String columnName = clean(resultSet.getString("COLUMN_NAME"));
                if (columnName.isBlank()) {
                    continue;
                }

                int jdbcType = resultSet.getInt("DATA_TYPE");
                if (isBinaryType(jdbcType)) {
                    continue;
                }

                columns.add(new DatabaseRelatedColumn(
                        columnName,
                        clean(resultSet.getString("TYPE_NAME")),
                        resultSet.getInt("ORDINAL_POSITION"),
                        jdbcType
                ));
            }
        }

        columns.sort(Comparator.comparingInt(DatabaseRelatedColumn::ordinalPosition));
        return columns;
    }

    private String chooseSuggestedRelationColumn(
            List<DatabaseRelatedColumn> targetColumns,
            String referencedPrimaryKeyColumn
    ) {
        for (String preferredName : PREFERRED_RELATION_COLUMNS) {
            for (DatabaseRelatedColumn column : targetColumns) {
                if (column.name().equalsIgnoreCase(preferredName)) {
                    return column.name();
                }
            }
        }

        return targetColumns.stream()
                .filter(column -> !column.name().equals(referencedPrimaryKeyColumn))
                .filter(column -> isTextType(column.jdbcType()))
                .map(DatabaseRelatedColumn::name)
                .findFirst()
                .orElse("");
    }

    private String buildSelectSql(
            Connection connection,
            DatabaseTable table,
            List<SelectedOutputColumn> outputColumns
    ) {
        String baseAlias = "t0";
        List<String> selectExpressions = new ArrayList<>();
        List<String> joinExpressions = new ArrayList<>();
        int relationIndex = 0;

        for (SelectedOutputColumn outputColumn : outputColumns) {
            if (outputColumn.relatedDisplayColumn() == null) {
                selectExpressions.add(
                        baseAlias + "." + quoteIdentifier(connection, outputColumn.sourceColumn().name())
                );
                continue;
            }

            DatabaseRelation relation = outputColumn.sourceColumn().relation();
            String relationAlias = "r" + (++relationIndex);

            selectExpressions.add(
                    relationAlias + "." + quoteIdentifier(connection, outputColumn.relatedDisplayColumn().name())
            );

            joinExpressions.add(
                    "LEFT JOIN " + qualifiedTableName(connection, relation.referencedTable())
                            + " " + relationAlias
                            + " ON " + baseAlias + "." + quoteIdentifier(connection, relation.sourceColumnName())
                            + " = " + relationAlias + "." + quoteIdentifier(connection, relation.referencedColumnName())
            );
        }

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selectExpressions))
                .append(" FROM ")
                .append(qualifiedTableName(connection, table))
                .append(" ")
                .append(baseAlias);

        if (!joinExpressions.isEmpty()) {
            sql.append(" ").append(String.join(" ", joinExpressions));
        }

        sql.append(" LIMIT ").append(MAX_RESULT_ROWS + 1);
        return sql.toString();
    }

    private String qualifiedTableName(Connection connection, DatabaseTable table) {
        String quotedTable = quoteIdentifier(connection, table.tableName());
        if (!table.schemaName().isBlank()) {
            return quoteIdentifier(connection, table.schemaName()) + "." + quotedTable;
        }
        return quotedTable;
    }

    private String quoteIdentifier(Connection connection, String identifier) {
        try {
            String quote = clean(connection.getMetaData().getIdentifierQuoteString());
            if (quote.isBlank()) {
                return identifier;
            }
            return quote + identifier.replace(quote, quote + quote) + quote;
        } catch (SQLException ex) {
            throw new IllegalStateException("Impossibile proteggere l'identificatore SQL.", ex);
        }
    }

    private String resolveHeader(String columnName, Map<String, String> requestedAliases) {
        if (requestedAliases == null) {
            return columnName;
        }

        String alias = clean(requestedAliases.get(columnName));
        return alias.isBlank() ? columnName : alias;
    }

    private DatabaseCell formatCell(Object value, String displayColumnName) throws SQLException {
        Object formattedValue = formatValue(value);
        if (formattedValue instanceof String textValue) {
            String imageUrl = resolveImageUrl(displayColumnName, textValue);
            if (imageUrl != null) {
                return new DatabaseCell(formattedValue, true, imageUrl);
            }
        }

        return new DatabaseCell(formattedValue, false, null);
    }

    private Object formatValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return "[dati binari: " + bytes.length + " byte]";
        }
        if (value instanceof Blob blob) {
            return "[BLOB: " + blob.length() + " byte]";
        }
        if (value instanceof Clob clob) {
            long length = clob.length();
            int previewLength = (int) Math.min(length, 500);
            if (previewLength == 0) {
                return "";
            }
            String preview = clob.getSubString(1, previewLength);
            return length > previewLength ? preview + "…" : preview;
        }
        return value;
    }

    private String resolveImageUrl(String columnName, String rawValue) {
        String value = clean(rawValue).replace('\\', '/');
        if (value.isBlank() || value.length() > 2000) {
            return null;
        }

        String normalizedValue = value.toLowerCase(Locale.ROOT);
        boolean imageColumn = isImageColumn(columnName);
        boolean safeAbsoluteUrl = normalizedValue.startsWith("https://")
                || normalizedValue.startsWith("http://");
        boolean applicationPath = normalizedValue.startsWith("/uploads/")
                || normalizedValue.startsWith("/images/")
                || normalizedValue.startsWith("uploads/")
                || normalizedValue.startsWith("images/");

        if (!imageColumn && !applicationPath) {
            return null;
        }

        if (!safeAbsoluteUrl && !applicationPath) {
            return null;
        }

        if (!safeAbsoluteUrl && !hasImageExtension(normalizedValue)) {
            return null;
        }

        if (normalizedValue.startsWith("uploads/") || normalizedValue.startsWith("images/")) {
            return "/" + value;
        }

        return value;
    }

    private boolean isImageColumn(String columnName) {
        String normalizedColumn = clean(columnName)
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");

        return normalizedColumn.contains("image")
                || normalizedColumn.contains("photo")
                || normalizedColumn.contains("foto")
                || normalizedColumn.contains("thumbnail")
                || normalizedColumn.contains("avatar")
                || normalizedColumn.contains("picture")
                || normalizedColumn.contains("poster")
                || normalizedColumn.contains("logo");
    }

    private boolean hasImageExtension(String value) {
        String path = value;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }

        String finalPath = path;
        return IMAGE_EXTENSIONS.stream().anyMatch(finalPath::endsWith);
    }

    private boolean isBinaryType(int jdbcType) {
        return jdbcType == Types.BINARY
                || jdbcType == Types.VARBINARY
                || jdbcType == Types.LONGVARBINARY
                || jdbcType == Types.BLOB;
    }

    private boolean isTextType(int jdbcType) {
        return jdbcType == Types.CHAR
                || jdbcType == Types.VARCHAR
                || jdbcType == Types.LONGVARCHAR
                || jdbcType == Types.NCHAR
                || jdbcType == Types.NVARCHAR
                || jdbcType == Types.LONGNVARCHAR
                || jdbcType == Types.CLOB
                || jdbcType == Types.NCLOB;
    }

    private boolean isSystemSchema(String schemaName) {
        String normalizedSchema = clean(schemaName).toLowerCase(Locale.ROOT);
        return SYSTEM_SCHEMAS.contains(normalizedSchema)
                || normalizedSchema.startsWith("pg_temp_")
                || normalizedSchema.startsWith("pg_toast_");
    }

    private DatabaseTable createDatabaseTable(String schemaName, String tableName) {
        String safeSchemaName = clean(schemaName);
        String safeTableName = clean(tableName);
        return new DatabaseTable(
                createTableKey(safeSchemaName, safeTableName),
                safeSchemaName,
                safeTableName,
                safeSchemaName.isBlank() ? safeTableName : safeSchemaName + "." + safeTableName
        );
    }

    private String createTableKey(String schemaName, String tableName) {
        return schemaName.isBlank() ? tableName : schemaName + "." + tableName;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record RawColumn(
            String name,
            String typeName,
            boolean nullable,
            int ordinalPosition,
            int jdbcType
    ) {
    }

    private record ImportedKeyRow(
            String groupKey,
            String sourceColumnName,
            String targetSchemaName,
            String targetTableName,
            String targetColumnName,
            int keySequence
    ) {
    }

    private record SelectedOutputColumn(
            DatabaseColumn sourceColumn,
            DatabaseRelatedColumn relatedDisplayColumn,
            String header,
            String displayColumnName
    ) {
    }

    public record DatabaseTable(
            String key,
            String schemaName,
            String tableName,
            String displayName
    ) {
    }

    public record DatabaseColumn(
            String name,
            String typeName,
            boolean nullable,
            int ordinalPosition,
            int jdbcType,
            DatabaseRelation relation
    ) {
        public boolean hasRelation() {
            return relation != null;
        }
    }

    public record DatabaseRelation(
            String sourceColumnName,
            DatabaseTable referencedTable,
            String referencedColumnName,
            List<DatabaseRelatedColumn> displayColumns,
            String suggestedDisplayColumn
    ) {
    }

    public record DatabaseRelatedColumn(
            String name,
            String typeName,
            int ordinalPosition,
            int jdbcType
    ) {
    }

    public record DatabaseCell(
            Object value,
            boolean image,
            String imageUrl
    ) {
    }

    public record DatabaseListResult(
            DatabaseTable table,
            List<String> selectedColumns,
            List<String> headers,
            List<List<DatabaseCell>> rows,
            boolean truncated,
            int maximumRows
    ) {
        public int rowCount() {
            return rows.size();
        }
    }
}
