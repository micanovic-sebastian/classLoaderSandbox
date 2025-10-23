# Baut und startet das Java Sandbox-Projekt

# Stoppt das Skript bei jedem Fehler
$ErrorActionPreference = "Stop"

# Helper-Funktion um einen Befehl auszuführen und zu loggen
function Invoke-Step {
    param(
        [string]$StepMessage,
        [string]$Command,
        [string[]]$Arguments
    )
    try {
        & $Command $Arguments
        Write-Host "`"$Command`" completed successfully.`n"
    } catch {
        Write-Error "An error occurred during: '$StepMessage'. Halting script."
        # Skript stoppt hier dank $ErrorActionPreference = "Stop"
        exit 1
    }
}

# Sicherstellen dass wir im Projekt-Root sind (wo die pom.xml liegt)
if (-not (Test-Path "pom.xml")) {
    Write-Error "This script must be run from the project root directory (where pom.xml is located)."
    exit 1
}

$userCodeDir = "target/test-classes"
$userCodeSource = "src/test/java/TestApp.java"
# Der Name der Shaded-JAR (aus pom.xml <finalName>)
$jarName = "cclsandbox.jar"
$jarPath = "target/$jarName"


# 1. Kompiliere den unsicheren Benutzercode (TestApp)
New-Item -ItemType Directory -Force -Path $userCodeDir | Out-Null
Invoke-Step "Step 1: Compiling untrusted user code" "javac" "-d", $userCodeDir, $userCodeSource

# 2. Baue die Sandbox-App mit Maven
Invoke-Step "Step 2: Building the main sandbox JAR with Maven" "mvn" "clean", "package"

# 3. Prüfen ob die JAR-Datei existiert
if (-not (Test-Path $jarPath)) {
    Write-Error "Build failed. The JAR file was not found at '$jarPath'."
    exit 1
}

# 4. Sandbox-Anwendung starten
Write-Host "--- Step 3: Running the sandbox application ---" -ForegroundColor Green
Write-Host "Executing: java -jar $jarPath pathfrom=$userCodeDir main-class=TestApp config=config.json`n"

# Starte mit Standard-Logdatei (cclsandbox.log)
java -jar $jarPath "pathfrom=$userCodeDir" --main-class=TestApp --config=config.json

# Beispiel mit eigenem Log-Pfad:
# java -jar $jarPath "pathfrom=$userCodeDir" main-class=TestApp config=config.json log=my-custom-log.txt
#
# Beispiel um File-Logging zu deaktivieren:
# java -jar $jarPath "pathfrom=$userCodeDir" main-class=TestApp config=config.json log=none

