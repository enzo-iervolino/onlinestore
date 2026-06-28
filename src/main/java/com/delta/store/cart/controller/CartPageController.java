package com.delta.store.cart.controller;

import com.delta.store.cart.model.Cart;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cart")
public class CartPageController {

    private static final String CART_SESSION_KEY = "CART";

    @GetMapping
    public String cartPage(HttpSession session, Model model) {
        Cart cart = getOrCreateCart(session);
        model.addAttribute("cart", cart);
        return "carrello";
    }

    @PostMapping("/page/increase/{id}")
    public String increaseFromPage(@PathVariable Long id, HttpSession session) {
        Cart cart = getOrCreateCart(session);
        cart.increase(id);
        return "redirect:/cart";
    }

    @PostMapping("/page/decrease/{id}")
    public String decreaseFromPage(@PathVariable Long id, HttpSession session) {
        Cart cart = getOrCreateCart(session);
        cart.decrease(id);
        return "redirect:/cart";
    }

    @PostMapping("/page/remove/{id}")
    public String removeFromPage(@PathVariable Long id, HttpSession session) {
        Cart cart = getOrCreateCart(session);
        cart.remove(id);
        return "redirect:/cart";
    }

    @PostMapping("/page/clear")
    public String clearFromPage(HttpSession session) {
        Cart cart = getOrCreateCart(session);
        cart.clear();
        return "redirect:/cart";
    }

    private Cart getOrCreateCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }
}