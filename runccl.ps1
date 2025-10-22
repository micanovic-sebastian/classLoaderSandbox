# PowerShell script to automate the build and run process for the Java Sandbox project.
# This script executes the steps outlined in README.md.

# Stop the script if any command fails.
$ErrorActionPreference = "Stop"

# --- Helper Function ---
# A function to execute a command and print a message. Exits if the command fails.
function Invoke-Step {
    param(
        [string]$StepMessage,
        [string]$Command,
        [string[]]$Arguments
    )
    Write-Host "--- $StepMessage ---" -ForegroundColor Green
    try {
        & $Command $Arguments
        Write-Host "`"$Command`" completed successfully.`n"
    } catch {
        Write-Error "An error occurred during: '$StepMessage'. Halting script."
        # The script will stop here due to $ErrorActionPreference = "Stop"
        exit 1
    }
}

# --- Pre-flight Check ---
# Ensure the script is being run from the correct directory.
if (-not (Test-Path "pom.xml")) {
    Write-Error "This script must be run from the project root directory (where pom.xml is located)."
    exit 1
}

# --- Variables ---
$userCodeDir = "target/test-classes"
$userCodeSource = "src/test/java/UserApp.java"
$jarName = "java-sandbox-1.0.0.jar"
$jarPath = "target/$jarName"

# --- Build and Run Steps ---

# 1. Compile the untrusted UserApp code
New-Item -ItemType Directory -Force -Path $userCodeDir | Out-Null
Invoke-Step "Step 1: Compiling untrusted user code" "javac" "-d", $userCodeDir, $userCodeSource

# 2. Package the main sandbox application using Maven
Invoke-Step "Step 2: Building the main sandbox JAR with Maven" "mvn" "clean", "package"

# 3. Verify that the JAR was created
if (-not (Test-Path $jarPath)) {
    Write-Error "Build failed. The JAR file was not found at '$jarPath'."
    exit 1
}

# 4. Run the final application
Write-Host "--- Step 3: Running the sandbox application ---" -ForegroundColor Green
Write-Host "Executing: java -jar $jarPath $userCodeDir`n"

& java -jar $jarPath "--pathfrom=$userCodeDir" --main-class="UserApp" "--config=config.json"

Write-Host "Script finished successfully." -ForegroundColor Cyan

