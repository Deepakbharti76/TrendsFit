package in.trendsfit.security;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import in.trendsfit.config.AppConfig;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwt, RateLimitFilter rate,
                                          SecurityHeadersFilter headers,
@Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)           // stateless Bearer-token API, no cookies
            .cors(c -> c.configurationSource(cors))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Please log in to continue.\"}}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"You do not have permission to do this.\"}}");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/**", "/api/payments/webhook", "/api/shipping/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/products/**", "/api/home", "/api/delivery/**",
                        "/api/config", "/sitemap.xml", "/robots.txt", "/seo/**", "/uploads/**", "/actuator/health", "/api/orders/*",
                        "/api/orders/*/invoice", "/api/orders/*/exchange-eligibility", "/api/orders/*/tracking", "/api/support/conversations/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/cart/quote", "/api/orders", "/api/orders/*/payment/verify",
                        "/api/orders/*/payment/failed", "/api/orders/*/payment", "/api/orders/*/exchange", "/api/newsletter", "/api/size-recommendation", "/api/uploads/exchange",
                        "/api/support/conversations", "/api/support/conversations/*/messages").permitAll()
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/me/**").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .addFilterBefore(rate, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(headers, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppConfig cfg) {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = cfg.corsOriginList();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Order-Token", "X-Support-Token"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
