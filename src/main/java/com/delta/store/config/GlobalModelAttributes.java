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

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final UserRepository userRepository;

    public GlobalModelAttributes(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "";
        }
        return auth.getName();
    }

    @ModelAttribute("currentAvatar")
    public String currentAvatar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "/images/avatar-default.png";
        }

        return userRepository.findByEmail(auth.getName())
                .map(u -> {
                    String avatar = u.getAvatar();

                    // avatar di default NON deve mai passare da uploads
                    if (avatar == null || avatar.isBlank() || avatar.equals("avatar-default.png")) {
                        return "/images/avatar-default.png";
                    }

                    // Google restituisce una URL completa per la foto profilo
                    if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
                        return avatar;
                    }

                    return "/uploads/avatars/" + avatar;
                })
                .orElse("/images/avatar-default.png");
    }

}
