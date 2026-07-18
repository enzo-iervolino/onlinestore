package com.delta.store.web.admin;

import com.delta.store.config.AdminMenuProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/menu")
public class AdminMenuController {

    private final AdminMenuProperties adminMenuProperties;

    public AdminMenuController(AdminMenuProperties adminMenuProperties) {
        this.adminMenuProperties = adminMenuProperties;
    }

    @GetMapping("/configuration")
    public String configuration(Authentication authentication) {
        requireAdministrator(authentication);
        return "admin/menu-configuration";
    }

    @PostMapping
    public String updateMenuFlags(
            @RequestParam(name = "enabled", required = false) List<String> enabledItemIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        requireAdministrator(authentication);

        try {
            adminMenuProperties.updateEnabled(
                    enabledItemIds == null ? List.of() : enabledItemIds
            );
            redirectAttributes.addFlashAttribute(
                    "menuConfigSuccess",
                    "Configurazione menu salvata correttamente. Le modifiche sono già attive."
            );
        } catch (IOException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "menuConfigError",
                    "Non è stato possibile salvare la configurazione del menu: " + ex.getMessage()
            );
        }

        return "redirect:/admin/menu/configuration";
    }

    private void requireAdministrator(Authentication authentication) {
        boolean administrator = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!administrator) {
            throw new AccessDeniedException("Configurazione menu consentita solo agli amministratori");
        }
    }
}
