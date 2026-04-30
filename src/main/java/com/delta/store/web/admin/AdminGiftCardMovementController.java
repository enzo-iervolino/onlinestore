 

 
package com.delta.store.web.admin;

import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.service.GiftCardAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/giftcard-movimenti")
public class AdminGiftCardMovementController {

    private final GiftCardAdminService adminService;

    public AdminGiftCardMovementController(GiftCardAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String code,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {

        List<GiftCardUsage> movements =
                adminService.searchMovements(code, from, to);

        model.addAttribute("movements", movements);
        model.addAttribute("code", code);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin/tables/giftcard-movimenti";
    }
}
