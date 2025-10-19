import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A custom ClassLoader that blocks access to specific classes
 * defined in a config.json file.
 *
 * This version is modified to explicitly load 'UserApp' from a
 * specified directory, forcing it into the sandbox.
 */
public class BlockingClassLoader extends ClassLoader {

    private final Set<String> blockedClasses = new HashSet<>();
    private final Path sandboxDir; // Path to the sandboxed code (e.g., "bin/user")

    /**
     * Constructor now takes the path to the sandboxed directory.
     * @param parent The parent classloader.
     * @param sandboxDir The directory containing the .class files to sandbox.
     */
    public BlockingClassLoader(ClassLoader parent, String sandboxDir) {
        super(parent);
        this.sandboxDir = Paths.get(sandboxDir);
        loadConfig("config.json");
    }

    /**
     * Reads and parses the config.json file to populate the blocklist.
     */
    private void loadConfig(String configFilePath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(configFilePath)));

            Pattern p = Pattern.compile("\"blockedClasses\"\\s*:\\s*\\[([^\\]]+)\\]");
            Matcher m = p.matcher(content);

            if (m.find()) {
                String arrayContents = m.group(1);
                Pattern stringPattern = Pattern.compile("\"(.*?)\"");
                Matcher stringMatcher = stringPattern.matcher(arrayContents);
                while (stringMatcher.find()) {
                    String className = stringMatcher.group(1);
                    blockedClasses.add(className);
                    System.out.println("[ClassLoader] Blocking: " + className);
                }
            }
            System.out.println("[ClassLoader] " + blockedClasses.size() + " classes will be blocked.");

        } catch (IOException e) {
            System.err.println("[ClassLoader] WARNING: Could not load config.json. No classes will be blocked.");
            e.printStackTrace();
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // 1. Check if the class is on the blocklist *FIRST*.
        if (blockedClasses.contains(name)) {
            // This is the check that will now block java.io.File
            throw new ClassNotFoundException("Access denied! The class '" + name + "' is blocked by policy.");
        }

        // 2. Check if it is the "UserApp" class.
        // We *must* load this class ourselves, not delegate to the parent.
        if (name.equals("UserApp")) {
            // Check if we've already loaded it
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            // Not loaded, so load it ourselves using findClass()
            return findClass(name);
        }

        // 3. For all other classes (like java.util.ArrayList, etc.),
        // delegate to the parent loader as usual.
        return super.loadClass(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // This method is called by loadClass when it needs to load a class itself.
        // We only expect 'UserApp' to be loaded this way.
        if (!name.equals("UserApp")) {
            return super.findClass(name);
        }

        try {
            // Convert class name to file path: UserApp -> UserApp.class
            Path classFile = sandboxDir.resolve(name + ".class");

            System.out.println("[ClassLoader] Finding and loading class from sandbox: " + classFile);

            byte[] classBytes = Files.readAllBytes(classFile);

            // Define the class from its bytes
            return defineClass(name, classBytes, 0, classBytes.length);

        } catch (IOException e) {
            throw new ClassNotFoundException("Could not find or load class " + name, e);
        }
    }
}