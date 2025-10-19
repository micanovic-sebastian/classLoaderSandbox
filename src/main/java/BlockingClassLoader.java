import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A custom ClassLoader that blocks specified classes/packages and loads
 * user-provided code from a separate directory.
 */
public class BlockingClassLoader extends ClassLoader {

    private final Set<String> blockedClasses = new HashSet<>();
    private final Set<String> blockedPackages = new HashSet<>();
    private final Map<String, byte[]> userClasses = new HashMap<>();

    public BlockingClassLoader(ClassLoader parent, String userCodePath, String configPath) {
        super(parent);
        loadConfig(configPath);
        loadUserClasses(userCodePath);
    }

    /**
     * Loads security rules from the specified JSON config file.
     * Now reads both "blockedClasses" and "blockedPackages".
     */
    private void loadConfig(String configFilePath) {
        try (InputStream is = Files.newInputStream(Paths.get(configFilePath))) {
            JSONTokener tokener = new JSONTokener(is);
            JSONObject config = new JSONObject(tokener);

            if (config.has("blockedClasses")) {
                JSONArray blocked = config.getJSONArray("blockedClasses");
                for (int i = 0; i < blocked.length(); i++) {
                    blockedClasses.add(blocked.getString(i));
                }
            }

            if (config.has("blockedPackages")) {
                JSONArray blocked = config.getJSONArray("blockedPackages");
                for (int i = 0; i < blocked.length(); i++) {
                    blockedPackages.add(blocked.getString(i));
                }
            }

            System.out.println("[ClassLoader] Loaded " + blockedClasses.size() + " blocked classes and "
                             + blockedPackages.size() + " blocked packages from " + configFilePath);
        } catch (Exception e) {
            System.err.println("[ClassLoader] WARNING: Could not load config file '" + configFilePath + "'. No classes will be blocked.");
        }
    }

    /**
     * Scans the user code directory and loads all .class files into memory.
     */
    private void loadUserClasses(String userCodePath) {
        Path startPath = Paths.get(userCodePath);
        try (Stream<Path> stream = Files.walk(startPath)) {
            stream.filter(path -> path.toString().endsWith(".class"))
                  .forEach(classFile -> {
                      try {
                          String className = startPath.relativize(classFile).toString()
                                                      .replace(".class", "")
                                                      .replace(java.io.File.separatorChar, '.');
                          byte[] classBytes = Files.readAllBytes(classFile);
                          userClasses.put(className, classBytes);
                          System.out.println("[ClassLoader] Discovered user class: " + className);
                      } catch (IOException e) {
                          System.err.println("[ClassLoader] Failed to load class file: " + classFile);
                      }
                  });
        } catch (IOException e) {
            System.err.println("[ClassLoader] ERROR: Could not read user code from directory: " + userCodePath);
        }
    }

    /**
     * Checks if a class name is explicitly blocked or belongs to a blocked package.
     */
    private boolean isBlocked(String name) {
        if (blockedClasses.contains(name)) {
            return true;
        }
        for (String pkg : blockedPackages) {
            if (name.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (userClasses.containsKey(name)) {
            byte[] classBytes = userClasses.get(name);
            System.out.println("[ClassLoader] Defining sandboxed class: " + name);
            return defineClass(name, classBytes, 0, classBytes.length);
        }
        return super.findClass(name);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. Check blocklist first. This is the primary security boundary.
        if (isBlocked(name)) {
            throw new ClassNotFoundException("Access denied! The class '" + name + "' is blocked by security policy.");
        }

        // 2. Check if it's one of the classes we are supposed to sandbox.
        if (userClasses.containsKey(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = findClass(name);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }

        // 3. For all other classes (e.g., java.util.ArrayList), delegate to the parent.
        return super.loadClass(name, resolve);
    }
}

