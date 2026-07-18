package com.delta.store.config;

import com.delta.store.cart.model.Cart;
import com.delta.store.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final UserRepository userRepository;
    private final AdminMenuProperties adminMenuProperties;
    private final SiteNoticeProperties siteNoticeProperties;

    public GlobalModelAttributes(UserRepository userRepository,
                                 AdminMenuProperties adminMenuProperties,
                                 SiteNoticeProperties siteNoticeProperties) {
        this.userRepository = userRepository;
        this.adminMenuProperties = adminMenuProperties;
        this.siteNoticeProperties = siteNoticeProperties;
    }

    // CART SEMPRE DALLO STESSO POSTO
    @ModelAttribute("cart")
    public Cart cart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute("CART");
        if (cart == null) {
            cart = new Cart();
            session.setAttribute("CART", cart);
        }
        return cart;
    }

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("adminMenu")
    public AdminMenuProperties adminMenu() {
        return adminMenuProperties;
    }

    @ModelAttribute("adminMenuItems")
    public List<AdminMenuProperties.MenuNode> adminMenuItems() {
        return adminMenuProperties.visibleMenu(isAdmin());
    }

    @ModelAttribute("adminMenuQuickLinks")
    public List<AdminMenuProperties.MenuItem> adminMenuQuickLinks() {
        return adminMenuProperties.visibleQuickLinks(isAdmin());
    }

    @ModelAttribute("adminMenuConfigurationItems")
    public List<AdminMenuProperties.MenuItem> adminMenuConfigurationItems() {
        return isAdmin() ? adminMenuProperties.getConfigurationItems() : List.of();
    }

    @ModelAttribute("adminMenuConfigurationPath")
    public String adminMenuConfigurationPath() {
        return adminMenuProperties.getConfigurationDisplayPath();
    }

    @ModelAttribute("siteNotice")
    public SiteNoticeProperties.SiteNoticeView siteNotice() {
        return siteNoticeProperties.getView();
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        Authentication auth = currentAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        Authentication auth = currentAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail() {
        Authentication auth = currentAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "";
        }
        return auth.getName();
    }

    @ModelAttribute("currentAvatar")
    public String currentAvatar() {
        Authentication auth = currentAuthentication();

        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "/images/avatar-default.png";
        }

        return userRepository.findByEmail(auth.getName())
                .map(user -> {
                    String avatar = user.getAvatar();

                    // L'avatar predefinito non deve mai passare dalla cartella uploads.
                    if (avatar == null || avatar.isBlank() || "avatar-default.png".equals(avatar)) {
                        return "/images/avatar-default.png";
                    }

                    // I provider OAuth possono restituire una URL completa.
                    if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
                        return avatar;
                    }

                    return "/uploads/avatars/" + avatar;
                })
                .orElse("/images/avatar-default.png");
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
