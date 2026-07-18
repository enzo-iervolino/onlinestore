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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AdminMenuProperties {

    private static final Logger log = LoggerFactory.getLogger(AdminMenuProperties.class);
    private static final String DEFAULT_CONFIGURATION_RESOURCE = "default-config/admin-menu.json";
    private static final Set<String> ALWAYS_ENABLED_ITEM_IDS = Set.of(
            "settings",
            "menu-configuration"
    );
    private static final Comparator<MenuItem> MENU_ORDER = Comparator
            .comparingInt(MenuItem::getOrder)
            .thenComparing(MenuItem::getLabel, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MenuItem::getId, String.CASE_INSENSITIVE_ORDER);

    private final ObjectMapper objectMapper;
    private final Path configurationPath;
    private final String configurationDisplayPath;

    private volatile MenuDocument menuDocument = new MenuDocument();
    private volatile long lastModifiedMillis;

    public AdminMenuProperties(ObjectMapper objectMapper,
                               @Value("${app.admin.menu.file:config/menu/admin-menu.json}") String configurationFile) {
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
                    "Impossibile inizializzare il menu amministratore dal file " + configurationPath,
                    ex
            );
        }
    }

    public synchronized void reload() throws IOException {
        try (InputStream inputStream = Files.newInputStream(configurationPath)) {
            MenuDocument loadedDocument = objectMapper.readValue(inputStream, MenuDocument.class);
            normalizeAndValidate(loadedDocument);
            menuDocument = loadedDocument;
            lastModifiedMillis = Files.getLastModifiedTime(configurationPath).toMillis();
            log.info("Menu amministratore caricato da {} con {} elementi",
                    configurationPath,
                    loadedDocument.getItems().size());
        }
    }

    public synchronized void updateEnabled(Collection<String> enabledItemIds) throws IOException {
        refreshIfChanged();

        Set<String> enabledIds = enabledItemIds == null
                ? new HashSet<>()
                : new HashSet<>(enabledItemIds);
        enabledIds.addAll(ALWAYS_ENABLED_ITEM_IDS);

        MenuDocument updatedDocument = copyDocument(menuDocument);
        for (MenuItem item : updatedDocument.getItems()) {
            item.setEnabled(enabledIds.contains(item.getId()));
        }

        normalizeAndValidate(updatedDocument);
        persist(updatedDocument);
        menuDocument = updatedDocument;
        lastModifiedMillis = Files.getLastModifiedTime(configurationPath).toMillis();

        log.info("Configurazione menu amministratore aggiornata in {}", configurationPath);
    }

    public List<MenuNode> visibleMenu(boolean admin) {
        refreshIfChanged();
        MenuDocument snapshot = menuDocument;
        Map<String, MenuItem> itemsById = indexById(snapshot.getItems());
        Map<String, List<MenuItem>> childrenByParent = new HashMap<>();
        List<MenuItem> rootItems = new ArrayList<>();

        for (MenuItem item : snapshot.getItems()) {
            if (isBlank(item.getParentId())) {
                rootItems.add(item);
            } else {
                childrenByParent
                        .computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        rootItems.sort(MENU_ORDER);
        childrenByParent.values().forEach(children -> children.sort(MENU_ORDER));

        List<MenuNode> result = new ArrayList<>();
        for (MenuItem rootItem : rootItems) {
            MenuNode node = buildVisibleNode(rootItem, admin, itemsById, childrenByParent);
            if (node != null) {
                result.add(node);
            }
        }

        return List.copyOf(result);
    }

    public List<MenuItem> visibleQuickLinks(boolean admin) {
        refreshIfChanged();
        MenuDocument snapshot = menuDocument;
        Map<String, MenuItem> itemsById = indexById(snapshot.getItems());

        return snapshot.getItems().stream()
                .filter(MenuItem::isQuickLink)
                .filter(MenuItem::isLink)
                .filter(item -> isVisible(item, admin, itemsById))
                .sorted(MENU_ORDER)
                .map(this::copyItem)
                .toList();
    }

    public boolean isVisible(String itemId, boolean admin) {
        refreshIfChanged();

        if (isBlank(itemId)) {
            return false;
        }

        MenuDocument snapshot = menuDocument;
        Map<String, MenuItem> itemsById = indexById(snapshot.getItems());
        MenuItem item = itemsById.get(itemId);
        return item != null && isVisible(item, admin, itemsById);
    }

    public List<MenuItem> getConfigurationItems() {
        refreshIfChanged();
        return menuDocument.getItems().stream()
                .sorted(MENU_ORDER)
                .map(this::copyItem)
                .toList();
    }

    public String getConfigurationDisplayPath() {
        return configurationDisplayPath;
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
                    "Il file menu {} è stato modificato ma non è valido. Resta attiva l'ultima configurazione corretta.",
                    configurationPath,
                    ex
            );
        }
    }

    private MenuNode buildVisibleNode(MenuItem item,
                                      boolean admin,
                                      Map<String, MenuItem> itemsById,
                                      Map<String, List<MenuItem>> childrenByParent) {
        if (!isVisible(item, admin, itemsById)) {
            return null;
        }

        List<MenuNode> visibleChildren = new ArrayList<>();
        for (MenuItem child : childrenByParent.getOrDefault(item.getId(), List.of())) {
            MenuNode childNode = buildVisibleNode(child, admin, itemsById, childrenByParent);
            if (childNode != null) {
                visibleChildren.add(childNode);
            }
        }

        if (item.isGroup() && visibleChildren.isEmpty()) {
            return null;
        }

        return new MenuNode(copyItem(item), List.copyOf(visibleChildren));
    }

    private boolean isVisible(MenuItem item,
                              boolean admin,
                              Map<String, MenuItem> itemsById) {
        if (!item.isEnabled() || (item.isAdminOnly() && !admin)) {
            return false;
        }

        String parentId = item.getParentId();
        while (!isBlank(parentId)) {
            MenuItem parent = itemsById.get(parentId);
            if (parent == null || !parent.isEnabled() || (parent.isAdminOnly() && !admin)) {
                return false;
            }
            parentId = parent.getParentId();
        }

        return true;
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

        log.info("Creato il file menu amministratore predefinito in {}", configurationPath);
    }

    private void persist(MenuDocument document) throws IOException {
        Path parentDirectory = configurationPath.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        Path temporaryDirectory = parentDirectory != null
                ? parentDirectory
                : Path.of(".").toAbsolutePath().normalize();
        Path temporaryFile = Files.createTempFile(temporaryDirectory, "admin-menu-", ".tmp");

        try {
            try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, document);
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

    private void normalizeAndValidate(MenuDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Il file menu non contiene una configurazione valida");
        }

        if (document.getItems() == null || document.getItems().isEmpty()) {
            throw new IllegalArgumentException("Il file menu deve contenere almeno una voce");
        }

        Map<String, MenuItem> itemsById = new LinkedHashMap<>();
        for (MenuItem item : document.getItems()) {
            normalize(item);

            if (!item.getId().matches("[a-z0-9][a-z0-9._-]*")) {
                throw new IllegalArgumentException("Identificativo menu non valido: " + item.getId());
            }
            if (itemsById.putIfAbsent(item.getId(), item) != null) {
                throw new IllegalArgumentException("Identificativo menu duplicato: " + item.getId());
            }
            if (item.isLink() && !item.getUrl().startsWith("/")) {
                throw new IllegalArgumentException(
                        "Il link della voce " + item.getId() + " deve iniziare con /"
                );
            }
        }

        for (MenuItem item : document.getItems()) {
            if (isBlank(item.getParentId())) {
                continue;
            }

            MenuItem parent = itemsById.get(item.getParentId());
            if (parent == null) {
                throw new IllegalArgumentException(
                        "Menu padre non trovato per " + item.getId() + ": " + item.getParentId()
                );
            }
            if (!parent.isGroup()) {
                throw new IllegalArgumentException(
                        "La voce padre di " + item.getId() + " deve essere di tipo GROUP"
                );
            }
        }

        validateNoCycles(itemsById);
    }

    private void normalize(MenuItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Il file menu contiene una voce nulla");
        }

        item.setId(clean(item.getId()).toLowerCase(Locale.ROOT));
        item.setParentId(clean(item.getParentId()).toLowerCase(Locale.ROOT));
        item.setMenu(clean(item.getMenu()));
        item.setLabel(clean(item.getLabel()));
        item.setUrl(clean(item.getUrl()));
        item.setIcon(clean(item.getIcon()));
        item.setDescription(clean(item.getDescription()));

        if (isBlank(item.getId())) {
            throw new IllegalArgumentException("Ogni voce menu deve avere un identificativo");
        }
        if (isBlank(item.getLabel())) {
            throw new IllegalArgumentException("La voce " + item.getId() + " deve avere un'etichetta");
        }
        if (item.getType() == null) {
            item.setType(MenuItemType.LINK);
        }
        if (isBlank(item.getMenu())) {
            item.setMenu("Principale");
        }
        if (isBlank(item.getIcon())) {
            item.setIcon("bi-circle");
        }
        if (!item.isLink()) {
            item.setUrl("");
            item.setQuickLink(false);
        }
    }

    private void validateNoCycles(Map<String, MenuItem> itemsById) {
        for (MenuItem item : itemsById.values()) {
            Set<String> visited = new HashSet<>();
            String currentId = item.getId();

            while (!isBlank(currentId)) {
                if (!visited.add(currentId)) {
                    throw new IllegalArgumentException("Riferimento circolare nel menu: " + item.getId());
                }

                MenuItem current = itemsById.get(currentId);
                currentId = current == null ? "" : current.getParentId();
            }
        }
    }

    private Map<String, MenuItem> indexById(List<MenuItem> items) {
        Map<String, MenuItem> result = new HashMap<>();
        for (MenuItem item : items) {
            result.put(item.getId(), item);
        }
        return result;
    }

    private MenuDocument copyDocument(MenuDocument source) {
        MenuDocument copy = new MenuDocument();
        copy.setVersion(source.getVersion());
        copy.setItems(source.getItems().stream().map(this::copyItem).toList());
        return copy;
    }

    private MenuItem copyItem(MenuItem source) {
        MenuItem copy = new MenuItem();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setParentId(source.getParentId());
        copy.setMenu(source.getMenu());
        copy.setLabel(source.getLabel());
        copy.setUrl(source.getUrl());
        copy.setIcon(source.getIcon());
        copy.setDescription(source.getDescription());
        copy.setEnabled(source.isEnabled());
        copy.setAdminOnly(source.isAdminOnly());
        copy.setQuickLink(source.isQuickLink());
        copy.setOrder(source.getOrder());
        return copy;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum MenuItemType {
        GROUP,
        LINK,
        PLACEHOLDER
    }

    public static class MenuDocument {

        private int version = 1;
        private List<MenuItem> items = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<MenuItem> getItems() {
            return items;
        }

        public void setItems(List<MenuItem> items) {
            this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        }
    }

    public static class MenuItem {

        private String id = "";
        private MenuItemType type = MenuItemType.LINK;
        private String parentId = "";
        private String menu = "";
        private String label = "";
        private String url = "";
        private String icon = "";
        private String description = "";
        private boolean enabled = true;
        private boolean adminOnly;
        private boolean quickLink;
        private int order;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public MenuItemType getType() {
            return type;
        }

        public void setType(MenuItemType type) {
            this.type = type;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public String getMenu() {
            return menu;
        }

        public void setMenu(String menu) {
            this.menu = menu;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAdminOnly() {
            return adminOnly;
        }

        public void setAdminOnly(boolean adminOnly) {
            this.adminOnly = adminOnly;
        }

        public boolean isQuickLink() {
            return quickLink;
        }

        public void setQuickLink(boolean quickLink) {
            this.quickLink = quickLink;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public boolean isGroup() {
            return type == MenuItemType.GROUP;
        }

        public boolean isLink() {
            return type == MenuItemType.LINK;
        }

        public boolean isPlaceholder() {
            return type == MenuItemType.PLACEHOLDER;
        }
    }

    public static class MenuNode {

        private final MenuItem item;
        private final List<MenuNode> children;

        public MenuNode(MenuItem item, List<MenuNode> children) {
            this.item = Objects.requireNonNull(item);
            this.children = children == null ? List.of() : List.copyOf(children);
        }

        public String getId() {
            return item.getId();
        }

        public String getLabel() {
            return item.getLabel();
        }

        public String getUrl() {
            return item.getUrl();
        }

        public String getIcon() {
            return item.getIcon();
        }

        public String getDescription() {
            return item.getDescription();
        }

        public List<MenuNode> getChildren() {
            return children;
        }

        public boolean isGroup() {
            return item.isGroup();
        }

        public boolean isLink() {
            return item.isLink();
        }

        public boolean isPlaceholder() {
            return item.isPlaceholder();
        }
    }
}
