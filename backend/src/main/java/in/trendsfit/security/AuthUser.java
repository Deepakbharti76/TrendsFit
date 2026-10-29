package in.trendsfit.security;

import in.trendsfit.domain.Role;

/** Authenticated principal stored in the SecurityContext. */
public record AuthUser(long id, String email, Role role) {}
