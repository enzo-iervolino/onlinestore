package com.delta.store.web.admin;

import com.delta.store.order.model.OrderType;
import com.delta.store.order.service.OrderTypeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/order-types")
public class AdminOrderTypeController {

    private final OrderTypeService orderTypeService;

    public AdminOrderTypeController(OrderTypeService orderTypeService) {
        this.orderTypeService = orderTypeService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       Model model) {
        model.addAttribute("orderTypes", orderTypeService.search(q));
        model.addAttribute("searchText", q);
        return "admin/tables/order-type-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("orderType", new OrderType());
        model.addAttribute("pageTitle", "Nuovo tipo ordine");
        return "admin/tables/order-type-form";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id,
                           Model model,
                           RedirectAttributes ra) {
        return orderTypeService.findById(id)
                .map(item -> {
                    model.addAttribute("orderType", item);
                    model.addAttribute("pageTitle", "Modifica tipo ordine (ID: " + id + ")");
                    return "admin/tables/order-type-form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("error", "Tipo ordine non trovato.");
                    return "redirect:/admin/order-types";
                });
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("orderType") OrderType orderType,
                       RedirectAttributes ra,
                       Model model) {

        String code = orderType.getCode() != null ? orderType.getCode().trim().toUpperCase() : "";
        String descri = orderType.getDescri() != null ? orderType.getDescri().trim() : "";

        if (code.isBlank() || descri.isBlank()) {
            model.addAttribute("pageTitle",
                    (orderType.getId() == null) ? "Nuovo tipo ordine" : "Modifica tipo ordine (ID: " + orderType.getId() + ")");
            model.addAttribute("error", "Codice e descrizione sono obbligatori.");
            return "admin/tables/order-type-form";
        }

        orderType.setCode(code);
        orderType.setDescri(descri);

        Optional<OrderType> existing = orderTypeService.findByCodeIgnoreCase(code);
        if (existing.isPresent() && (orderType.getId() == null || !existing.get().getId().equals(orderType.getId()))) {
            model.addAttribute("pageTitle",
                    (orderType.getId() == null) ? "Nuovo tipo ordine" : "Modifica tipo ordine (ID: " + orderType.getId() + ")");
            model.addAttribute("error", "Esiste già un tipo ordine con codice: " + code);
            return "admin/tables/order-type-form";
        }

        OrderType saved = orderTypeService.save(orderType);
        ra.addFlashAttribute("success", "Tipo ordine salvato correttamente (ID: " + saved.getId() + ").");
        return "redirect:/admin/order-types";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         RedirectAttributes ra) {
        orderTypeService.deleteById(id);
        ra.addFlashAttribute("success", "Tipo ordine eliminato.");
        return "redirect:/admin/order-types";
    }
}






