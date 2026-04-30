package com.delta.store.web.admin;

import com.delta.store.discount.model.DiscountCode;
import com.delta.store.discount.service.DiscountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/discount-codes")
public class AdminDiscountCodeController {

    private final DiscountService discountService;

    public AdminDiscountCodeController(DiscountService discountService) {
        this.discountService = discountService;
        
    }

    @GetMapping
    public String list(
            @RequestParam(value = "editId", required = false) Long editId,
            @RequestParam(value = "success", required = false) String success,
            Model model
    ) {
        model.addAttribute("discountCodes", discountService.findAll());
        model.addAttribute("statuses", DiscountCode.Status.values());

        if (editId != null) {
            DiscountCode discountCode = discountService.findById(editId)
                    .orElse(new DiscountCode());
            model.addAttribute("discountCodeForm", discountCode);
            model.addAttribute("editMode", true);
        } else {
            model.addAttribute("discountCodeForm", new DiscountCode());
            model.addAttribute("editMode", false);
        }

        model.addAttribute("success", success);

        return "admin/discount/lista-buoni-sconto";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("discountCodeForm") DiscountCode discountCode) {
        discountService.create(discountCode);
        return "redirect:/admin/discount-codes?success=created";
    }

    @PostMapping("/update/{id}")
    public String update(
            @PathVariable Long id,
            @ModelAttribute("discountCodeForm") DiscountCode discountCode
    ) {
        discountService.update(id, discountCode);
        return "redirect:/admin/discount-codes?success=updated";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        discountService.deleteById(id);
        return "redirect:/admin/discount-codes?success=deleted";
    }
}