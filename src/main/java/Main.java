import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main entry point für die Sandbox
 *
 * pathfrom=/path/to/source/or/jar  (Nötig)
 * main-class=com.example.UserApp   (Nötig)
 * pathto=/path/to/compile/output   (Optional Standard ".")
 * config=/path/to/config.json      (Optional Standard "config.json")
 * log=/path/to/cclsandbox.log      (Optional Standard "cclsandbox.log")
 */
public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);

        String logFile = params.getOrDefault("log", "cclsandbox.log");
        // Setzt die Variable für log4j2.xml
        ThreadContext.put("logFile", logFile);

        if (params.containsKey("help") || !params.containsKey("pathfrom") || !params.containsKey("main-class")) {
            printUsage();
            return;
        }

        String pathFrom = params.get("pathfrom");
        String mainClassName = params.get("main-class");
        String pathFromConfig = params.getOrDefault("config", "config.json");
        // Standard-Ausgabepfad ist das aktuelle Verzeichnis
        String pathFromCompileTo = params.getOrDefault("pathto", ".");
        String effectiveUserCodePath;

        logger.info("Starte Sandbox...");
        logger.info("Security Config: " + pathFromConfig);
        logger.info("User Main Class: " + mainClassName);
        logger.info("Log File: " + logFile);

        Path sourcePath = Paths.get(pathFrom);
        boolean isJar = pathFrom.endsWith(".jar") && Files.isRegularFile(sourcePath);
        boolean isDir = Files.isDirectory(sourcePath);

        try {
            if (isJar) {
                logger.info("Lade von JAR: " + pathFrom);
                effectiveUserCodePath = pathFrom;
            } else if (isDir) {
                // Prüfen ob das Verzeichnis .java-Dateien enthält
                boolean hasJavaFiles;
                try (Stream<Path> stream = Files.walk(sourcePath)) {
                    hasJavaFiles = stream.anyMatch(file -> file.toString().endsWith(".java"));
                } catch (IOException e) {
                    logger.error("ERROR: Konnte Quellverzeichnis nicht prüfen: " + pathFrom, e);
                    System.exit(1);
                    return;
                }

                if (hasJavaFiles) {
                    logger.info("Lade von Quellverzeichnis: " + pathFrom);
                    logger.info("Kompiliere nach: " + pathFromCompileTo);

                    boolean compiled = compileSourceFiles(sourcePath, Paths.get(pathFromCompileTo));
                    if (!compiled) {
                        logger.error("ERROR: Kompilierung fehlgeschlagen Programm wird beendet");
                        System.exit(1);
                    }
                    logger.info("Kompilierung erfolgreich");
                    effectiveUserCodePath = pathFromCompileTo; // Kompilierpfad nutzen
                } else {
                    logger.info("Lade von vorkompiliertem Verzeichnis: " + pathFrom);
                    // Keine .java-Dateien gefunden wir nehmen an es ist ein Ordner mit .class-Dateien
                    effectiveUserCodePath = pathFrom; // pathFrom direkt nutzen
                }
            } else {
                logger.error("ERROR: --pathfrom ist kein gültiges Verzeichnis oder .jar-Archiv: " + pathFrom);
                System.exit(1);
                return;
            }

            // 1. Custom ClassLoader erstellen
            BlockingClassLoader customLoader = new BlockingClassLoader(
                    ClassLoader.getSystemClassLoader(),
                    effectiveUserCodePath,
                    pathFromConfig
            );

            // 2. Main-Klasse des Benutzers laden und ausführen
            logger.info("\n--- Führe Benutzercode in Sandbox aus ---");
            Class<?> userAppClass = customLoader.loadClass(mainClassName);

            Method mainMethod = null;
            try {
                // Zuerst nach einer Standard 'public static void main(String[] args)' suchen
                mainMethod = userAppClass.getMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                // Nicht gefunden dann probieren wir 'run()'
            }

            if (mainMethod != null) {
                // 'main(String[])' gefunden Aufruf
                logger.info("Fand 'public static void main(String[] args)' Methode Rufe auf...");
                // Die 'main' Methode ist static erster Parameter für invoke ist null
                // Wir übergeben ein leeres String-Array
                mainMethod.invoke(null, (Object) new String[0]);
            } else {
                // 'main' nicht gefunden suche nach 'public void run()'
                logger.info("Keine 'main(String[])' Methode gefunden Suche nach 'public void run()'...");
                Method runMethod = null;
                try {
                    runMethod = userAppClass.getMethod("run");
                    // 'run()' gefunden Klasse instanziieren und aufrufen
                    Object userAppInstance = userAppClass.getDeclaredConstructor().newInstance();
                    runMethod.invoke(userAppInstance);
                } catch (NoSuchMethodException runException) {
                    // Keine der Methoden wurde gefunden Das ist der Fehler
                    logger.error("ERROR: Main-Klasse '" + mainClassName + "' hat weder 'public static void main(String[] args)' noch 'public void run()' Methode", runException);
                    throw runException; // Exception weiterwerfen
                }
            }


        } catch (ClassNotFoundException e) {
            logger.error("ERROR: Konnte Main-Klasse '" + mainClassName + "' in " + sourcePath + " nicht finden", e);
            System.exit(1);
        } catch (NoSuchMethodException e) {
            // Fängt die Exception falls weder 'main' noch 'run' gefunden wurde
            logger.error("Ausführung fehlgeschlagen: Konnte keinen passenden Einstiegspunkt finden", e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Ein Fehler ist während der Ausführung der Benutzeranwendung aufgetreten", e);
        }

        logger.info("Sandbox-Ausführung beendet");
    }

    /**
     * Kompiliert alle .java-Dateien aus einem Quellverzeichnis in ein Ausgabeverzeichnis
     */
    private static boolean compileSourceFiles(Path sourceDir, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            logger.error("FEHLER: Keinen Java-Compiler gefunden Bitte mit einem JDK ausführen nicht nur JRE");
            return false;
        }

        try (Stream<Path> stream = Files.walk(sourceDir)) {
            List<String> javaFiles = stream
                    .filter(file -> file.toString().endsWith(".java"))
                    .map(Path::toString)
                    .collect(Collectors.toList());

            if (javaFiles.isEmpty()) {
                logger.warn("WARNUNG: Keine .java-Dateien in " + sourceDir + " gefunden");
                return true; // Kein Fehler nur nichts zu tun
            }

            // Sicherstellen dass das Ausgabeverzeichnis existiert
            Files.createDirectories(outputDir);

            String[] compilerArgs = {
                    "-d", outputDir.toString()
            };

            // Compiler-Argumente und Dateiliste zusammenführen
            Stream<String> argsStream = Stream.concat(Stream.of(compilerArgs), javaFiles.stream());

            int compilationResult = compiler.run(null, null, null, argsStream.toArray(String[]::new));
            return (compilationResult == 0);

        } catch (IOException e) {
            logger.error("ERROR: Konnte Quelldateien nicht lesen", e);
            return false;
        }
    }


    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            // Help-Argument behandeln (mit oder ohne --)
            if (arg.equals("help") || arg.equals("--help")) {
                params.put("help", "");
                continue;
            }

            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0];
                // Präfix entfernen falls vorhanden
                if (key.startsWith("--")) {
                    key = key.substring(2);
                }
                params.put(key, parts[1]);
            }
        }
        return params;
    }

    private static void printUsage() {
        // logger.info für die Nutzungsinfos verwenden geht an Konsole/Log
        logger.info("Usage: java -jar cclsandbox.jar [options]");
        logger.info("Options:");
        logger.info("  --pathfrom=<path>     (Nötig) Pfad zum Quellverzeichnis (.java) oder einer .jar-Datei");
        logger.info("  --main-class=<class>  (Nötig) Vollständiger Klassenname zur Ausführung (zB com.example.UserApp)");
        logger.info("  --pathto=<path>       (Optional) Verzeichnis wohin .class-Dateien kompiliert werden");
        logger.info("                      (Standard ist aktuelles Arbeitsverzeichnis: '.')");
        logger.info("  --config=<path>       (Optional) Pfad zur config.json Sicherheitskonfigurationsdatei");
        logger.info("                      (Standard ist 'config.json')");
        logger.info("  --log=<path>          (Optional) Pfad zur Log-Datei");
        logger.info("                      (Standard ist 'cclsandbox.log')");
    }
}
