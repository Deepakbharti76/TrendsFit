package in.trendsfit.security;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Jwt;
import in.trendsfit.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads "Authorization: Bearer <jwt>" and, if valid, populates the SecurityContext. Invalid tokens are ignored (anonymous). */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final AppConfig cfg;

    public JwtAuthFilter(AppConfig cfg) { this.cfg = cfg; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ") && cfg.jwtSecret != null && cfg.jwtSecret.length() >= 32) {
            try {
                Jwt.Claims c = Jwt.parse(cfg.jwtSecret, h.substring(7).trim(), Instant.now());
                if (c != null) {
                    Role role = Role.valueOf(c.role());
                    List<GrantedAuthority> auth = new ArrayList<>();
                    auth.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                    for (Role.Permission p : role.permissions()) auth.add(new SimpleGrantedAuthority("PERM_" + p.name()));
                    AuthUser user = new AuthUser(c.userId(), c.subject(), role);
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, auth));
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
