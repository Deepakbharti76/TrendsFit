package in.trendsfit.service;

import in.trendsfit.security.AuthUser;
import in.trendsfit.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(String action, String entity, Object entityId, String detail) {
        AuthUser u = CurrentUser.get();
        logAs(u == null ? "system" : u.email(), action, entity, entityId, detail);
    }

    public void logAs(String actor, String action, String entity, Object entityId, String detail) {
        String d = detail == null ? null : (detail.length() > 480 ? detail.substring(0, 480) : detail);
        jdbc.update("INSERT INTO audit_logs (actor_email, action, entity, entity_id, detail) VALUES (?,?,?,?,?)",
                actor, action, entity, entityId == null ? null : String.valueOf(entityId), d);
    }

    public List<Map<String, Object>> recent(int limit) {
        int l = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("SELECT id, actor_email, action, entity, entity_id, detail, created_at FROM audit_logs ORDER BY id DESC LIMIT " + l);
    }
}
