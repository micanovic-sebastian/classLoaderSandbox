/**
 * This is the "untrusted" application that will be run inside
 * the sandbox created by our BlockingClassLoader.
 */
public class UserApp {

    public void run() {
        System.out.println("\n--- [UserApp] Starting ---");

        // 1. Attempt to use an allowed class
        try {
            Class.forName("java.util.ArrayList");
            System.out.println("[UserApp] SUCCESS: java.util.ArrayList was loaded.");
        } catch (Exception e) {
            System.err.println("[UserApp] FAILED: " + e.getMessage());
        }

        // 2. Attempt to use a BLOCKED class (java.io.File)
        try {
            Class.forName("java.io.File");
            System.out.println("[UserApp] FAILED: java.io.File was loaded (sandbox breached!)");
        } catch (Exception e) {
            // We expect this to fail with a ClassNotFoundException
            System.out.println("[UserApp] SUCCESS: " + e.getMessage());
        }
        
        // 3. Attempt to use another BLOCKED class (java.net.Socket)
        try {
            Class.forName("java.net.Socket");
            System.out.println("[UserApp] FAILED: java.net.Socket was loaded (sandbox breached!)");
        } catch (Exception e) {
            System.out.println("[UserApp] SUCCESS: " + e.getMessage());
        }
        
        System.out.println("--- [UserApp] Finished ---");
    }
}