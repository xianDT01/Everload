package com.EverLoad.everload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:4200", "http://localhost:*", "http://127.0.0.1:*",
                        "https://localhost", "capacitor://localhost")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                .exposedHeaders("Content-Disposition", "Accept-Ranges", "Content-Range", "Content-Length")
                .allowCredentials(false);
    }
}
