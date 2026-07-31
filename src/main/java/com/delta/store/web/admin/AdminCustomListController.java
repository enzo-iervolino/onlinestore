package com.delta.store.web.admin;

import com.delta.store.utility.database.DatabaseListBuilderService;
import com.delta.store.utility.database.DatabaseListBuilderService.DatabaseCell;
import com.delta.store.utility.database.DatabaseListBuilderService.DatabaseColumn;
import com.delta.store.utility.database.DatabaseListBuilderService.DatabaseListResult;
import com.delta.store.utility.database.DatabaseListBuilderService.DatabaseTable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/admin/list-builder")
public class AdminCustomListController {

    private static final String ALIAS_PARAMETER_PREFIX = "alias_";
    private static final String RELATION_PARAMETER_PREFIX = "relation_";
    private static final String WIDTH_PARAMETER_PREFIX = "width_";
    private static final String GAP_PARAMETER_PREFIX = "gap_";
    private static final String MAX_CHARACTERS_PARAMETER_PREFIX = "maxchars_";
    private static final String TEXT_MODE_PARAMETER_PREFIX = "textmode_";
    private static final String ORDER_PARAMETER_PREFIX = "order_";

    private static final int MIN_COLUMN_WIDTH = 60;
    private static final int MAX_COLUMN_WIDTH = 900;
    private static final int DEFAULT_COLUMN_WIDTH = 220;

    private static final int MIN_COLUMN_GAP = 0;
    private static final int MAX_COLUMN_GAP = 80;
    private static final int DEFAULT_COLUMN_GAP = 16;

    private static final int MIN_MAX_CHARACTERS = 1;
    private static final int MAX_MAX_CHARACTERS = 1000;

    private static final int MIN_COLUMN_ORDER = 1;
    private static final int MAX_COLUMN_ORDER = 9999;

    private static final String TEXT_MODE_ELLIPSIS = "ellipsis";
    private static final String TEXT_MODE_WRAP = "wrap";
    private static final String TEXT_MODE_CLIP = "clip";

    private static final Set<String> ALLOWED_TEXT_MODES = Set.of(
            TEXT_MODE_ELLIPSIS,
            TEXT_MODE_WRAP,
            TEXT_MODE_CLIP
    );

    private final DatabaseListBuilderService databaseListBuilderService;

    public AdminCustomListController(DatabaseListBuilderService databaseListBuilderService) {
        this.databaseListBuilderService = databaseListBuilderService;
    }

    @GetMapping
    public String showBuilder(
            @RequestParam(name = "table", required = false) String tableKey,
            Authentication authentication,
            Model model
    ) {
        requireAdministrator(authentication);
        preparePage(
                model,
                tableKey,
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
        return "admin/list-builder";
    }

    @PostMapping("/preview")
    public String buildPreview(
            @RequestParam("table") String tableKey,
            @RequestParam(name = "selectedColumns", required = false) List<String> selectedColumns,
            @RequestParam MultiValueMap<String, String> requestParameters,
            Authentication authentication,
            Model model
    ) {
        requireAdministrator(authentication);

        List<String> safeSelectedColumns = selectedColumns == null ? List.of() : List.copyOf(selectedColumns);
        Map<String, String> aliases = extractParameters(requestParameters, ALIAS_PARAMETER_PREFIX);
        Map<String, String> relationSelections = extractParameters(
                requestParameters,
                RELATION_PARAMETER_PREFIX
        );
        Map<String, Integer> columnWidths = extractBoundedIntegerParameters(
                requestParameters,
                WIDTH_PARAMETER_PREFIX,
                MIN_COLUMN_WIDTH,
                MAX_COLUMN_WIDTH
        );
        Map<String, Integer> columnGaps = extractBoundedIntegerParameters(
                requestParameters,
                GAP_PARAMETER_PREFIX,
                MIN_COLUMN_GAP,
                MAX_COLUMN_GAP
        );
        Map<String, Integer> maximumCharacters = extractBoundedIntegerParameters(
                requestParameters,
                MAX_CHARACTERS_PARAMETER_PREFIX,
                MIN_MAX_CHARACTERS,
                MAX_MAX_CHARACTERS
        );
        Map<String, String> textModes = extractTextModes(requestParameters);
        Map<String, Integer> columnOrders = extractBoundedIntegerParameters(
                requestParameters,
                ORDER_PARAMETER_PREFIX,
                MIN_COLUMN_ORDER,
                MAX_COLUMN_ORDER
        );
        List<String> orderedSelectedColumns = orderSelectedColumns(
                safeSelectedColumns,
                columnOrders
        );

        preparePage(
                model,
                tableKey,
                orderedSelectedColumns,
                aliases,
                relationSelections,
                columnWidths,
                columnGaps,
                maximumCharacters,
                textModes,
                columnOrders
        );

        try {
            DatabaseListResult rawResult = databaseListBuilderService.buildList(
                    tableKey,
                    orderedSelectedColumns,
                    aliases,
                    relationSelections
            );

            model.addAttribute(
                    "listResult",
                    configureResult(
                            rawResult,
                            columnWidths,
                            columnGaps,
                            maximumCharacters,
                            textModes
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("listBuilderError", ex.getMessage());
        }

        return "admin/list-builder";
    }

    private void preparePage(
            Model model,
            String tableKey,
            List<String> selectedColumns,
            Map<String, String> aliases,
            Map<String, String> relationSelections,
            Map<String, Integer> columnWidths,
            Map<String, Integer> columnGaps,
            Map<String, Integer> maximumCharacters,
            Map<String, String> textModes,
            Map<String, Integer> columnOrders
    ) {
        model.addAttribute("maximumRows", DatabaseListBuilderService.MAX_RESULT_ROWS);
        model.addAttribute("selectedColumns", selectedColumns);
        model.addAttribute("aliases", aliases);
        model.addAttribute("relationSelections", relationSelections == null ? Map.of() : relationSelections);
        model.addAttribute("columnWidths", columnWidths == null ? Map.of() : columnWidths);
        model.addAttribute("columnGaps", columnGaps == null ? Map.of() : columnGaps);
        model.addAttribute("maximumCharacters", maximumCharacters == null ? Map.of() : maximumCharacters);
        model.addAttribute("textModes", textModes == null ? Map.of() : textModes);
        model.addAttribute("columnOrders", columnOrders == null ? Map.of() : columnOrders);
        model.addAttribute("selectedTableKey", tableKey == null ? "" : tableKey);
        model.addAttribute("databaseColumns", List.of());

        List<DatabaseTable> tables;
        try {
            tables = databaseListBuilderService.findTables();
            model.addAttribute("databaseTables", tables);
        } catch (IllegalStateException ex) {
            model.addAttribute("databaseTables", List.of());
            model.addAttribute("listBuilderError", ex.getMessage());
            return;
        }

        if (tableKey == null || tableKey.isBlank()) {
            return;
        }

        try {
            DatabaseTable selectedTable = tables.stream()
                    .filter(table -> table.key().equals(tableKey.trim()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La tabella selezionata non è disponibile."
                    ));

            List<DatabaseColumn> columns = databaseListBuilderService.findColumns(tableKey);
            Map<String, String> effectiveRelationSelections = relationSelections == null
                    ? buildSuggestedRelationSelections(columns)
                    : relationSelections;
            Map<String, Integer> effectiveColumnWidths = mergeIntegerSettings(
                    buildSuggestedColumnWidths(columns),
                    columnWidths
            );
            Map<String, Integer> effectiveColumnGaps = mergeIntegerSettings(
                    buildSuggestedColumnGaps(columns),
                    columnGaps
            );
            Map<String, Integer> effectiveMaximumCharacters = maximumCharacters == null
                    ? Map.of()
                    : maximumCharacters;
            Map<String, String> effectiveTextModes = mergeTextSettings(
                    buildSuggestedTextModes(columns),
                    textModes
            );
            Map<String, Integer> effectiveColumnOrders = mergeIntegerSettings(
                    buildSuggestedColumnOrders(columns),
                    columnOrders
            );

            model.addAttribute("selectedTable", selectedTable);
            model.addAttribute("databaseColumns", columns);
            model.addAttribute("relationSelections", effectiveRelationSelections);
            model.addAttribute("columnWidths", effectiveColumnWidths);
            model.addAttribute("columnGaps", effectiveColumnGaps);
            model.addAttribute("maximumCharacters", effectiveMaximumCharacters);
            model.addAttribute("textModes", effectiveTextModes);
            model.addAttribute("columnOrders", effectiveColumnOrders);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("listBuilderError", ex.getMessage());
        }
    }

    private ConfiguredListResult configureResult(
            DatabaseListResult rawResult,
            Map<String, Integer> requestedColumnWidths,
            Map<String, Integer> requestedColumnGaps,
            Map<String, Integer> requestedMaximumCharacters,
            Map<String, String> requestedTextModes
    ) {
        List<ConfiguredColumn> configuredColumns = new ArrayList<>();

        for (int index = 0; index < rawResult.selectedColumns().size(); index++) {
            String sourceName = rawResult.selectedColumns().get(index);
            String header = rawResult.headers().get(index);
            int width = requestedColumnWidths == null
                    ? DEFAULT_COLUMN_WIDTH
                    : requestedColumnWidths.getOrDefault(sourceName, DEFAULT_COLUMN_WIDTH);
            int gap = requestedColumnGaps == null
                    ? DEFAULT_COLUMN_GAP
                    : requestedColumnGaps.getOrDefault(sourceName, DEFAULT_COLUMN_GAP);
            int maxCharacters = requestedMaximumCharacters == null
                    ? 0
                    : requestedMaximumCharacters.getOrDefault(sourceName, 0);
            String textMode = normalizeTextMode(
                    requestedTextModes == null
                            ? null
                            : requestedTextModes.get(sourceName)
            );

            configuredColumns.add(createConfiguredColumn(
                    sourceName,
                    header,
                    width,
                    gap,
                    maxCharacters,
                    textMode
            ));
        }

        List<List<ConfiguredCell>> configuredRows = new ArrayList<>();
        for (List<DatabaseCell> rawRow : rawResult.rows()) {
            List<ConfiguredCell> configuredRow = new ArrayList<>(rawRow.size());

            for (int index = 0; index < rawRow.size(); index++) {
                DatabaseCell rawCell = rawRow.get(index);
                ConfiguredColumn configuredColumn = configuredColumns.get(index);
                configuredRow.add(configureCell(rawCell, configuredColumn));
            }

            configuredRows.add(List.copyOf(configuredRow));
        }

        return new ConfiguredListResult(
                rawResult.table(),
                List.copyOf(configuredColumns),
                List.copyOf(configuredRows),
                rawResult.truncated(),
                rawResult.maximumRows()
        );
    }

    private ConfiguredColumn createConfiguredColumn(
            String sourceName,
            String header,
            int width,
            int gap,
            int maxCharacters,
            String textMode
    ) {
        String widthStyle = "width:" + width + "px;";
        String cellStyle = widthStyle
                + "min-width:" + width + "px;"
                + "max-width:" + width + "px;"
                + "padding-right:" + gap + "px;";
        String textCssClass = switch (textMode) {
            case TEXT_MODE_WRAP -> "list-cell-text--wrap";
            case TEXT_MODE_CLIP -> "list-cell-text--clip";
            default -> "list-cell-text--ellipsis";
        };

        return new ConfiguredColumn(
                sourceName,
                header,
                width,
                gap,
                maxCharacters,
                textMode,
                widthStyle,
                cellStyle,
                textCssClass
        );
    }

    private ConfiguredCell configureCell(DatabaseCell rawCell, ConfiguredColumn configuredColumn) {
        if (rawCell.value() == null) {
            return new ConfiguredCell(
                    configuredColumn,
                    null,
                    null,
                    rawCell.image(),
                    rawCell.imageUrl(),
                    false
            );
        }

        String rawText = String.valueOf(rawCell.value());
        if (rawCell.image()) {
            return new ConfiguredCell(
                    configuredColumn,
                    rawText,
                    rawText,
                    true,
                    rawCell.imageUrl(),
                    false
            );
        }

        TruncatedText truncatedText = truncateText(rawText, configuredColumn.maxCharacters());
        return new ConfiguredCell(
                configuredColumn,
                rawText,
                truncatedText.value(),
                false,
                null,
                truncatedText.truncated()
        );
    }

    private TruncatedText truncateText(String value, int maximumCharacters) {
        if (value == null || maximumCharacters <= 0) {
            return new TruncatedText(value, false);
        }

        int characterCount = value.codePointCount(0, value.length());
        if (characterCount <= maximumCharacters) {
            return new TruncatedText(value, false);
        }

        int endIndex = value.offsetByCodePoints(0, maximumCharacters);
        String shortenedValue = value.substring(0, endIndex).stripTrailing() + "…";
        return new TruncatedText(shortenedValue, true);
    }

    private Map<String, String> buildSuggestedRelationSelections(List<DatabaseColumn> columns) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (DatabaseColumn column : columns) {
            if (column.relation() == null) {
                continue;
            }

            String suggestedDisplayColumn = column.relation().suggestedDisplayColumn();
            if (suggestedDisplayColumn != null && !suggestedDisplayColumn.isBlank()) {
                suggestions.put(column.name(), suggestedDisplayColumn);
            }
        }
        return Map.copyOf(suggestions);
    }

    private Map<String, Integer> buildSuggestedColumnWidths(List<DatabaseColumn> columns) {
        Map<String, Integer> suggestions = new LinkedHashMap<>();
        for (DatabaseColumn column : columns) {
            suggestions.put(column.name(), suggestColumnWidth(column));
        }
        return Map.copyOf(suggestions);
    }

    private Map<String, Integer> buildSuggestedColumnGaps(List<DatabaseColumn> columns) {
        Map<String, Integer> suggestions = new LinkedHashMap<>();
        for (DatabaseColumn column : columns) {
            suggestions.put(column.name(), DEFAULT_COLUMN_GAP);
        }
        return Map.copyOf(suggestions);
    }

    private Map<String, String> buildSuggestedTextModes(List<DatabaseColumn> columns) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (DatabaseColumn column : columns) {
            suggestions.put(column.name(), TEXT_MODE_ELLIPSIS);
        }
        return Map.copyOf(suggestions);
    }

    private Map<String, Integer> buildSuggestedColumnOrders(List<DatabaseColumn> columns) {
        Map<String, Integer> suggestions = new LinkedHashMap<>();
        for (DatabaseColumn column : columns) {
            suggestions.put(column.name(), column.ordinalPosition());
        }
        return Map.copyOf(suggestions);
    }

    private List<String> orderSelectedColumns(
            List<String> selectedColumns,
            Map<String, Integer> requestedOrders
    ) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> originalPositions = new LinkedHashMap<>();
        for (String columnName : selectedColumns) {
            if (columnName != null && !columnName.isBlank()) {
                originalPositions.putIfAbsent(columnName, originalPositions.size());
            }
        }

        List<String> orderedColumns = new ArrayList<>(originalPositions.keySet());
        orderedColumns.sort(Comparator
                .comparingInt((String columnName) -> requestedOrders == null
                        ? Integer.MAX_VALUE
                        : requestedOrders.getOrDefault(columnName, Integer.MAX_VALUE))
                .thenComparingInt(originalPositions::get));

        return List.copyOf(orderedColumns);
    }

    private int suggestColumnWidth(DatabaseColumn column) {
        String normalizedName = column.name()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        String normalizedType = column.typeName().toUpperCase(Locale.ROOT);

        if (isImageColumn(normalizedName)) {
            return 120;
        }

        if (column.relation() == null
                && (normalizedName.equals("id") || normalizedName.endsWith("id"))) {
            return 100;
        }

        if (normalizedType.contains("BOOL")) {
            return 90;
        }

        if (normalizedType.contains("DATE") || normalizedType.contains("TIME")) {
            return 160;
        }

        if (isNumericType(normalizedType)) {
            return 140;
        }

        if (isTextType(normalizedType)) {
            return 300;
        }

        return DEFAULT_COLUMN_WIDTH;
    }

    private boolean isImageColumn(String normalizedName) {
        return normalizedName.contains("image")
                || normalizedName.contains("photo")
                || normalizedName.contains("foto")
                || normalizedName.contains("thumbnail")
                || normalizedName.contains("avatar")
                || normalizedName.contains("picture")
                || normalizedName.contains("poster")
                || normalizedName.contains("logo");
    }

    private boolean isNumericType(String normalizedType) {
        return normalizedType.contains("INT")
                || normalizedType.contains("NUMERIC")
                || normalizedType.contains("DECIMAL")
                || normalizedType.contains("DOUBLE")
                || normalizedType.contains("FLOAT")
                || normalizedType.contains("REAL")
                || normalizedType.contains("MONEY");
    }

    private boolean isTextType(String normalizedType) {
        return normalizedType.contains("CHAR")
                || normalizedType.contains("TEXT")
                || normalizedType.contains("CLOB")
                || normalizedType.contains("JSON")
                || normalizedType.contains("XML");
    }

    private Map<String, Integer> mergeIntegerSettings(
            Map<String, Integer> defaults,
            Map<String, Integer> requestedValues
    ) {
        Map<String, Integer> mergedValues = new LinkedHashMap<>(defaults);
        if (requestedValues != null) {
            mergedValues.putAll(requestedValues);
        }
        return Map.copyOf(mergedValues);
    }

    private Map<String, String> mergeTextSettings(
            Map<String, String> defaults,
            Map<String, String> requestedValues
    ) {
        Map<String, String> mergedValues = new LinkedHashMap<>(defaults);
        if (requestedValues != null) {
            requestedValues.forEach((columnName, textMode) ->
                    mergedValues.put(columnName, normalizeTextMode(textMode))
            );
        }
        return Map.copyOf(mergedValues);
    }

    private Map<String, Integer> extractBoundedIntegerParameters(
            MultiValueMap<String, String> requestParameters,
            String parameterPrefix,
            int minimumValue,
            int maximumValue
    ) {
        Map<String, String> requestedValues = extractParameters(requestParameters, parameterPrefix);
        Map<String, Integer> safeValues = new LinkedHashMap<>();

        requestedValues.forEach((columnName, requestedValue) -> {
            if (requestedValue == null || requestedValue.isBlank()) {
                return;
            }

            try {
                int numericValue = Integer.parseInt(requestedValue);
                if (numericValue >= minimumValue && numericValue <= maximumValue) {
                    safeValues.put(columnName, numericValue);
                }
            } catch (NumberFormatException ignored) {
                // Il valore viene ignorato e sostituito dal valore predefinito sicuro.
            }
        });

        return Map.copyOf(safeValues);
    }

    private Map<String, String> extractTextModes(MultiValueMap<String, String> requestParameters) {
        Map<String, String> requestedModes = extractParameters(
                requestParameters,
                TEXT_MODE_PARAMETER_PREFIX
        );
        Map<String, String> safeModes = new LinkedHashMap<>();

        requestedModes.forEach((columnName, textMode) ->
                safeModes.put(columnName, normalizeTextMode(textMode))
        );

        return Map.copyOf(safeModes);
    }

    private String normalizeTextMode(String textMode) {
        String normalizedMode = textMode == null
                ? ""
                : textMode.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_TEXT_MODES.contains(normalizedMode)
                ? normalizedMode
                : TEXT_MODE_ELLIPSIS;
    }

    private Map<String, String> extractParameters(
            MultiValueMap<String, String> requestParameters,
            String parameterPrefix
    ) {
        MultiValueMap<String, String> safeParameters = requestParameters == null
                ? new LinkedMultiValueMap<>()
                : requestParameters;

        Map<String, String> extractedValues = new LinkedHashMap<>();
        safeParameters.forEach((parameterName, values) -> {
            if (!parameterName.startsWith(parameterPrefix)) {
                return;
            }

            String columnName = parameterName.substring(parameterPrefix.length());
            String value = values == null || values.isEmpty() ? "" : values.get(0);
            extractedValues.put(columnName, value == null ? "" : value.trim());
        });
        return Map.copyOf(extractedValues);
    }

    private void requireAdministrator(Authentication authentication) {
        boolean administrator = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!administrator) {
            throw new AccessDeniedException("Tool lista personalizzata consentito solo agli amministratori");
        }
    }

    public record ConfiguredColumn(
            String sourceName,
            String header,
            int width,
            int gap,
            int maxCharacters,
            String textMode,
            String widthStyle,
            String cellStyle,
            String textCssClass
    ) {
    }

    public record ConfiguredCell(
            ConfiguredColumn column,
            String rawText,
            String displayText,
            boolean image,
            String imageUrl,
            boolean characterTruncated
    ) {
    }

    public record ConfiguredListResult(
            DatabaseTable table,
            List<ConfiguredColumn> columns,
            List<List<ConfiguredCell>> rows,
            boolean truncated,
            int maximumRows
    ) {
        public int rowCount() {
            return rows.size();
        }
    }

    private record TruncatedText(
            String value,
            boolean truncated
    ) {
    }
}
