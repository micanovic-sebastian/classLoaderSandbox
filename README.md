# Custom ClassLoader Sandbox Project

This project uses a custom Java ClassLoader to prevent specified classes from being loaded, effectively creating a simple sandbox.

## How to Run (PowerShell for Windows)

This project requires a two-step compilation to ensure the `UserApp` is not on the main classpath, which forces it to be loaded by our `BlockingClassLoader`.

**1. Create Build Directories**

Open PowerShell in the project's root directory and run:

```powershell
# Create a directory for the main app
New-Item -ItemType Directory -Force -Path "bin/main"

# Create a separate directory for the sandboxed app
New-Item -ItemType Directory -Force -Path "bin/user"