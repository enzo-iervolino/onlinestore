package com.delta.store.web.admin;

import com.delta.store.cart.model.Cart;
import com.delta.store.cart.model.CartItem;
import com.delta.store.order.model.CartItemDTO;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.prove.RigaAdmin;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/admin/prove")
public class AdminProveController {

    private static final String CART_SESSION_KEY = "CART";
    private static final String ORDER_TEMP_DATA_SESSION_KEY = "ORDER_TEMP_DATA";

   
    @GetMapping
    public String cartItemTable(HttpSession session, Model model) {
        List<CartItemDTO> items = new ArrayList<>();

        Object raw = session.getAttribute(CART_SESSION_KEY);

        if (raw instanceof Cart cart) {
            Collection<CartItem> cartItems = cart.getItems();

            for (CartItem ci : cartItems) {
                CartItemDTO dto = new CartItemDTO();
                dto.setProductId(ci.getProductId());
                dto.setVariantId(ci.getVariantId());
                dto.setProductName(ci.getName());
                dto.setSku(ci.getVariantCode() != null ? ci.getVariantCode() : ci.getProductCode());
                dto.setProductCode(ci.getProductCode());
                dto.setVariantCode(ci.getVariantCode());
                dto.setColor(ci.getColor());
                dto.setSize(ci.getSize());
                dto.setQuantity(ci.getQuantity());
                dto.setUnitPrice(ci.getPrice());
                items.add(dto);
            }
        }

        model.addAttribute("items", items);
        return "admin/prove/cartitem";
    }



    @GetMapping("/ordertempdata-table")
    public String orderTempDataTable(HttpSession session, Model model) {
        Object raw = session.getAttribute(ORDER_TEMP_DATA_SESSION_KEY);

        OrderTempData data = null;
        if (raw instanceof OrderTempData otd) {
            data = otd;
        }

        model.addAttribute("orderTempData", data);
        return "admin/prove/ordertempdata";
    }



    @GetMapping("/lista")
    public String lista(Model model) {

        List<RigaAdmin> items = List.of(
                new RigaAdmin(1L, "Mario Rossi", "mario@email.it", true, "3331111111", "Napoli", "19/04/2026"),
                new RigaAdmin(2L, "Anna Bianchi", "anna@email.it", false, "3332222222", "Roma", "18/04/2026"),
                new RigaAdmin(3L, "Luigi Verdi", "luigi@email.it", true, "3333333333", "Milano", "17/04/2026")
        );

        model.addAttribute("items", items);
        return "admin/prove/lista";
    }






}









 