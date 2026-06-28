package com.delta.store.product.importer;

import com.delta.store.product.model.ColorOption;
import com.delta.store.product.repository.ColorOptionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class ColorOptionCsvImporter implements CommandLineRunner {

    private static final String DEFAULT_CSV_PATH = "import/colors.csv";

    private final ColorOptionRepository colorOptionRepository;

    @Value("${app.import.colors.enabled:false}")
    private boolean importEnabled;

    @Value("${app.import.colors.path:" + DEFAULT_CSV_PATH + "}")
    private String csvPath;

    public ColorOptionCsvImporter(ColorOptionRepository colorOptionRepository) {
        this.colorOptionRepository = colorOptionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!importEnabled) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(csvPath);

        if (!resource.exists()) {
            System.out.println("[COLOR IMPORT] File CSV non trovato: " + csvPath);
            return;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = 0;

            while ((line = reader.readLine()) != null) {
                rowNumber++;

                if (rowNumber == 1 && isHeader(line)) {
                    continue;
                }

                if (line.isBlank()) {
                    skipped++;
                    continue;
                }

                String[] columns = line.split(";", -1);

                if (columns.length < 4) {
                    System.out.println("[COLOR IMPORT] Riga " + rowNumber + " saltata: colonne insufficienti.");
                    skipped++;
                    continue;
                }

                String code = normalizeCode(columns[0]);
                String nameIt = normalizeName(columns[1]);
                String hex = normalizeHex(columns[2]);
                String rgb = normalizeRgb(columns[3]);

                if (code == null || nameIt == null) {
                    System.out.println("[COLOR IMPORT] Riga " + rowNumber + " saltata: codice o nome mancante.");
                    skipped++;
                    continue;
                }

                ColorOption color = colorOptionRepository
                        .findByCodeIgnoreCase(code)
                        .orElseGet(ColorOption::new);

                boolean isNew = color.getId() == null;

                color.setCode(code);
                color.setNameIt(nameIt);
                color.setHex(hex);
                color.setRgb(rgb);

                colorOptionRepository.save(color);

                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }

        System.out.println("[COLOR IMPORT] Completato. Inseriti: "
                + inserted + ", aggiornati: " + updated + ", saltati: " + skipped);
    }

    private boolean isHeader(String line) {
        String value = line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("codice;") || value.contains("nome italiano");
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace(" ", "_");
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeHex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String hex = value.trim().toUpperCase(Locale.ROOT);

        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }

        if (!hex.matches("^#[0-9A-F]{6}$")) {
            throw new IllegalArgumentException("Valore HEX non valido: " + value);
        }

        return hex;
    }

    private String normalizeRgb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String rgb = value.trim().replace(" ", "");

        if (!rgb.matches("^\\d{1,3},\\d{1,3},\\d{1,3}$")) {
            throw new IllegalArgumentException("Valore RGB non valido: " + value);
        }

        String[] parts = rgb.split(",");

        for (String part : parts) {
            int number = Integer.parseInt(part);
            if (number < 0 || number > 255) {
                throw new IllegalArgumentException("Valore RGB fuori range: " + value);
            }
        }

        return rgb;
    }
}