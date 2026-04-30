package com.delta.store.config;



import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "shop")
public class ShopRoutingProperties {

    private Map<String, String> pages = new HashMap<>();

    public Map<String, String> getPages() {
        return pages;
    }

    public void setPages(Map<String, String> pages) {
        this.pages = pages;
    }

    public String getPage(String key) {
        return pages.getOrDefault(key, "error/404");
    }
}
