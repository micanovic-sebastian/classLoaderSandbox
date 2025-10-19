import java.lang.reflect.Method;


/**
 * Main entry point for the Sandbox application.
 * It takes the path to user code and an optional config file as arguments.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java -jar java-sandbox-1.0.0.jar <path_to_user_code_dir> [path_to_config.json]");
            System.exit(1);
        }

        String userCodePath = args[0];
        String configPath = (args.length == 2) ? args[1] : "config.json";

        System.out.println("[Main] Starting Sandbox...");
        System.out.println("[Main] User Code Path: " + userCodePath);
        System.out.println("[Main] Security Config: " + configPath);

        // 1. Create the custom class loader
        BlockingClassLoader customLoader = new BlockingClassLoader(
            ClassLoader.getSystemClassLoader(),
            userCodePath,
            configPath
        );

        // 2. Find the main class in the user's code.
        // We will assume the main class is named "UserApp".
        String mainClassName = "UserApp";

        // 3. Load, instantiate, and run the user's main class
        try {
            Class<?> userAppClass = customLoader.loadClass(mainClassName);
            Object userAppInstance = userAppClass.getDeclaredConstructor().newInstance();
            Method runMethod = userAppClass.getMethod("run");

            System.out.println("\n[Main] --- Executing user code inside sandbox ---");
            runMethod.invoke(userAppInstance);
            System.out.println("[Main] --- User code execution finished ---\n");

        } catch (ClassNotFoundException e) {
            System.err.println("[Main] ERROR: Could not find main class '" + mainClassName + "' in the provided path.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[Main] An error occurred while running the user application.");
            e.printStackTrace();
        }

        System.out.println("[Main] Sandbox execution complete.");
    }
}
