package com.delta.store.product.importer;

import com.delta.store.product.model.SizeOption;
import com.delta.store.product.repository.SizeOptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class SizeOptionCsvImporter implements CommandLineRunner {

    private static final String DEFAULT_CSV_PATH = "import/sizes.csv";

    private final SizeOptionRepository sizeOptionRepository;

    @Value("${app.import.sizes.enabled:false}")
    private boolean importEnabled;

    @Value("${app.import.sizes.path:" + DEFAULT_CSV_PATH + "}")
    private String csvPath;

    public SizeOptionCsvImporter(SizeOptionRepository sizeOptionRepository) {
        this.sizeOptionRepository = sizeOptionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!importEnabled) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(csvPath);

        if (!resource.exists()) {
            System.out.println("[SIZE IMPORT] File CSV non trovato: " + csvPath);
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

                if (columns.length < 2) {
                    System.out.println("[SIZE IMPORT] Riga " + rowNumber + " saltata: colonne insufficienti.");
                    skipped++;
                    continue;
                }

                String code = normalizeCode(columns[0]);
                String nameIt = normalizeName(columns[1]);

                if (code == null || nameIt == null) {
                    System.out.println("[SIZE IMPORT] Riga " + rowNumber + " saltata: codice o nome mancante.");
                    skipped++;
                    continue;
                }

                SizeOption size = sizeOptionRepository
                        .findByCodeIgnoreCase(code)
                        .orElseGet(SizeOption::new);

                boolean isNew = size.getId() == null;

                size.setCode(code);
                size.setNameIt(nameIt);

                sizeOptionRepository.save(size);

                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }

        System.out.println("[SIZE IMPORT] Completato. Inserite: "
                + inserted + ", aggiornate: " + updated + ", saltate: " + skipped);
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
                .replace(",", ".")
                .replace(" ", "_");
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}