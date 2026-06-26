package com.delta.store.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        Path productsPath = Paths.get("uploads/products")
                .toAbsolutePath()
                .normalize();

        Path avatarsPath = Paths.get("uploads/avatars")
                .toAbsolutePath()
                .normalize();

        Path categoriesPath = Paths.get("uploads/categories")
                .toAbsolutePath()
                .normalize();

        Path variantsPath = Paths.get("uploads/variants")
                .toAbsolutePath()
                .normalize();

        Path groupsPath = Paths.get("uploads/groups")
                .toAbsolutePath()
                .normalize();

        Path subgroupsPath = Paths.get("uploads/subgroups")
                .toAbsolutePath()
                .normalize();

        Path brandsPath = Paths.get("uploads/brands")
                .toAbsolutePath()
                .normalize();

        Path familiesPath = Paths.get("uploads/families")
                .toAbsolutePath()
                .normalize();

        Path subfamiliesPath = Paths.get("uploads/subfamilies")
                .toAbsolutePath()
                .normalize();

        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations("file:" + productsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:" + avatarsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/categories/**")
                .addResourceLocations("file:" + categoriesPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/variants/**")
                .addResourceLocations("file:" + variantsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/groups/**")
                .addResourceLocations("file:" + groupsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/subgroups/**")
                .addResourceLocations("file:" + subgroupsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/brands/**")
                .addResourceLocations("file:" + brandsPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/families/**")
                .addResourceLocations("file:" + familiesPath + "/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/subfamilies/**")
                .addResourceLocations("file:" + subfamiliesPath + "/")
                .setCachePeriod(3600);
    }
}
