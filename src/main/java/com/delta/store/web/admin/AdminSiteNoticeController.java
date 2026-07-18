package com.delta.store.web.admin;

import com.delta.store.config.SiteNoticeProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/admin/site-notice")
public class AdminSiteNoticeController {

    private final SiteNoticeProperties siteNoticeProperties;

    public AdminSiteNoticeController(SiteNoticeProperties siteNoticeProperties) {
        this.siteNoticeProperties = siteNoticeProperties;
    }

    @GetMapping
    public String configuration(
            Authentication authentication,
            Model model
    ) {
        requireAdministrator(authentication);

        SiteNoticeProperties.SiteNoticeView siteNotice = siteNoticeProperties.getView();
        if (!model.containsAttribute("siteNoticeFormText")) {
            model.addAttribute("siteNoticeFormText", siteNotice.getText());
        }
        if (!model.containsAttribute("siteNoticeFormEnabled")) {
            model.addAttribute("siteNoticeFormEnabled", siteNotice.isEnabled());
        }

        model.addAttribute("siteNoticeConfigurationPath", siteNoticeProperties.getConfigurationDisplayPath());
        model.addAttribute("siteNoticeMaxLength", SiteNoticeProperties.MAX_TEXT_LENGTH);

        return "admin/site-notice-configuration";
    }

    @PostMapping
    public String update(
            @RequestParam(name = "text", required = false) String text,
            @RequestParam(name = "enabled", required = false) String enabledValue,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        requireAdministrator(authentication);

        boolean enabled = enabledValue != null;

        try {
            siteNoticeProperties.update(text, enabled);
            redirectAttributes.addFlashAttribute(
                    "siteNoticeSuccess",
                    enabled
                            ? "Avviso salvato e attivato correttamente."
                            : "Avviso salvato e disattivato correttamente."
            );
        } catch (IOException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "siteNoticeError",
                    "Non è stato possibile salvare l'avviso: " + ex.getMessage()
            );
            redirectAttributes.addFlashAttribute("siteNoticeFormText", text == null ? "" : text);
            redirectAttributes.addFlashAttribute("siteNoticeFormEnabled", enabled);
        }

        return "redirect:/admin/site-notice";
    }

    private void requireAdministrator(Authentication authentication) {
        boolean administrator = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!administrator) {
            throw new AccessDeniedException("Gestione avviso consentita solo agli amministratori");
        }
    }
}
