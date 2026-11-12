package com.delta.store.cart.controller;

import com.delta.store.cart.model.Cart;
import com.delta.store.product.dto.ProductCodeLookupDTO;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.repository.ProductRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import com.delta.store.product.service.ProductCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    private static final String CART_SESSION_KEY = "CART";

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductCodeService productCodeService;

    public CartController(ProductRepository productRepository,
                          ProductVariantRepository productVariantRepository,
                          ProductCodeService productCodeService) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productCodeService = productCodeService;
    }

    @GetMapping("/view")
    public Map<String, Object> view(HttpSession session) {
        synchronized (session) {
        Cart cart = getCart(session);
        return response(cart);
            }
    }

    @PostMapping("/add/{id}")
    public Map<String, Object> add(
            @PathVariable Long id,
            @RequestParam(name = "qty", defaultValue = "1") int qty,
            HttpSession session
    ) {
        synchronized (session) {
        Cart cart = getCart(session);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Prodotto non trovato: " + id));

        requireBaseProduct(product);
        cart.add(product, resolveQty(qty, null));
        return response(cart);
            }
    }

    @PostMapping("/add")
    public Map<String, Object> addToCart(
            @RequestParam(name = "variantId", required = false) Long variantId,
            @RequestParam(name = "productId", required = false) Long productId,
            @RequestParam(name = "qty", required = false) Integer qty,
            @RequestParam(name = "quantity", required = false) Integer quantity,
            HttpSession session
    ) {
        synchronized (session) {
        int finalQty = resolveQty(qty, quantity);
        Cart cart = getCart(session);

        if (variantId != null) {
            ProductVariant variant = productVariantRepository.findForCartById(variantId)
                    .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

            if (!variant.isEnabled()) throw new IllegalArgumentException("Variante non disponibile");
            cart.add(variant, finalQty);
            return response(cart);
        }

        if (productId != null) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Prodotto non trovato: " + productId));

            requireBaseProduct(product);
        cart.add(product, finalQty);
            return response(cart);
        }

        throw new IllegalArgumentException("Parametro mancante: variantId o productId");
            }
    }

    @PostMapping("/add-by-code")
    public Map<String, Object> addByCode(
            @RequestParam String code,
            @RequestParam(name = "qty", required = false) Integer qty,
            @RequestParam(name = "quantity", required = false) Integer quantity,
            HttpSession session
    ) {
        synchronized (session) {
        ProductCodeLookupDTO lookup = productCodeService.lookup(code)
                .orElseThrow(() -> new IllegalArgumentException("Nessun articolo trovato per il codice: " + code));

        int finalQty = resolveQty(qty, quantity);
        Cart cart = getCart(session);

        if (lookup.variantId() != null) {
            ProductVariant variant = productVariantRepository.findForCartById(lookup.variantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + lookup.variantId()));

            if (!variant.isEnabled()) {
                throw new IllegalArgumentException("La variante letta non è attiva: " + lookup.variantCode());
            }

            if (!variant.isEnabled()) throw new IllegalArgumentException("Variante non disponibile");
            cart.add(variant, finalQty);
            return response(cart);
        }

        Product product = productRepository.findById(lookup.productId())
                .orElseThrow(() -> new IllegalArgumentException("Prodotto non trovato: " + lookup.productId()));

        requireBaseProduct(product);
        cart.add(product, finalQty);
        return response(cart);
            }
    }

    @PostMapping("/increase/{id}")
    public Map<String, Object> increase(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
        Cart cart = getCart(session);
        cart.increase(id);
        return response(cart);
            }
    }

    @PostMapping("/decrease/{id}")
    public Map<String, Object> decrease(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
        Cart cart = getCart(session);
        cart.decrease(id);
        return response(cart);
            }
    }

    @PostMapping("/remove/{id}")
    public Map<String, Object> remove(@PathVariable Long id, HttpSession session) {
        synchronized (session) {
        Cart cart = getCart(session);
        cart.remove(id);
        return response(cart);
            }
    }

    @PostMapping("/clear")
    public Map<String, Object> clear(HttpSession session) {
        synchronized (session) {
        Cart cart = getCart(session);
        cart.clear();
        return response(cart);
            }
    }

    private void requireBaseProduct(Product product) {
        if (product.getPrice()==null || product.getPrice().signum()<0) throw new IllegalArgumentException("Prezzo non disponibile");
        if (!productVariantRepository.findByProductIdOrderByVariantCodeAsc(product.getId()).isEmpty()) throw new IllegalArgumentException("Scegli una variante del prodotto");
    }
    private Cart getCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    private int resolveQty(Integer qty, Integer quantity) {
        int value = qty != null ? qty : (quantity != null ? quantity : 1);
        if (value < 1 || value > 999) throw new IllegalArgumentException("Quantità non valida (1–999)");
        return value;
    }

    private Map<String, Object> response(Cart cart) {
        return Map.of(
                "count", cart.getCount(),
                "total", cart.getTotal(),
                "items", cart.getItems()
        );
    }
}