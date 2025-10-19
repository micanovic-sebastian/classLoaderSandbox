/**
 * This is the "untrusted" application that will be run inside the sandbox.
 */
public class UserApp {

    public void run() {
        System.out.println("\n--- [UserApp] Starting ---");

        // 1. Attempt to use an allowed class
        try {
            Class.forName("java.util.ArrayList");
            System.out.println("[UserApp] SUCCESS: java.util.ArrayList was loaded.");
        } catch (Exception e) {
            System.err.println("[UserApp] FAILED: Could not load an allowed class: " + e.getMessage());
        }

        // 2. Attempt to use a BLOCKED class (java.io.File)
        testBlockedClass("java.io.File");

        // 3. Attempt to use another BLOCKED class (System.exit)
        testBlockedClass("java.lang.System");

        // 4. Attempt to use another BLOCKED class (Reflection)
        testBlockedClass("java.lang.reflect.Method");

        System.out.println("--- [UserApp] Finished ---");
    }

    private void testBlockedClass(String className) {
        try {
            Class.forName(className);
            System.err.println("[UserApp] FAILED: " + className + " was loaded (sandbox breached!)");
        } catch (Exception e) {
            // We expect this to fail with a ClassNotFoundException
            System.out.println("[UserApp] SUCCESS: Access to '" + className + "' was correctly blocked.");
        }
    }
}
