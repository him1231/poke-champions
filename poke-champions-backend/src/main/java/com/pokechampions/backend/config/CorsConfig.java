package com.pokechampions.backend.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> DEFAULT_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.web.app",
            "https://*.firebaseapp.com");

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> patterns = new ArrayList<>(DEFAULT_ORIGIN_PATTERNS);
        String extra = System.getenv("APP_CORS_EXTRA_ORIGINS");
        if (extra != null && !extra.isBlank()) {
            Arrays.stream(extra.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(patterns::add);
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
