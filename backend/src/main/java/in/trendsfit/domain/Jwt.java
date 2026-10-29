package in.trendsfit.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal HS256 JWT (header.payload.signature). Claims are flat strings/longs, which is all TrendsFit needs.
 * The signature is checked with a constant-time compare and "alg" is pinned, so "alg: none" tokens are rejected.
 */
public final class Jwt {
    public record Claims(String subject, String role, long userId, long expiresAtEpoch) {}

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private Jwt() {}

    public static String issue(String secret, String subject, String role, long userId, long ttlSeconds, Instant now) {
        requireSecret(secret);
        String payload = "{\"sub\":" + quote(subject) + ",\"role\":" + quote(role) + ",\"uid\":" + userId
                + ",\"iat\":" + now.getEpochSecond() + ",\"exp\":" + (now.getEpochSecond() + ttlSeconds) + "}";
        String body = HEADER + "." + ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + ENC.encodeToString(Hmac.sha256(secret.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8)));
    }

    /** @return claims, or null when the token is malformed, tampered with or expired. */
    public static Claims parse(String secret, String token, Instant now) {
        try {
            requireSecret(secret);
            if (token == null) return null;
            String[] p = token.split("\\.");
            if (p.length != 3 || !HEADER.equals(p[0])) return null;
            String expected = ENC.encodeToString(Hmac.sha256(secret.getBytes(StandardCharsets.UTF_8), (p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8)));
            if (!Hmac.safeEquals(expected, p[2])) return null;
            Map<String, String> m = flatJson(new String(DEC.decode(p[1]), StandardCharsets.UTF_8));
            long exp = Long.parseLong(m.getOrDefault("exp", "0"));
            if (now.getEpochSecond() >= exp) return null;
            return new Claims(m.get("sub"), m.get("role"), Long.parseLong(m.getOrDefault("uid", "0")), exp);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void requireSecret(String s) {
        if (s == null || s.length() < 32) throw new IllegalStateException("JWT secret must be at least 32 characters");
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    /** Parses the flat object we issue ourselves (string and number values only). */
    static Map<String, String> flatJson(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) throw new IllegalArgumentException("not an object");
        int i = 1;
        while (i < s.length() - 1) {
            while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) i++;
            if (i >= s.length() - 1) break;
            int[] pos = {i};
            String key = readString(s, pos);
            i = pos[0];
            while (s.charAt(i) == ':' || Character.isWhitespace(s.charAt(i))) i++;
            String val;
            if (s.charAt(i) == '"') { pos[0] = i; val = readString(s, pos); i = pos[0]; }
            else { int j = i; while (j < s.length() - 1 && s.charAt(j) != ',') j++; val = s.substring(i, j).trim(); i = j; }
            out.put(key, val);
        }
        return out;
    }

    private static String readString(String s, int[] pos) {
        int i = pos[0];
        if (s.charAt(i) != '"') throw new IllegalArgumentException("expected string");
        StringBuilder sb = new StringBuilder();
        i++;
        while (s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c == '\\') {
                char n = s.charAt(i++);
                if (n == 'u') { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                else sb.append(n);
            } else sb.append(c);
        }
        pos[0] = i + 1;
        return sb.toString();
    }
}
