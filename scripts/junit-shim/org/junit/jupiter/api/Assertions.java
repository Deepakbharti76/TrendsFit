package org.junit.jupiter.api;

import java.util.Objects;

/** Local stand-in for the few JUnit 5 assertions the domain tests use. */
public final class Assertions {
    private Assertions() {}

    public static void assertTrue(boolean c) { if (!c) throw new AssertionError("expected true"); }
    public static void assertTrue(boolean c, String m) { if (!c) throw new AssertionError(m); }
    public static void assertFalse(boolean c) { if (c) throw new AssertionError("expected false"); }
    public static void assertFalse(boolean c, String m) { if (c) throw new AssertionError(m); }
    public static void assertNull(Object o) { if (o != null) throw new AssertionError("expected null but was " + o); }
    public static void assertNull(Object o, String m) { if (o != null) throw new AssertionError(m + ": expected null but was " + o); }
    public static void assertNotNull(Object o, String m) { if (o == null) throw new AssertionError(m); }
    public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("expected not null"); }
    public static void assertEquals(Object e, Object a) { if (!Objects.equals(e, a)) throw new AssertionError("expected <" + e + "> but was <" + a + ">"); }
    public static void assertEquals(Object e, Object a, String m) { if (!Objects.equals(e, a)) throw new AssertionError(m + ": expected <" + e + "> but was <" + a + ">"); }
    public static void assertEquals(long e, long a) { if (e != a) throw new AssertionError("expected <" + e + "> but was <" + a + ">"); }
    public static void assertEquals(double e, double a, double d) { if (Math.abs(e - a) > d) throw new AssertionError("expected <" + e + "> but was <" + a + ">"); }

    public interface Executable { void execute() throws Throwable; }

    public static <T extends Throwable> T assertThrows(Class<T> type, Executable ex) {
        try { ex.execute(); } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
