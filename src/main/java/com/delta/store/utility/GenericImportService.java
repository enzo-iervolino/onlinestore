package com.delta.store.utility;

import com.delta.store.product.dto.ProductDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class GenericImportService {

    private final ObjectMapper objectMapper;
    private final SampleProductImportMapper mapper;

    public GenericImportService(ObjectMapper objectMapper,
                                SampleProductImportMapper mapper) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    public ImportResult previewFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Nome file non valido");
        }

        String lowerFilename = filename.toLowerCase(Locale.ROOT);

        if (lowerFilename.endsWith(".csv")) {
            return previewCsv(file, ";");
        }

        if (lowerFilename.endsWith(".txt")) {
            return previewTxt(file);
        }

        if (lowerFilename.endsWith(".json")) {
            return previewJson(file);
        }

        throw new IllegalArgumentException("Formato non supportato. Usa .csv, .txt oppure .json");
    }

    private ImportResult previewCsv(MultipartFile file, String separator) throws IOException {
        ImportResult result = new ImportResult();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = 0;

            while ((line = reader.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) {
                    continue;
                }

                if (rowNumber == 1 && line.toLowerCase(Locale.ROOT).contains("codpro")) {
                    continue;
                }

                result.incrementTotalRows();

                try {
                    String[] columns = line.split(separator, -1);
                    ProductDTO dto = mapper.fromColumns(columns, rowNumber);

                    result.addImportedItem(dto);
                    result.incrementImportedRows();
                } catch (Exception ex) {
                    result.incrementSkippedRows();
                    result.addError("Riga CSV " + rowNumber + ": " + ex.getMessage());
                }
            }
        }

        return result;
    }

    private ImportResult previewTxt(MultipartFile file) throws IOException {
        ImportResult result = new ImportResult();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int rowNumber = 0;

            while ((line = reader.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) {
                    continue;
                }

                result.incrementTotalRows();

                try {
                    String[] columns = line.split(";", -1);
                    ProductDTO dto = mapper.fromColumns(columns, rowNumber);

                    result.addImportedItem(dto);
                    result.incrementImportedRows();
                } catch (Exception ex) {
                    result.incrementSkippedRows();
                    result.addError("Riga TXT " + rowNumber + ": " + ex.getMessage());
                }
            }
        }

        return result;
    }

    private ImportResult previewJson(MultipartFile file) throws IOException {
        ImportResult result = new ImportResult();

        JsonNode root = objectMapper.readTree(file.getInputStream());

        if (!root.isArray()) {
            throw new IllegalArgumentException("Il file JSON deve contenere un array di oggetti");
        }

        int rowNumber = 0;

        for (JsonNode node : root) {
            rowNumber++;
            result.incrementTotalRows();

            try {
                ProductDTO dto = mapper.fromJsonNode(node, rowNumber);

                result.addImportedItem(dto);
                result.incrementImportedRows();
            } catch (Exception ex) {
                result.incrementSkippedRows();
                result.addError("Elemento JSON " + rowNumber + ": " + ex.getMessage());
            }
        }

        return result;
    }
}