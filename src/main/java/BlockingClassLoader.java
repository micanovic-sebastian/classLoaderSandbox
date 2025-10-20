import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * A custom ClassLoader that blocks specified classes/packages and loads
 * user-provided code from a separate directory OR a JAR file.
 */
public class BlockingClassLoader extends ClassLoader {

    private final Set<String> blockedClasses = new HashSet<>();
    private final Set<String> blockedPackages = new HashSet<>();
    private final Map<String, byte[]> userClasses = new HashMap<>();

    public BlockingClassLoader(ClassLoader parent, String userCodePath, String configPath) {
        super(parent);
        loadConfig(configPath);

        // Check if userCodePath is a directory or a JAR file
        Path path = Paths.get(userCodePath);
        if (Files.isDirectory(path)) {
            System.out.println("[ClassLoader] Loading from directory: " + userCodePath);
            loadUserClassesFromDir(userCodePath);
        } else if (userCodePath.endsWith(".jar") && Files.isRegularFile(path)) {
            System.out.println("[ClassLoader] Loading from JAR: " + userCodePath);
            loadUserClassesFromJar(userCodePath);
        } else {
            System.err.println("[ClassLoader] ERROR: userCodePath is not a valid directory or .jar file: " + userCodePath);
        }
    }

    /**
     * Loads security rules from the specified JSON config file.
     * (Unchanged from original)
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
     * (Renamed from loadUserClasses)
     */
    private void loadUserClassesFromDir(String userCodePath) {
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
     * Scans a JAR file and loads all .class files into memory.
     * (New method)
     */
    private void loadUserClassesFromJar(String jarPath) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace(".class", "")
                            .replace('/', '.');

                    try (InputStream is = new BufferedInputStream(jarFile.getInputStream(entry));
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }

                        byte[] classBytes = baos.toByteArray();
                        userClasses.put(className, classBytes);
                        System.out.println("[ClassLoader] Discovered user class from JAR: " + className);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ClassLoader] ERROR: Could not read user code from JAR: " + jarPath);
        }
    }

    /**
     * Checks if a class name is explicitly blocked or belongs to a blocked package.
     * (Unchanged from original)
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
        byte[] classBytes = userClasses.get(name);
        if (classBytes != null) {
            System.out.println("[ClassLoader] Defining sandboxed class: " + name);
            return defineClass(name, classBytes, 0, classBytes.length);
        }
        return super.findClass(name);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. Check blocklist first.
        if (isBlocked(name)) {
            throw new ClassNotFoundException("Access denied! The class '" + name + "' is blocked by security policy.");
        }

        // 2. Check if it's one of our loaded user classes.
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (userClasses.containsKey(name)) {
                    // It's a user class, find it (which will call defineClass)
                    c = findClass(name);
                } else {
                    // Not a user class, delegate to parent
                    c = super.loadClass(name, false); // Pass 'false' to 'resolve' to avoid loops
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}