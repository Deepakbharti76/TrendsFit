package runner;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Runs every @Test method of the given classes (no Maven needed). Exit code 1 on any failure. */
public final class MiniRunner {
    public static void main(String[] classNames) throws Exception {
        int pass = 0, fail = 0;
        for (String cn : classNames) {
            Class<?> c = Class.forName(cn);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) continue;
                m.setAccessible(true);
                try {
                    var ctor = c.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object o = ctor.newInstance();
                    m.invoke(o);
                    pass++;
                } catch (Throwable t) {
                    fail++;
                    Throwable r = t.getCause() != null ? t.getCause() : t;
                    System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + " :: " + r);
                }
            }
        }
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
