/**
 * This is the "untrusted" application that will be run inside the sandbox.
 * This version has been updated to be a comprehensive test suite that attempts
 * to load every class and a representative from every package defined as "blocked"
 * in the configuration file.
 */
public class UserApp {

    public void run() {
        System.out.println("\n--- [UserApp] Starting Comprehensive Test Suite ---");

        // 1. Attempt to use an allowed class to ensure basic functionality
        try {
            Class.forName("java.util.ArrayList");
            System.out.println("[UserApp] SUCCESS: java.util.ArrayList was loaded as expected.");
        } catch (Exception e) {
            System.err.println("[UserApp] FAILED: Could not load a basic allowed class: " + e.getMessage());
        }

        System.out.println("\n--- Testing Blocked Classes ---");
        String[] blockedClassesToTest = {
            "java.io.File",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.RandomAccessFile",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.URL",
            "java.net.URLConnection"
        };

        for (String className : blockedClassesToTest) {
            testBlockedClass(className);
        }

        System.out.println("\n--- Testing Blocked Packages ---");
        String[] blockedPackagesToTest = {
            // Representative class from the 'java.lang.reflect' package
            "java.lang.reflect.Method",
            // Representative class from the 'java.lang.instrument' package
            "java.lang.instrument.Instrumentation",
            // Representative class from the 'java.sql' package
            "java.sql.DriverManager"
        };

        for (String className : blockedPackagesToTest) {
            testBlockedClass(className);
        }

        System.out.println("\n--- [UserApp] Test Suite Finished ---");
    }

    /**
     * A helper method to test if a given class name is blocked by the ClassLoader.
     * It prints a success message if access is denied, and an error if it is not.
     * @param className The fully qualified name of the class to test.
     */
    private void testBlockedClass(String className) {
        try {
            Class.forName(className);
            System.err.println("[UserApp] FAILED: " + className + " was loaded (sandbox breached!)");
        } catch (Exception e) {
            // We expect this to fail with a ClassNotFoundException or similar.
            System.out.println("[UserApp] SUCCESS: Access to '" + className + "' was correctly blocked.");
        }
    }
}

