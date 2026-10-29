package in.trendsfit.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    /** @return the logged-in user or null for anonymous requests. */
    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) return u;
        return null;
    }

    public static AuthUser require() {
        AuthUser u = get();
        if (u == null) throw in.trendsfit.web.ApiException.unauthorized("Please log in to continue.");
        return u;
    }
}
