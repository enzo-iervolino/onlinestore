package com.delta.store.web.admin;

import com.delta.store.shipping.model.ShippingCost;
import com.delta.store.shipping.service.ShippingService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/shipping-costs")
public class AdminShippingCostController {

    private static final String LIST_REDIRECT = "redirect:/admin/shipping-costs";
    private static final String LIST_TEMPLATE = "admin/shipping-costs/list";
    private static final String FORM_TEMPLATE = "admin/shipping-costs/form";

    private final ShippingService shippingService;

    public AdminShippingCostController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @GetMapping
    public String list(@RequestParam(name = "status", defaultValue = "all") String status,
                       Model model) {
        List<ShippingCost> allShippingCosts = shippingService.findAll();
        String normalizedStatus = normalizeStatus(status);

        List<ShippingCost> visibleShippingCosts = switch (normalizedStatus) {
            case "active" -> allShippingCosts.stream()
                    .filter(ShippingCost::isActive)
                    .toList();
            case "inactive" -> allShippingCosts.stream()
                    .filter(item -> !item.isActive())
                    .toList();
            default -> allShippingCosts;
        };

        long activeCount = allShippingCosts.stream()
                .filter(ShippingCost::isActive)
                .count();

        model.addAttribute("shippingCosts", visibleShippingCosts);
        model.addAttribute("selectedStatus", normalizedStatus);
        model.addAttribute("totalCount", allShippingCosts.size());
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("inactiveCount", allShippingCosts.size() - activeCount);
        return LIST_TEMPLATE;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        ShippingCost shippingCost = new ShippingCost();
        shippingCost.setActive(true);
        prepareForm(model, shippingCost, false, null);
        return FORM_TEMPLATE;
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("shippingCost") ShippingCost shippingCost,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        normalize(shippingCost);
        validateBusinessRules(shippingCost, null, bindingResult);

        if (bindingResult.hasErrors()) {
            prepareForm(model, shippingCost, false, null);
            return FORM_TEMPLATE;
        }

        ShippingCost saved = shippingService.save(shippingCost);
        redirectAttributes.addFlashAttribute(
                "success",
                "Fascia di spedizione creata correttamente (ID: " + saved.getId() + ")."
        );
        return LIST_REDIRECT;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        return shippingService.findById(id)
                .map(shippingCost -> {
                    prepareForm(model, shippingCost, true, id);
                    return FORM_TEMPLATE;
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Fascia di spedizione non trovata.");
                    return LIST_REDIRECT;
                });
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("shippingCost") ShippingCost formShippingCost,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        ShippingCost existing = shippingService.findById(id).orElse(null);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("error", "Fascia di spedizione non trovata.");
            return LIST_REDIRECT;
        }

        normalize(formShippingCost);
        validateBusinessRules(formShippingCost, id, bindingResult);

        if (bindingResult.hasErrors()) {
            prepareForm(model, formShippingCost, true, id);
            return FORM_TEMPLATE;
        }

        existing.setMinTotal(formShippingCost.getMinTotal());
        existing.setMaxTotal(formShippingCost.getMaxTotal());
        existing.setCost(formShippingCost.getCost());
        existing.setDescription(formShippingCost.getDescription());
        existing.setActive(formShippingCost.isActive());

        shippingService.save(existing);
        redirectAttributes.addFlashAttribute("success", "Fascia di spedizione aggiornata correttamente.");
        return LIST_REDIRECT;
    }

    @PostMapping("/{id}/toggle")
    public String toggleActive(@PathVariable("id") Long id,
                               RedirectAttributes redirectAttributes) {
        ShippingCost shippingCost = shippingService.findById(id).orElse(null);
        if (shippingCost == null) {
            redirectAttributes.addFlashAttribute("error", "Fascia di spedizione non trovata.");
            return LIST_REDIRECT;
        }

        boolean newActiveValue = !shippingCost.isActive();
        if (newActiveValue && shippingService.hasActiveOverlap(
                shippingCost.getId(),
                shippingCost.getMinTotal(),
                shippingCost.getMaxTotal())) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "La fascia non può essere attivata perché si sovrappone a un'altra fascia attiva."
            );
            return LIST_REDIRECT;
        }

        shippingCost.setActive(newActiveValue);
        shippingService.save(shippingCost);
        redirectAttributes.addFlashAttribute(
                "success",
                newActiveValue ? "Fascia di spedizione attivata." : "Fascia di spedizione disattivata."
        );
        return LIST_REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         RedirectAttributes redirectAttributes) {
        if (!shippingService.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Fascia di spedizione non trovata.");
            return LIST_REDIRECT;
        }

        try {
            shippingService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Fascia di spedizione eliminata.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Non è stato possibile eliminare la fascia di spedizione."
            );
        }

        return LIST_REDIRECT;
    }

    private void prepareForm(Model model,
                             ShippingCost shippingCost,
                             boolean edit,
                             Long editingId) {
        model.addAttribute("shippingCost", shippingCost);
        model.addAttribute("isEdit", edit);
        model.addAttribute("editingId", editingId);
        model.addAttribute("pageTitle", edit ? "Modifica costo di spedizione" : "Nuovo costo di spedizione");
        model.addAttribute(
                "formAction",
                edit ? "/admin/shipping-costs/" + editingId + "/edit" : "/admin/shipping-costs/new"
        );
    }

    private void validateBusinessRules(ShippingCost shippingCost,
                                       Long excludedId,
                                       BindingResult bindingResult) {
        BigDecimal minTotal = shippingCost.getMinTotal();
        BigDecimal maxTotal = shippingCost.getMaxTotal();

        if (minTotal != null && maxTotal != null && maxTotal.compareTo(minTotal) < 0) {
            bindingResult.rejectValue(
                    "maxTotal",
                    "shippingCost.maxTotal.beforeMin",
                    "Il totale massimo deve essere maggiore o uguale al totale minimo."
            );
        }

        if (!bindingResult.hasFieldErrors("minTotal")
                && !bindingResult.hasFieldErrors("maxTotal")
                && shippingCost.isActive()
                && shippingService.hasActiveOverlap(excludedId, minTotal, maxTotal)) {
            bindingResult.reject(
                    "shippingCost.overlap",
                    "La fascia si sovrappone a un'altra fascia attiva. Modifica i limiti o disattivala."
            );
        }
    }

    private void normalize(ShippingCost shippingCost) {
        String description = shippingCost.getDescription();
        if (description == null || description.trim().isEmpty()) {
            shippingCost.setDescription(null);
        } else {
            shippingCost.setDescription(description.trim());
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "all";
        }

        return switch (status.trim().toLowerCase()) {
            case "active" -> "active";
            case "inactive" -> "inactive";
            default -> "all";
        };
    }
}
