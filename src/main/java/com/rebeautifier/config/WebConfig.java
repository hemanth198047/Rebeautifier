package com.rebeautifier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${rebeautifier.cover-storage-path}")
    private String coverStoragePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(coverStoragePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/api/v1/covers/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}