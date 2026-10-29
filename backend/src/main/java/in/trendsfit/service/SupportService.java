package in.trendsfit.service;

import in.trendsfit.domain.Hmac;
import in.trendsfit.domain.Validators;
import in.trendsfit.security.AuthUser;
import in.trendsfit.security.CurrentUser;
import in.trendsfit.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer support chat. Messages are stored in MySQL (support_conversations / support_messages), so nothing is lost on refresh.
 * A customer (guest or logged in) holds a per-conversation secret token; staff access is protected by the SUPPORT_CHAT permission.
 */
@Service
public class SupportService {
    static final int MAX_BODY = 2000;
    static final int MAX_MESSAGES = 500;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public SupportService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    // ------------------------------------------------------------------ customer side

    @Transactional
    public Map<String, Object> start(String name, String email, String orderNumber, String message) {
        String n = name == null ? "" : name.trim();
        String e = email == null ? "" : email.trim().toLowerCase();
        if (n.isEmpty() || n.length() > 80) throw ApiException.badRequest("BAD_NAME", "Please enter your name (up to 80 characters).");
        if (!Validators.email(e)) throw ApiException.badRequest("BAD_EMAIL", "Please enter a valid email address.");
        String body = cleanBody(message);
        String order = orderNumber == null ? "" : orderNumber.trim().toUpperCase();
        if (!order.isEmpty()) {
            Long c = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_number = ? AND LOWER(email) = ?", Long.class, order, e);
            if (c == null || c == 0) throw ApiException.badRequest("ORDER_NOT_FOUND", "We could not match that order number with this email address.");
        }
        Long recent = jdbc.queryForObject("SELECT COUNT(*) FROM support_conversations WHERE customer_email = ? AND created_at >= (NOW() - INTERVAL 1 DAY)", Long.class, e);
        if (recent != null && recent >= 5) throw ApiException.badRequest("TOO_MANY_CHATS", "You have started several chats today. Please continue in an existing chat.");
        AuthUser u = CurrentUser.get();
        String token = Tokens.random(24).substring(0, 32).replace('-', 'a').replace('_', 'b');
        jdbc.update("INSERT INTO support_conversations (access_token, customer_name, customer_email, user_id, order_number) VALUES (?,?,?,?,?)",
            token, n, e, u == null ? null : u.id(), order.isEmpty() ? null : order);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        insertMessage(id, "CUSTOMER", null, body);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("token", token);
        return m;
    }

    /** Returns the thread and marks staff messages as read by the customer (this is what "Seen" in the admin view means). */
    @Transactional
    public Map<String, Object> customerView(long id, String token) {
        Map<String, Object> c = requireCustomerAccess(id, token);
        jdbc.update("UPDATE support_messages SET read_at = NOW() WHERE conversation_id = ? AND sender = 'STAFF' AND read_at IS NULL", id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", c.get("status"));
        m.put("orderNumber", c.get("order_number"));
        m.put("messages", messages(id, false));
        return m;
    }

    @Transactional
    public Map<String, Object> customerSend(long id, String token, String body) {
        requireCustomerAccess(id, token);
        String b = cleanBody(body);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM support_messages WHERE conversation_id = ?", Long.class, id);
        if (n != null && n >= MAX_MESSAGES) throw ApiException.badRequest("CHAT_FULL", "This chat is full. Please start a new chat.");
        insertMessage(id, "CUSTOMER", null, b);
        jdbc.update("UPDATE support_conversations SET status = 'OPEN' WHERE id = ?", id);
        return customerView(id, token);
    }

    private Map<String, Object> requireCustomerAccess(long id, String token) {
        Map<String, Object> c;
        try {
            c = jdbc.queryForMap("SELECT access_token, status, order_number FROM support_conversations WHERE id = ?", id);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("Chat not found.");
        }
        if (token == null || token.isBlank() || !Hmac.safeEquals(token, String.valueOf(c.get("access_token"))))
            throw ApiException.notFound("Chat not found."); // same answer as a missing chat: do not reveal which ids exist
        return c;
    }

    // ------------------------------------------------------------------ staff side

    public Map<String, Object> list(String q, String status, boolean unreadOnly, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 50));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            if (!status.equals("OPEN") && !status.equals("CLOSED")) throw ApiException.badRequest("BAD_STATUS", "Status must be OPEN or CLOSED.");
            w.append(" AND c.status = ?"); args.add(status);
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (LOWER(c.customer_name) LIKE ? OR LOWER(c.customer_email) LIKE ? OR LOWER(c.order_number) LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        if (unreadOnly) w.append(" AND EXISTS (SELECT 1 FROM support_messages u WHERE u.conversation_id = c.id AND u.sender = 'CUSTOMER' AND u.read_at IS NULL)");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM support_conversations c" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.id, c.customer_name AS customerName, c.customer_email AS customerEmail, c.order_number AS orderNumber, c.status, c.last_message_at AS lastMessageAt, "
            + "(SELECT COUNT(*) FROM support_messages m WHERE m.conversation_id = c.id AND m.sender = 'CUSTOMER' AND m.read_at IS NULL) AS unread, "
            + "(SELECT m2.body FROM support_messages m2 WHERE m2.conversation_id = c.id ORDER BY m2.id DESC LIMIT 1) AS lastMessage "
            + "FROM support_conversations c" + w + " ORDER BY c.last_message_at DESC, c.id DESC LIMIT ? OFFSET ?", pa.toArray());
        for (Map<String, Object> r : rows) {
            Object lm = r.get("lastMessage");
            if (lm != null && String.valueOf(lm).length() > 90) r.put("lastMessage", String.valueOf(lm).substring(0, 90) + "…");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        m.put("totalPages", (int) Math.ceil((total == null ? 0 : total) / (double) size));
        m.put("unreadTotal", unreadTotal());
        return m;
    }

    public long unreadTotal() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM support_messages WHERE sender = 'CUSTOMER' AND read_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    /** Opening a conversation marks the customer's messages as read. */
    @Transactional
    public Map<String, Object> adminView(long id) {
        Map<String, Object> c = conversation(id);
        jdbc.update("UPDATE support_messages SET read_at = NOW() WHERE conversation_id = ? AND sender = 'CUSTOMER' AND read_at IS NULL", id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("customerName", c.get("customer_name"));
        m.put("customerEmail", c.get("customer_email"));
        m.put("status", c.get("status"));
        m.put("orderNumber", c.get("order_number"));
        Object on = c.get("order_number");
        if (on != null) {
            List<Map<String, Object>> o = jdbc.queryForList("SELECT status, total FROM orders WHERE order_number = ?", on);
            if (!o.isEmpty()) { m.put("orderStatus", o.get(0).get("status")); m.put("orderTotal", o.get(0).get("total")); }
        }
        m.put("messages", messages(id, true));
        return m;
    }

    @Transactional
    public Map<String, Object> reply(long id, String body, String staffEmail) {
        conversation(id);
        String b = cleanBody(body);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM support_messages WHERE conversation_id = ?", Long.class, id);
        if (n != null && n >= MAX_MESSAGES) throw ApiException.badRequest("CHAT_FULL", "This chat is full.");
        insertMessage(id, "STAFF", staffEmail, b);
        return adminView(id);
    }

    @Transactional
    public void setStatus(long id, String status) {
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) throw ApiException.badRequest("BAD_STATUS", "Status must be OPEN or CLOSED.");
        conversation(id);
        jdbc.update("UPDATE support_conversations SET status = ? WHERE id = ?", status, id);
        audit.log("SUPPORT_CHAT_" + status, "support_conversation", id, null);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> conversation(long id) {
        try {
            return jdbc.queryForMap("SELECT id, customer_name, customer_email, order_number, status FROM support_conversations WHERE id = ?", id);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("Conversation not found.");
        }
    }

    private List<Map<String, Object>> messages(long id, boolean forStaff) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, sender, staff_email, body, created_at, read_at FROM support_messages WHERE conversation_id = ? ORDER BY id", id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("sender", r.get("sender"));
            m.put("body", r.get("body"));
            m.put("createdAt", r.get("created_at"));
            m.put("read", r.get("read_at") != null);
            if (forStaff && r.get("staff_email") != null) m.put("staffEmail", r.get("staff_email")); // never shown to customers
            out.add(m);
        }
        return out;
    }

    private void insertMessage(long conversationId, String sender, String staffEmail, String body) {
        jdbc.update("INSERT INTO support_messages (conversation_id, sender, staff_email, body) VALUES (?,?,?,?)", conversationId, sender, staffEmail, body);
        jdbc.update("UPDATE support_conversations SET last_message_at = NOW() WHERE id = ?", conversationId);
    }

    static String cleanBody(String s) {
        String b = s == null ? "" : s.trim();
        if (b.isEmpty()) throw ApiException.badRequest("EMPTY_MESSAGE", "Please type a message.");
        if (b.length() > MAX_BODY) throw ApiException.badRequest("MESSAGE_TOO_LONG", "Messages can be up to " + MAX_BODY + " characters.");
        return b;
    }
}
