import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * Ein eigener ClassLoader der bestimmte Klassen/Pakete blockiert und
 * Code aus einem Verzeichnis ODER einer JAR-Datei lädt
 */
public class BlockingClassLoader extends ClassLoader {

    private static final Logger logger = LogManager.getLogger(BlockingClassLoader.class);

    private final Set<String> blockedClasses = new HashSet<>();
    private final Set<String> blockedPackages = new HashSet<>();
    private final Map<String, byte[]> userClasses = new HashMap<>();

    public BlockingClassLoader(ClassLoader parent, String userCodePath, String configPath) {
        super(parent);
        loadConfig(configPath);

        // Prüfen ob userCodePath ein Verzeichnis oder eine JAR ist
        Path path = Paths.get(userCodePath);
        if (Files.isDirectory(path)) {
            logger.info("Loading from directory: " + userCodePath);
            loadUserClassesFromDir(userCodePath);
        } else if (userCodePath.endsWith(".jar") && Files.isRegularFile(path)) {
            logger.info("Loading from JAR: " + userCodePath);
            loadUserClassesFromJar(userCodePath);
        } else {
            logger.error("ERROR: userCodePath is not a valid directory or .jar file: " + userCodePath);
        }
    }

    /**
     * Lädt die Sicherheitsregeln aus der JSON-Konfigdatei
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

            logger.info("Loaded " + blockedClasses.size() + " blocked classes and "
                    + blockedPackages.size() + " blocked packages from " + configFilePath);
        } catch (Exception e) {
            logger.warn("WARNING: Could not load config file '" + configFilePath + "'. No classes will be blocked.", e);
        }
    }

    /**
     * Scannt das Verzeichnis und lädt alle .class-Dateien in den Speicher
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
                            logger.debug("Discovered user class: " + className);
                        } catch (IOException e) {
                            logger.warn("Failed to load class file: " + classFile, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("ERROR: Could not read user code from directory: " + userCodePath, e);
        }
    }

    /**
     * Scannt eine JAR-Datei und lädt alle .class-Dateien in den Speicher
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
                        logger.debug("Discovered user class from JAR: " + className);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("ERROR: Could not read user code from JAR: " + jarPath, e);
        }
    }

    /**
     * Prüft ob eine Klasse blockiert ist (direkt oder per Package)
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
            logger.debug("Defining sandboxed class: " + name);
            return defineClass(name, classBytes, 0, classBytes.length);
        }
        return super.findClass(name);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. Blocklist prüfen
        if (isBlocked(name)) {
            logger.warn("Access denied! The class '" + name + "' is blocked by security policy.");
            throw new ClassNotFoundException("Access denied! The class '" + name + "' is blocked by security policy.");
        }

        // 2. Prüfen ob es eine unserer User-Klassen ist
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (userClasses.containsKey(name)) {
                    // Ist eine User-Klasse also selbst laden via findClass
                    c = findClass(name);
                } else {
                    // Keine User-Klasse an Parent delegieren
                    c = super.loadClass(name, false); // 'false' für resolve um Zyklen zu vermeiden
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}

