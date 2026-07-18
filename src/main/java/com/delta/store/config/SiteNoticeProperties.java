package com.delta.store.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class SiteNoticeProperties {

    public static final int MAX_TEXT_LENGTH = 240;

    private static final Logger log = LoggerFactory.getLogger(SiteNoticeProperties.class);
    private static final String DEFAULT_CONFIGURATION_RESOURCE = "default-config/site-notice.json";

    private final ObjectMapper objectMapper;
    private final Path configurationPath;
    private final String configurationDisplayPath;

    private volatile SiteNoticeDocument document = new SiteNoticeDocument();
    private volatile long lastModifiedMillis;

    public SiteNoticeProperties(
            ObjectMapper objectMapper,
            @Value("${app.site.notice.file:config/site/site-notice.json}") String configurationFile
    ) {
        this.objectMapper = objectMapper;
        Path configuredPath = Path.of(configurationFile).normalize();
        this.configurationPath = configuredPath.toAbsolutePath().normalize();
        this.configurationDisplayPath = configuredPath.toString().replace('\\', '/');
    }

    @PostConstruct
    public synchronized void initialize() {
        try {
            ensureConfigurationFileExists();
            reload();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Impossibile inizializzare l'avviso del sito dal file " + configurationPath,
                    ex
            );
        }
    }

    public SiteNoticeView getView() {
        refreshIfChanged();
        SiteNoticeDocument snapshot = document;
        return new SiteNoticeView(snapshot.isEnabled(), snapshot.getText());
    }

    public String getConfigurationDisplayPath() {
        return configurationDisplayPath;
    }

    public synchronized void update(String text, boolean enabled) throws IOException {
        String normalizedText = normalizeText(text);

        if (normalizedText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "La frase non può superare " + MAX_TEXT_LENGTH + " caratteri"
            );
        }

        if (enabled && normalizedText.isBlank()) {
            throw new IllegalArgumentException(
                    "Inserisci una frase prima di attivare l'avviso"
            );
        }

        SiteNoticeDocument updatedDocument = new SiteNoticeDocument();
        updatedDocument.setEnabled(enabled);
        updatedDocument.setText(normalizedText);

        persist(updatedDocument);
        document = updatedDocument;
        lastModifiedMillis = Files.getLastModifiedTime(configurationPath).toMillis();

        log.info("Configurazione avviso sito aggiornata in {}", configurationPath);
    }

    private synchronized void reload() throws IOException {
        try (InputStream inputStream = Files.newInputStream(configurationPath)) {
            SiteNoticeDocument loadedDocument = objectMapper.readValue(inputStream, SiteNoticeDocument.class);
            normalizeAndValidate(loadedDocument);
            document = loadedDocument;
            lastModifiedMillis = Files.getLastModifiedTime(configurationPath).toMillis();
            log.info("Configurazione avviso sito caricata da {}", configurationPath);
        }
    }

    private void refreshIfChanged() {
        try {
            if (!Files.exists(configurationPath)) {
                synchronized (this) {
                    ensureConfigurationFileExists();
                    reload();
                }
                return;
            }

            long currentLastModified = Files.getLastModifiedTime(configurationPath).toMillis();
            if (currentLastModified == lastModifiedMillis) {
                return;
            }

            synchronized (this) {
                currentLastModified = Files.getLastModifiedTime(configurationPath).toMillis();
                if (currentLastModified != lastModifiedMillis) {
                    reload();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.error(
                    "Il file avviso {} è stato modificato ma non è valido. Resta attiva l'ultima configurazione corretta.",
                    configurationPath,
                    ex
            );
        }
    }

    private void ensureConfigurationFileExists() throws IOException {
        if (Files.exists(configurationPath)) {
            return;
        }

        Path parentDirectory = configurationPath.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        ClassPathResource defaultConfiguration = new ClassPathResource(DEFAULT_CONFIGURATION_RESOURCE);
        if (!defaultConfiguration.exists()) {
            throw new IOException("Configurazione predefinita non trovata: " + DEFAULT_CONFIGURATION_RESOURCE);
        }

        try (InputStream inputStream = defaultConfiguration.getInputStream()) {
            Files.copy(inputStream, configurationPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Creato il file avviso predefinito in {}", configurationPath);
    }

    private void persist(SiteNoticeDocument updatedDocument) throws IOException {
        Path parentDirectory = configurationPath.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        Path temporaryDirectory = parentDirectory != null
                ? parentDirectory
                : Path.of(".").toAbsolutePath().normalize();
        Path temporaryFile = Files.createTempFile(temporaryDirectory, "site-notice-", ".tmp");

        try {
            try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, updatedDocument);
            }

            try {
                Files.move(
                        temporaryFile,
                        configurationPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(
                        temporaryFile,
                        configurationPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void normalizeAndValidate(SiteNoticeDocument loadedDocument) {
        if (loadedDocument == null) {
            throw new IllegalArgumentException("Il file avviso non contiene una configurazione valida");
        }

        String normalizedText = normalizeText(loadedDocument.getText());
        if (normalizedText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "La frase configurata supera " + MAX_TEXT_LENGTH + " caratteri"
            );
        }

        if (loadedDocument.isEnabled() && normalizedText.isBlank()) {
            loadedDocument.setEnabled(false);
        }

        loadedDocument.setText(normalizedText);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    public static class SiteNoticeDocument {

        private boolean enabled;
        private String text = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text == null ? "" : text;
        }
    }

    public static class SiteNoticeView {

        private final boolean enabled;
        private final String text;

        public SiteNoticeView(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text == null ? "" : text;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getText() {
            return text;
        }

        public boolean isVisible() {
            return enabled && !text.isBlank();
        }
    }
}
