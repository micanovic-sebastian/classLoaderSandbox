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
 * Main entry point for the Sandbox application.
 *
 * Supports new argument-based execution:
 * --pathfrom=/path/to/source/or/jar (Required)
 * --main-class=com.example.UserApp   (Required)
 * --pathto=/path/to/compile/output   (Optional, defaults to current dir ".")
 * --config=/path/to/config.json      (Optional, defaults to "config.json")
 */
public class Main {

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);

        if (params.containsKey("help") || !params.containsKey("pathfrom") || !params.containsKey("main-class")) {
            printUsage();
            return;
        }

        String pathFrom = params.get("pathfrom");
        String mainClassName = params.get("main-class");
        String pathFromConfig = params.getOrDefault("config", "config.json");
        // Default output path is the current working directory
        String pathFromCompileTo = params.getOrDefault("pathto", ".");
        String effectiveUserCodePath;

        System.out.println("[Main] Starting Sandbox...");
        System.out.println("[Main] Security Config: " + pathFromConfig);
        System.out.println("[Main] User Main Class: " + mainClassName);

        Path sourcePath = Paths.get(pathFrom);
        boolean isJar = pathFrom.endsWith(".jar") && Files.isRegularFile(sourcePath);
        boolean isDir = Files.isDirectory(sourcePath);

        try {
if (isJar) {
                System.out.println("[Main] Loading from JAR: " + pathFrom);
                effectiveUserCodePath = pathFrom;
            } else if (isDir) {
                // Check if the directory contains .java files
                boolean hasJavaFiles;
                try (Stream<Path> stream = Files.walk(sourcePath)) {
                    hasJavaFiles = stream.anyMatch(file -> file.toString().endsWith(".java"));
                } catch (IOException e) {
                    System.err.println("[Main] ERROR: Could not inspect source directory: " + pathFrom);
                    System.exit(1);
                    return;
                }

                if (hasJavaFiles) {
                    System.out.println("[Main] Loading from source directory: " + pathFrom);
                    System.out.println("[Main] Compiling to: " + pathFromCompileTo);

                    boolean compiled = compileSourceFiles(sourcePath, Paths.get(pathFromCompileTo));
                    if (!compiled) {
                        System.err.println("[Main] ERROR: Compilation failed. Exiting.");
                        System.exit(1);
                    }
                    System.out.println("[Main] Compilation successful.");
                    effectiveUserCodePath = pathFromCompileTo; // Use compile path
                } else {
                    System.out.println("[Main] Loading from pre-compiled directory: " + pathFrom);
                    // No .java files, assume it's a directory of .class files
                    effectiveUserCodePath = pathFrom; // Use pathFrom directly
                }
            } else {
                System.err.println("[Main] ERROR: --pathfrom is not a valid directory or .jar file: " + pathFrom);
                System.exit(1);
                return;
            }

            // 1. Create the custom class loader
            BlockingClassLoader customLoader = new BlockingClassLoader(
                    ClassLoader.getSystemClassLoader(),
                    effectiveUserCodePath,
                    pathFromConfig
            );

            // 2. Load and run the user's main class
            System.out.println("\n[Main] --- Executing user code inside sandbox ---");
            Class<?> userAppClass = customLoader.loadClass(mainClassName);

            Method mainMethod = null;
            try {
                // First, look for a standard 'public static void main(String[] args)'
                mainMethod = userAppClass.getMethod("main", String[].class);
            } catch (NoSuchMethodException e) {
                // Not found, will try 'run()' method next.
            }

            if (mainMethod != null) {
                // Found 'main(String[])'. Invoke it.
                System.out.println("[Main] Found 'public static void main(String[] args)' method. Invoking...");
                // The 'main' method is static, so the first argument to invoke is null.
                // Pass an empty String array as the arguments.
                mainMethod.invoke(null, (Object) new String[0]);
            } else {
                // 'main' not found, look for 'public void run()'
                System.out.println("[Main] No 'main(String[])' method found. Looking for 'public void run()'...");
                Method runMethod = null;
                try {
                    runMethod = userAppClass.getMethod("run");
                    // Found 'run()'. Instantiate the class and invoke it.
                    Object userAppInstance = userAppClass.getDeclaredConstructor().newInstance();
                    runMethod.invoke(userAppInstance);
                } catch (NoSuchMethodException runException) {
                    // Neither method was found. This is the error.
                    System.err.println("[Main] ERROR: Main class '" + mainClassName + "' does not have a 'public static void main(String[] args)' or a public 'run()' method.");
                    throw runException; // Re-throw the exception
                }
            }

            System.out.println("[Main] --- User code execution finished ---\n");

        } catch (ClassNotFoundException e) {
            System.err.println("[Main] ERROR: Could not find main class '" + mainClassName + "' in " + sourcePath);
            e.printStackTrace();
            System.exit(1);
        } catch (NoSuchMethodException e) {
            // This will catch the re-thrown exception if neither 'main' nor 'run' is found
            System.err.println("[Main] Execution failed: Could not find suitable entry point.");
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[Main] An error occurred while running the user application.");
            e.printStackTrace();
        }

        System.out.println("[Main] Sandbox execution complete.");
    }

    /**
     * Compiles all .java files from a source directory to an output directory.
     */
    private static boolean compileSourceFiles(Path sourceDir, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[Main] ERROR: No Java compiler found. Please run this with a JDK, not just a JRE.");
            return false;
        }

        try (Stream<Path> stream = Files.walk(sourceDir)) {
            List<String> javaFiles = stream
                    .filter(file -> file.toString().endsWith(".java"))
                    .map(Path::toString)
                    .collect(Collectors.toList());

            if (javaFiles.isEmpty()) {
                System.err.println("[Main] WARNING: No .java files found in " + sourceDir);
                return true; // Not a failure, just nothing to do.
            }

            // Ensure output directory exists
            Files.createDirectories(outputDir);

            String[] compilerArgs = {
                    "-d", outputDir.toString()
            };

            // Combine compiler args and file list
            Stream<String> argsStream = Stream.concat(Stream.of(compilerArgs), javaFiles.stream());

            int compilationResult = compiler.run(null, null, null, argsStream.toArray(String[]::new));
            return (compilationResult == 0);

        } catch (IOException e) {
            System.err.println("[Main] ERROR: Could not read source files.");
            e.printStackTrace();
            return false;
        }
    }


    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            // Handle help argument (with or without --)
            if (arg.equals("help") || arg.equals("--help")) {
                params.put("help", "");
                continue;
            }

            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0];
                // Remove prefix if it exists
                if (key.startsWith("--")) {
                    key = key.substring(2);
                }
                params.put(key, parts[1]);
            }
        }
        return params;
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar cclsandbox.jar [options]");
        System.err.println("Options:");
        System.err.println("  pathfrom=<path>     (Required) Path to source .java directory or a user .jar file.");
        System.err.println("  main-class=<class>  (Required) Full class name to execute (e.g., com.example.UserApp).");
        System.err.println("  pathto=<path>       (Optional) Directory to compile .class files into.");
        System.err.println("                      (Defaults to current working directory: '.')");
        System.err.println("  config=<path>       (Optional) Path to the security config.json file.");
        System.err.println("                      (Defaults to 'config.json')");
    }
}