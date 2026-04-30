package com.delta.store.web.admin;

import com.delta.store.product.model.ColorOption;
import com.delta.store.product.service.ColorOptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/colors")
public class AdminColorController {

    private final ColorOptionService colorService;

    public AdminColorController(ColorOptionService colorService) {
        this.colorService = colorService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       Model model) {
        model.addAttribute("colors", colorService.search(q));
        model.addAttribute("searchText", q);
        return "admin/tables/colorlist";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("color", new ColorOption());
        model.addAttribute("pageTitle", "Nuovo colore");
        return "admin/tables/color-form";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id,
                           Model model,
                           RedirectAttributes ra) {
        return colorService.findById(id)
                .map(c -> {
                    model.addAttribute("color", c);
                    model.addAttribute("pageTitle", "Modifica colore (ID: " + id + ")");
                    return "admin/tables/color-form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("error", "Colore non trovato.");
                    return "redirect:/admin/colors";
                });
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("color") ColorOption color,
                       RedirectAttributes ra,
                       Model model) {

        String code = color.getCode() != null ? color.getCode().trim().toUpperCase() : "";
        String nameIt = color.getNameIt() != null ? color.getNameIt().trim() : "";

        if (code.isBlank() || nameIt.isBlank()) {
            model.addAttribute("pageTitle", (color.getId() == null) ? "Nuovo colore" : "Modifica colore (ID: " + color.getId() + ")");
            model.addAttribute("error", "Codice e Nome (IT) sono obbligatori.");
            return "admin/tables/color-form";
        }

        color.setCode(code);
        color.setNameIt(nameIt);

        if (color.getHex() != null) {
            String hex = color.getHex().trim();
            if (hex.isBlank()) {
                color.setHex(null);
            } else {
                if (!hex.startsWith("#")) {
                    hex = "#" + hex;
                }
                color.setHex(hex.toUpperCase());
            }
        }

        if (color.getRgb() != null) {
            String rgb = color.getRgb().trim();
            color.setRgb(rgb.isBlank() ? null : rgb);
        }

        Optional<ColorOption> existing = colorService.findByCodeIgnoreCase(code);
        if (existing.isPresent() && (color.getId() == null || !existing.get().getId().equals(color.getId()))) {
            model.addAttribute("pageTitle", (color.getId() == null) ? "Nuovo colore" : "Modifica colore (ID: " + color.getId() + ")");
            model.addAttribute("error", "Esiste già un colore con codice: " + code);
            return "admin/tables/color-form";
        }

        ColorOption saved = colorService.save(color);
        ra.addFlashAttribute("success", "Colore salvato correttamente (ID: " + saved.getId() + ").");
        return "redirect:/admin/colors";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         RedirectAttributes ra) {
        try {
            colorService.deleteById(id);
            ra.addFlashAttribute("success", "Colore eliminato.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "Impossibile eliminare il colore perché è associato a una o più varianti prodotto.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Errore durante l'eliminazione del colore.");
        }

        return "redirect:/admin/colors";
    }
}