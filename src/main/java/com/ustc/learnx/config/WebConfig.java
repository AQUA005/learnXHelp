package com.ustc.learnx.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * How the built frontend is cached.
 *
 * <p>Vite fingerprints asset filenames, so those can be held indefinitely. The
 * shell names the current files, so it must never be cached or a browser would
 * keep asking for assets that no longer exist.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Duration ASSET_CACHE = Duration.ofDays(365);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(ASSET_CACHE).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}
