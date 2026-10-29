package in.trendsfit.web;

import in.trendsfit.config.AppConfig;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded images from the upload directory under /uploads/**. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final AppConfig cfg;

    public WebMvcConfig(AppConfig cfg) { this.cfg = cfg; }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String loc = Path.of(cfg.uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(loc.endsWith("/") ? loc : loc + "/")
            .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic());
    }
}
