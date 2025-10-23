/**
 * This is the "untrusted" test application that will be run inside the sandbox.
 *
 * This test suite attempts to *use* methods or constructors from a wide
 * variety of classes that should be blocked by the security configuration.
 *
 * The test is considered a SUCCESS if a Throwable (like NoClassDefFoundError,
 * ClassNotFoundException, or SecurityException) is caught.
 *
 * The test is a FAILURE if the code executes without error.
 */
public class TestApp {

    private int successCount = 0;
    private int failCount = 0;

    /**
     * A simple functional interface that allows for code that
     * might throw a checked exception.
     */
    @FunctionalInterface
    interface TestOperation {
        void run() throws Exception;
    }

    /**
     * Helper method to run a test.
     * @param className The name of the class/operation being tested (for logging).
     * @param testCode  A lambda expression containing the code to test.
     */
    private void testBlockedMethod(String className, TestOperation testCode) {
        try {
            testCode.run();
            // If we get here, the code executed *without* being blocked.
            System.err.println("[TestApp] FAILURE: '" + className + "' was used! (Sandbox Breach!)");
            failCount++;
        } catch (Throwable t) {
            // This is the desired outcome. The classloader or security layer
            // threw an Error or Exception, preventing the operation.
            System.out.println("[TestApp] SUCCESS: Use of '" + className + "' was correctly blocked (" + t.getClass().getSimpleName() + ").");
            successCount++;
        }
    }

    /**
     * Main entry point for the test application.
     */
    public void run() {
        System.out.println("\n[TestApp] Starting Comprehensive Method-Use Test Suite ---");

        // 1. Test a class that should be ALLOWED
        System.out.println("\n[TestApp] Testing ALLOWED Class (Should PASS) ---");
        testAllowedMethod("java.util.ArrayList", () -> {
            java.util.List<String> list = new java.util.ArrayList<>();
            list.add("This should work!");
            list.clear();
            if (list.size() != 0) {
                throw new RuntimeException("ArrayList is broken?");
            }
        });

        // 2. Test classes that should be BLOCKED
        System.out.println("\n--- [TestApp] Testing BLOCKED Methods (Should all PASS) ---");

        // --- File I/O ---
        testBlockedMethod("java.io.File", () -> {
            java.io.File f = new java.io.File("test.txt");
            f.exists();
            f.createNewFile();
        });
        testBlockedMethod("java.io.FileReader", () -> {
            new java.io.FileReader("test.txt").read();
        });
        testBlockedMethod("java.io.FileWriter", () -> {
            new java.io.FileWriter("test.txt").write("test");
        });
        testBlockedMethod("java.io.FileInputStream", () -> {
            new java.io.FileInputStream("test.txt").read();
        });
        testBlockedMethod("java.io.FileOutputStream", () -> {
            new java.io.FileOutputStream("test.txt").write(1);
        });
        testBlockedMethod("java.io.RandomAccessFile", () -> {
            new java.io.RandomAccessFile("test.txt", "r").seek(0);
        });
        testBlockedMethod("java.io.FileDescriptor", () -> {
            java.io.FileDescriptor.in.valid();
        });

        // --- NIO (New I/O) ---
        testBlockedMethod("java.nio.file.Files", () -> {
            java.nio.file.Files.exists(java.nio.file.Paths.get("test.txt"));
        });
        testBlockedMethod("java.nio.file.Paths", () -> {
            java.nio.file.Paths.get("test.txt", "more");
        });
        testBlockedMethod("java.nio.file.Path", () -> {
            java.nio.file.Path.of("test.txt").toAbsolutePath();
        });
        testBlockedMethod("java.nio.channels.FileChannel", () -> {
            new java.io.FileInputStream("test.txt").getChannel().size();
        });

        // --- Networking ---
        testBlockedMethod("java.net.Socket", () -> {
            new java.net.Socket("google.com", 80).close();
        });
        testBlockedMethod("java.net.ServerSocket", () -> {
            new java.net.ServerSocket(9999).accept();
        });
        testBlockedMethod("java.net.URL", () -> {
            new java.net.URL("http://google.com").openStream();
        });
        testBlockedMethod("java.net.URLConnection", () -> {
            new java.net.URL("http://google.com").openConnection().connect();
        });
        testBlockedMethod("java.net.HttpURLConnection", () -> {
            ((java.net.HttpURLConnection) new java.net.URL("http://google.com").openConnection()).setRequestMethod("GET");
        });
        testBlockedMethod("java.net.DatagramSocket", () -> {
            new java.net.DatagramSocket(9998).close();
        });

        // --- Reflection ---
        testBlockedMethod("java.lang.reflect.Method", () -> {
            java.lang.reflect.Method m = String.class.getMethod("length");
            m.invoke("test");
        });
        testBlockedMethod("java.lang.reflect.Constructor", () -> {
            java.lang.reflect.Constructor<String> c = String.class.getConstructor(byte[].class);
            c.newInstance(new byte[]{1, 2});
        });
        testBlockedMethod("java.lang.reflect.AccessibleObject", () -> {
            java.lang.reflect.Field f = String.class.getDeclaredField("value");
            f.setAccessible(true); // This is a method on AccessibleObject
        });
        testBlockedMethod("java.lang.reflect.Proxy", () -> {
            java.lang.reflect.Proxy.newProxyInstance(null, new Class<?>[]{}, (p, m, a) -> null);
        });

        // --- Process Execution ---
        testBlockedMethod("java.lang.Runtime", () -> {
            java.lang.Runtime.getRuntime().exec("calc");
        });
        testBlockedMethod("java.lang.Process", () -> {
            java.lang.Process p = java.lang.Runtime.getRuntime().exec("calc");
            p.waitFor();
        });
        testBlockedMethod("java.lang.ProcessBuilder", () -> {
            new java.lang.ProcessBuilder("calc").start();
        });

        // --- ClassLoader ---
        testBlockedMethod("java.net.URLClassLoader", () -> {
            new java.net.URLClassLoader(new java.net.URL[0]).loadClass("java.io.File");
        });

        // --- SQL ---
        testBlockedMethod("java.sql.DriverManager", () -> {
            java.sql.DriverManager.getConnection("jdbc:bogus:db");
        });
        testBlockedMethod("java.sql.Connection", () -> {
            java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:bogus:db");
            c.createStatement();
        });

        // --- AWT ---
        testBlockedMethod("java.awt.Robot", () -> {
            java.awt.Robot r = new java.awt.Robot();
            r.mouseMove(0, 0);
        });

        // --- Internal/Unsafe ---
        testBlockedMethod("sun.misc.Unsafe", () -> {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            unsafe.allocateMemory(1024);
        });

        // --- Blocked Package Test ---
        testBlockedMethod("java.lang.instrument.Instrumentation", () -> {
            // This is an interface, just trying to load it is enough.
            Class.forName("java.lang.instrument.Instrumentation");
        });


        // 3. Final Report
        System.out.println("\n--- [TestApp] Test Suite Finished ---");
        System.out.printf("[TestApp] FINAL REPORT: %d Tests Succeeded, %d Tests Failed\n", successCount, failCount);
    }

    /**
     * Helper method to run an "allowed" test.
     * @param className The name of the class/operation being tested (for logging).
     * @param testCode  A lambda expression containing the code to test.
     */
    private void testAllowedMethod(String className, TestOperation testCode) {
        try {
            testCode.run();
            // This is the desired outcome
            System.out.println("[TestApp] SUCCESS: Allowed class '" + className + "' was instantiated and used.");
            successCount++;
        } catch (Throwable t) {
            // This is a failure, the allowed class was blocked.
            System.err.println("[TestApp] FAILURE: Allowed class '" + className + "' was BLOCKED: " + t);
            failCount++;
        }
    }
}

