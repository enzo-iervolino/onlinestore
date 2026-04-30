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
    
    
    
    
    }
}
