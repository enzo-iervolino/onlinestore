package com.delta.store.web.admin;

import com.delta.store.product.dto.LookupTableForm;
import com.delta.store.product.model.ImageLookupEntity;
import com.delta.store.product.model.ProductBrand;
import com.delta.store.product.model.ProductFamily;
import com.delta.store.product.model.ProductGroup;
import com.delta.store.product.model.ProductSubfamily;
import com.delta.store.product.model.ProductSubgroup;
import com.delta.store.product.repository.ImageLookupRepository;
import com.delta.store.product.repository.ProductBrandRepository;
import com.delta.store.product.repository.ProductFamilyRepository;
import com.delta.store.product.repository.ProductGroupRepository;
import com.delta.store.product.repository.ProductSubfamilyRepository;
import com.delta.store.product.repository.ProductSubgroupRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Controller
@RequestMapping("/admin")
public class AdminLookupTableController {

    private final Map<String, LookupTableConfig<? extends ImageLookupEntity>> configs;

    public AdminLookupTableController(ProductGroupRepository groupRepository,
                                      ProductSubgroupRepository subgroupRepository,
                                      ProductBrandRepository brandRepository,
                                      ProductFamilyRepository familyRepository,
                                      ProductSubfamilyRepository subfamilyRepository) {
        this.configs = new LinkedHashMap<>();
        this.configs.put("groups", new LookupTableConfig<>(
                "groups",
                "/admin/groups",
                "Gruppo",
                "Gruppi",
                "Gestione Gruppi",
                "Nuovo gruppo",
                "Modifica gruppo",
                "Dettaglio gruppo",
                "uploads/groups",
                "/uploads/groups/",
                groupRepository,
                ProductGroup::new
        ));
        this.configs.put("subgroups", new LookupTableConfig<>(
                "subgroups",
                "/admin/subgroups",
                "Sottogruppo",
                "Sottogruppi",
                "Gestione Sottogruppi",
                "Nuovo sottogruppo",
                "Modifica sottogruppo",
                "Dettaglio sottogruppo",
                "uploads/subgroups",
                "/uploads/subgroups/",
                subgroupRepository,
                ProductSubgroup::new
        ));
        this.configs.put("brands", new LookupTableConfig<>(
                "brands",
                "/admin/brands",
                "Brand",
                "Brand",
                "Gestione Brand",
                "Nuovo brand",
                "Modifica brand",
                "Dettaglio brand",
                "uploads/brands",
                "/uploads/brands/",
                brandRepository,
                ProductBrand::new
        ));
        this.configs.put("families", new LookupTableConfig<>(
                "families",
                "/admin/families",
                "Famiglia",
                "Famiglie",
                "Gestione Famiglie",
                "Nuova famiglia",
                "Modifica famiglia",
                "Dettaglio famiglia",
                "uploads/families",
                "/uploads/families/",
                familyRepository,
                ProductFamily::new
        ));
        this.configs.put("subfamilies", new LookupTableConfig<>(
                "subfamilies",
                "/admin/subfamilies",
                "Sottofamiglia",
                "Sottofamiglie",
                "Gestione Sottofamiglie",
                "Nuova sottofamiglia",
                "Modifica sottofamiglia",
                "Dettaglio sottofamiglia",
                "uploads/subfamilies",
                "/uploads/subfamilies/",
                subfamilyRepository,
                ProductSubfamily::new
        ));
    }

    @GetMapping({"/groups", "/subgroups", "/brands", "/families", "/subfamilies"})
    public String list(HttpServletRequest request,
                       Model model,
                       @RequestParam(value = "keyword", required = false) String keyword) {
        LookupTableConfig<? extends ImageLookupEntity> config = configForRequest(request);

        List<? extends ImageLookupEntity> items = config.repository().findAllByOrderByCodeAsc()
                .stream()
                .sorted(Comparator.comparing((ImageLookupEntity i) -> safe(i.getCode()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (StringUtils.hasText(keyword)) {
            String q = keyword.trim().toLowerCase();
            items = items.stream()
                    .filter(i -> contains(i.getCode(), q) || contains(i.getDescri(), q))
                    .toList();
        }

        addCommonModel(model, config);
        model.addAttribute("items", items);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", config.listTitle());

        return "admin/lookup/list";
    }

    @GetMapping({"/groups/new", "/subgroups/new", "/brands/new", "/families/new", "/subfamilies/new"})
    public String newForm(HttpServletRequest request, Model model) {
        LookupTableConfig<? extends ImageLookupEntity> config = configForRequest(request);

        addCommonModel(model, config);
        model.addAttribute("lookup", new LookupTableForm());
        model.addAttribute("pageTitle", config.newTitle());
        model.addAttribute("formAction", config.basePath() + "/new");
        model.addAttribute("isEdit", false);

        return "admin/lookup/form";
    }

    @PostMapping({"/groups/new", "/subgroups/new", "/brands/new", "/families/new", "/subfamilies/new"})
    public String create(HttpServletRequest request,
                         @ModelAttribute("lookup") LookupTableForm form,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return createInternal(configForRequest(request), form, imageFile, model, redirectAttributes);
    }

    @GetMapping({"/groups/{id}", "/subgroups/{id}", "/brands/{id}", "/families/{id}", "/subfamilies/{id}"})
    public String view(HttpServletRequest request,
                       @PathVariable Long id,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        LookupTableConfig<? extends ImageLookupEntity> config = configForRequest(request);
        Optional<? extends ImageLookupEntity> optionalEntity = config.repository().findById(id);

        if (optionalEntity.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", config.entitySingular() + " non trovato.");
            return "redirect:" + config.basePath();
        }

        addCommonModel(model, config);
        model.addAttribute("item", optionalEntity.get());
        model.addAttribute("pageTitle", config.viewTitle());

        return "admin/lookup/view";
    }

    @GetMapping({"/groups/{id}/edit", "/subgroups/{id}/edit", "/brands/{id}/edit", "/families/{id}/edit", "/subfamilies/{id}/edit"})
    public String editForm(HttpServletRequest request,
                           @PathVariable Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        LookupTableConfig<? extends ImageLookupEntity> config = configForRequest(request);
        Optional<? extends ImageLookupEntity> optionalEntity = config.repository().findById(id);

        if (optionalEntity.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", config.entitySingular() + " non trovato.");
            return "redirect:" + config.basePath();
        }

        addCommonModel(model, config);
        model.addAttribute("lookup", toForm(optionalEntity.get()));
        model.addAttribute("pageTitle", config.editTitle());
        model.addAttribute("formAction", config.basePath() + "/" + id + "/edit");
        model.addAttribute("isEdit", true);

        return "admin/lookup/form";
    }

    @PostMapping({"/groups/{id}/edit", "/subgroups/{id}/edit", "/brands/{id}/edit", "/families/{id}/edit", "/subfamilies/{id}/edit"})
    public String update(HttpServletRequest request,
                         @PathVariable Long id,
                         @ModelAttribute("lookup") LookupTableForm form,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return updateInternal(configForRequest(request), id, form, imageFile, model, redirectAttributes);
    }

    private <T extends ImageLookupEntity> String createInternal(LookupTableConfig<T> config,
                                                                LookupTableForm form,
                                                                MultipartFile imageFile,
                                                                Model model,
                                                                RedirectAttributes redirectAttributes) {
        normalize(form);

        String validationError = validateForm(form);
        if (validationError != null) {
            prepareFormAfterError(model, config, form, config.newTitle(), config.basePath() + "/new", false, validationError);
            return "admin/lookup/form";
        }

        Optional<T> existing = config.repository().findByCode(form.getCode());
        if (existing.isPresent()) {
            prepareFormAfterError(model, config, form, config.newTitle(), config.basePath() + "/new", false,
                    "Esiste già un record con questo codice.");
            return "admin/lookup/form";
        }

        T entity = config.supplier().get();
        applyForm(entity, form);

        try {
            String imageUrl = saveImageIfPresent(config, imageFile);
            if (StringUtils.hasText(imageUrl)) {
                entity.setImageUrl(imageUrl);
            }
        } catch (IOException e) {
            prepareFormAfterError(model, config, form, config.newTitle(), config.basePath() + "/new", false,
                    "Errore durante il caricamento dell'immagine.");
            return "admin/lookup/form";
        }

        config.repository().save(entity);
        redirectAttributes.addFlashAttribute("successMessage", config.entitySingular() + " creato correttamente.");
        return "redirect:" + config.basePath();
    }

    private <T extends ImageLookupEntity> String updateInternal(LookupTableConfig<T> config,
                                                                Long id,
                                                                LookupTableForm form,
                                                                MultipartFile imageFile,
                                                                Model model,
                                                                RedirectAttributes redirectAttributes) {
        Optional<T> optionalEntity = config.repository().findById(id);

        if (optionalEntity.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", config.entitySingular() + " non trovato.");
            return "redirect:" + config.basePath();
        }

        normalize(form);
        form.setId(id);

        String validationError = validateForm(form);
        if (validationError != null) {
            prepareFormAfterError(model, config, form, config.editTitle(), config.basePath() + "/" + id + "/edit", true, validationError);
            return "admin/lookup/form";
        }

        Optional<T> existingByCode = config.repository().findByCode(form.getCode());
        if (existingByCode.isPresent() && !existingByCode.get().getId().equals(id)) {
            prepareFormAfterError(model, config, form, config.editTitle(), config.basePath() + "/" + id + "/edit", true,
                    "Esiste già un altro record con questo codice.");
            return "admin/lookup/form";
        }

        T entity = optionalEntity.get();
        applyForm(entity, form);

        try {
            String imageUrl = saveImageIfPresent(config, imageFile);
            if (StringUtils.hasText(imageUrl)) {
                entity.setImageUrl(imageUrl);
            }
        } catch (IOException e) {
            prepareFormAfterError(model, config, form, config.editTitle(), config.basePath() + "/" + id + "/edit", true,
                    "Errore durante il caricamento dell'immagine.");
            return "admin/lookup/form";
        }

        config.repository().save(entity);
        redirectAttributes.addFlashAttribute("successMessage", config.entitySingular() + " aggiornato correttamente.");
        return "redirect:" + config.basePath();
    }

    private void addCommonModel(Model model, LookupTableConfig<? extends ImageLookupEntity> config) {
        model.addAttribute("basePath", config.basePath());
        model.addAttribute("dashboardPath", "/admin/dashboard");
        model.addAttribute("entitySingular", config.entitySingular());
        model.addAttribute("entityPlural", config.entityPlural());
        model.addAttribute("listTitle", config.listTitle());
    }

    private void prepareFormAfterError(Model model,
                                       LookupTableConfig<? extends ImageLookupEntity> config,
                                       LookupTableForm form,
                                       String pageTitle,
                                       String formAction,
                                       boolean isEdit,
                                       String errorMessage) {
        addCommonModel(model, config);
        model.addAttribute("lookup", form);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("formAction", formAction);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("errorMessage", errorMessage);
    }

    private LookupTableForm toForm(ImageLookupEntity entity) {
        LookupTableForm form = new LookupTableForm();
        form.setId(entity.getId());
        form.setCode(entity.getCode());
        form.setDescri(entity.getDescri());
        form.setImageUrl(entity.getImageUrl());
        return form;
    }

    private void applyForm(ImageLookupEntity entity, LookupTableForm form) {
        entity.setCode(form.getCode());
        entity.setDescri(form.getDescri());
        entity.setImageUrl(form.getImageUrl());
    }

    private String validateForm(LookupTableForm form) {
        if (!StringUtils.hasText(form.getCode())) {
            return "Il codice è obbligatorio.";
        }
        return null;
    }

    private void normalize(LookupTableForm form) {
        if (form.getCode() != null) {
            form.setCode(form.getCode().trim().toUpperCase());
        }
        if (form.getDescri() != null) {
            form.setDescri(form.getDescri().trim());
        }
        if (form.getImageUrl() != null) {
            form.setImageUrl(form.getImageUrl().trim());
        }
    }

    private String saveImageIfPresent(LookupTableConfig<? extends ImageLookupEntity> config,
                                      MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        String originalFilename = imageFile.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();

        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png") && !extension.equals("webp")) {
            throw new IOException("Formato file non supportato");
        }

        Path uploadDir = Paths.get(config.uploadDir());
        Files.createDirectories(uploadDir);

        String newFilename = UUID.randomUUID() + "." + extension;
        Path filePath = uploadDir.resolve(newFilename);

        Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return config.uploadUrlPrefix() + newFilename;
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private <T extends ImageLookupEntity> LookupTableConfig<T> configForRequest(HttpServletRequest request) {
        String key = extractTableKey(request);
        LookupTableConfig<? extends ImageLookupEntity> config = configs.get(key);
        if (config == null) {
            throw new IllegalArgumentException("Tabella non gestita: " + key);
        }
        return (LookupTableConfig<T>) config;
    }

    private String extractTableKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String path = uri;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if (configs.containsKey(part)) {
                return part;
            }
        }
        return "";
    }

    private record LookupTableConfig<T extends ImageLookupEntity>(
            String key,
            String basePath,
            String entitySingular,
            String entityPlural,
            String listTitle,
            String newTitle,
            String editTitle,
            String viewTitle,
            String uploadDir,
            String uploadUrlPrefix,
            ImageLookupRepository<T> repository,
            Supplier<T> supplier
    ) {
    }
}
