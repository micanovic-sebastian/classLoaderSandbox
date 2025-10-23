/**
 * Das ist die "unsichere" Test-Anwendung die in der Sandbox laufen wird
 *
 * Diese Test-Suite versucht Methoden oder Konstruktoren von Klassen zu nutzen
 * die durch die Sicherheitskonfiguration blockiert sein sollten
 *
 * Der Test ist ein Erfolg wenn ein Throwable (zb NoClassDefFoundError
 * ClassNotFoundException oder SecurityException) gefangen wird
 *
 * Der Test ist ein Fehler wenn der Code ohne Fehler durchläuft
 */
public class TestApp {

    private int successCount = 0;
    private int failCount = 0;

    /**
     * Ein einfaches FunctionalInterface das Code erlaubt
     * der eine Ausnahme werfen könnte
     */
    @FunctionalInterface
    interface TestOperation {
        void run() throws Exception;
    }

    /**
     * Hilfsmethode um einen Test auszuführen
     * @param className der Name der Klasse/Operation für's Logging
     * @param testCode  ein Lambda-Ausdruck mit dem Testcode
     */
    private void testBlockedMethod(String className, TestOperation testCode) {
        try {
            testCode.run();
            // wenn wir hier ankommen wurde der Code *nicht* blockiert
            System.err.println("[TestApp] FAILURE: '" + className + "' was used! (Sandbox Breach!)");
            failCount++;
        } catch (Throwable t) {
            // das ist das gewünschte Ergebnis der ClassLoader oder SecurityManager
            // hat einen Error oder eine Exception geworfen
            System.out.println("[TestApp] SUCCESS: Use of '" + className + "' was correctly blocked (" + t.getClass().getSimpleName() + ").");
            successCount++;
        }
    }

    /**
     * Haupteinstiegspunkt für die Testanwendung
     */
    public void run() {
        System.out.println("\n[TestApp] Starting Comprehensive Method-Use Test Suite ---");

        // teste eine erlaubt Klasse
        System.out.println("\n[TestApp] Testing ALLOWED Class (Should PASS) ---");
        testAllowedMethod("java.util.ArrayList", () -> {
            java.util.List<String> list = new java.util.ArrayList<>();
            list.add("This should work!");
            list.clear();
            if (list.size() != 0) {
                throw new RuntimeException("ArrayList is broken?");
            }
        });

        // teste Klassen nicht erlaubte Klassen
        System.out.println("\n--- [TestApp] Testing BLOCKED Methods (Should all PASS) ---");

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
            f.setAccessible(true); // das ist eine Methode von AccessibleObject
        });
        testBlockedMethod("java.lang.reflect.Proxy", () -> {
            java.lang.reflect.Proxy.newProxyInstance(null, new Class<?>[]{}, (p, m, a) -> null);
        });

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

        testBlockedMethod("java.net.URLClassLoader", () -> {
            new java.net.URLClassLoader(new java.net.URL[0]).loadClass("java.io.File");
        });

        testBlockedMethod("java.sql.DriverManager", () -> {
            java.sql.DriverManager.getConnection("jdbc:bogus:db");
        });
        testBlockedMethod("java.sql.Connection", () -> {
            java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:bogus:db");
            c.createStatement();
        });

        testBlockedMethod("java.awt.Robot", () -> {
            java.awt.Robot r = new java.awt.Robot();
            r.mouseMove(0, 0);
        });

        testBlockedMethod("sun.misc.Unsafe", () -> {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            unsafe.allocateMemory(1024);
        });

        testBlockedMethod("java.lang.instrument.Instrumentation", () -> {
            // das ist ein Interface es reicht zu versuchen es zu laden
            Class.forName("java.lang.instrument.Instrumentation");
        });


        // Logausgabe
        System.out.println("\n--- [TestApp] Test Suite Finished ---");
        System.out.printf("[TestApp] FINAL REPORT: %d Tests Succeeded, %d Tests Failed\n", successCount, failCount);
    }

    /**
     * Hilfsmethode um einen "erlaubten" Test auszuführen
     * @param className der Name der Klasse/Operation für's Logging
     * @param testCode  ein Lambda-Ausdruck mit dem Testcode
     */
    private void testAllowedMethod(String className, TestOperation testCode) {
        try {
            testCode.run();
            // das ist das gewünschte Ergebnis
            System.out.println("[TestApp] SUCCESS: Allowed class '" + className + "' was instantiated and used.");
            successCount++;
        } catch (Throwable t) {
            // das ist ein Fehler die erlaubte Klasse wurde blockiert
            System.err.println("[TestApp] FAILURE: Allowed class '" + className + "' was BLOCKED: " + t);
            failCount++;
        }
    }
}

