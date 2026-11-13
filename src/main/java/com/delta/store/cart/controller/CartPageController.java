package com.delta.store.cart.controller;

import com.delta.store.cart.model.Cart;
import com.delta.store.web.payment.CheckoutSession;
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
        synchronized (session) {
            Cart cart = getOrCreateCart(session);
            model.addAttribute("cart", cart);
            return "carrello";
        }
    }

    @PostMapping("/page/increase/{id}")
    public String increaseFromPage(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
            Cart cart = getOrCreateCart(session);
            cart.increase(id);
            return "redirect:/cart";
        }
    }

    @PostMapping("/page/decrease/{id}")
    public String decreaseFromPage(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
            Cart cart = getOrCreateCart(session);
            cart.decrease(id);
            CheckoutSession.clearIfCartEmpty(session);
            return "redirect:/cart";
        }
    }

    @PostMapping("/page/remove/{id}")
    public String removeFromPage(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
            Cart cart = getOrCreateCart(session);
            cart.remove(id);
            CheckoutSession.clearIfCartEmpty(session);
            return "redirect:/cart";
        }
    }

    @PostMapping("/page/clear")
    public String clearFromPage(HttpSession session) {
        synchronized (session) {
            Cart cart = getOrCreateCart(session);
            cart.clear();
            CheckoutSession.clearIfCartEmpty(session);
            return "redirect:/cart";
        }
    }

    private Cart getOrCreateCart(HttpSession session) {
        CheckoutSession.clearIfCartEmpty(session);
        Cart cart = (Cart) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }
}
