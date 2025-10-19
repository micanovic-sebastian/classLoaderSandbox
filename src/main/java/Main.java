import java.lang.reflect.Method;

/**
 * The main entry point. This class is responsible for creating
 * the BlockingClassLoader and running the UserApp within it.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("[Main] Setting up sandboxed class loader...");

        // Define the path to the "untrusted" code
        String sandboxPath = "bin/user";

        // 1. Create an instance of our custom loader
        // Pass the parent loader AND the path to the sandboxed code.
        BlockingClassLoader customLoader = new BlockingClassLoader(
            ClassLoader.getSystemClassLoader(),
            sandboxPath
        );

        // 2. Load the "untrusted" UserApp class using our loader
        // This will now trigger our overriden loadClass/findClass logic.
        Class<?> userAppClass = customLoader.loadClass("UserApp");

        // 3. Create an instance of UserApp (now running in the sandbox)
        Object userAppInstance = userAppClass.getDeclaredConstructor().newInstance();

        // 4. Find its "run" method and invoke it
        Method runMethod = userAppClass.getMethod("run");
        
        System.out.println("[Main] Executing UserApp.run() inside the sandbox...");
        runMethod.invoke(userAppInstance);

        System.out.println("[Main] Execution complete.");
    }
}