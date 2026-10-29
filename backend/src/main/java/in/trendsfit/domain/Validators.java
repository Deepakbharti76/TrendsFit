package in.trendsfit.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Input rules shared by checkout, registration and address forms (the backend always re-checks them). */
public final class Validators {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private Validators() {}

    public static boolean email(String s) { return s != null && s.length() <= 190 && EMAIL.matcher(s.trim()).matches(); }

    public static boolean pincode(String s) { return s != null && PINCODE.matcher(s.trim()).matches(); }

    /** Normalises +91 / 0 prefixes and spaces to a 10-digit Indian mobile, or returns null when invalid. */
    public static String mobile(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9]", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return MOBILE.matcher(d).matches() ? d : null;
    }

    /**
     * Strict form used by the mandatory mobile fields: exactly 10 digits, starting 6-9, with no spaces,
     * letters, "+91" or "0" prefix. Returns the number, or null when invalid.
     */
    public static String mobileExact(String s) {
        if (s == null) return null;
        String t = s.trim();
        return MOBILE.matcher(t).matches() ? t : null;
    }

    /** @return list of problems, empty when the password is acceptable. */
    public static List<String> passwordProblems(String password, String email) {
        List<String> p = new ArrayList<>();
        if (password == null || password.length() < 10) p.add("Use at least 10 characters.");
        if (password != null) {
            if (!password.matches(".*[a-z].*")) p.add("Add a lowercase letter.");
            if (!password.matches(".*[A-Z].*")) p.add("Add an uppercase letter.");
            if (!password.matches(".*[0-9].*")) p.add("Add a number.");
            if (password.length() > 72) p.add("Use at most 72 characters.");
           
        }
        return p;
    }

    public static String slugify(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "item" : (n.length() > 150 ? n.substring(0, 150) : n);
    }
}
