package com.delta.store.web.admin;

import com.delta.store.product.model.SizeOption;
import com.delta.store.product.service.SizeOptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/sizes")
public class AdminSizeController {

    private final SizeOptionService sizeService;

    public AdminSizeController(SizeOptionService sizeService) {
        this.sizeService = sizeService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       Model model) {
        model.addAttribute("sizes", sizeService.search(q));
        model.addAttribute("searchText", q);
        return "admin/tables/sizelist";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("size", new SizeOption());
        model.addAttribute("pageTitle", "Nuova taglia");
        return "admin/tables/sizeform";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id,
                           Model model,
                           RedirectAttributes ra) {
        return sizeService.findById(id)
                .map(s -> {
                    model.addAttribute("size", s);
                    model.addAttribute("pageTitle", "Modifica taglia (ID: " + id + ")");
                    return "admin/tables/sizeform";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("error", "Taglia non trovata.");
                    return "redirect:/admin/sizes";
                });
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("size") SizeOption size,
                       RedirectAttributes ra,
                       Model model) {

        String code = size.getCode() != null ? size.getCode().trim().toUpperCase() : "";
        String nameIt = size.getNameIt() != null ? size.getNameIt().trim().toUpperCase() : "";

        if (code.isBlank() || nameIt.isBlank()) {
            model.addAttribute("pageTitle", (size.getId() == null) ? "Nuova taglia" : "Modifica taglia (ID: " + size.getId() + ")");
            model.addAttribute("error", "Codice e Nome (IT) sono obbligatori.");
            return "admin/tables/sizeform";
        }

        size.setCode(code);
        size.setNameIt(nameIt);

        Optional<SizeOption> existing = sizeService.findByCodeIgnoreCase(code);
        if (existing.isPresent() && (size.getId() == null || !existing.get().getId().equals(size.getId()))) {
            model.addAttribute("pageTitle", (size.getId() == null) ? "Nuova taglia" : "Modifica taglia (ID: " + size.getId() + ")");
            model.addAttribute("error", "Esiste già una taglia con codice: " + code);
            return "admin/tables/sizeform";
        }

        SizeOption saved = sizeService.save(size);
        ra.addFlashAttribute("success", "Taglia salvata correttamente (ID: " + saved.getId() + ").");
        return "redirect:/admin/sizes";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         RedirectAttributes ra) {
        sizeService.deleteById(id);
        ra.addFlashAttribute("success", "Taglia eliminata.");
        return "redirect:/admin/sizes";
    }
}