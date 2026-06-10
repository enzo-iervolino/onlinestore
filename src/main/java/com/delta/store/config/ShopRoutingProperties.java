package com.delta.store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ShopRoutingProperties {

    private Map<String, String> pages = new HashMap<>();

    public ShopRoutingProperties(
            @Value("${shop.pages.shop:shop/shop}") String shopPage,
            @Value("${shop.pages.detail-shop:shop/shop-detail}") String detailShopPage) {
        this.pages.put("shop", normalizePage(shopPage, "shop/shop"));
        this.pages.put("detail-shop", normalizePage(detailShopPage, "shop/shop-detail"));
    }

    public Map<String, String> getPages() {
        return pages;
    }

    public void setPages(Map<String, String> pages) {
        this.pages = pages != null ? pages : new HashMap<>();
    }

    public String getPage(String key) {
        if (key == null || key.isBlank()) {
            return "error/404";
        }
        return pages.getOrDefault(key, "error/404");
    }

    private String normalizePage(String page, String defaultPage) {
        if (page == null || page.isBlank()) {
            return defaultPage;
        }
        return page.trim();
    }
}
