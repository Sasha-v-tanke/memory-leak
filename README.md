# Memory Leak - RTS Game

## Project Structure
- **core**: Shared client logic (LibGDX)
- **server**: Ktor Server application
- **shared**: Common data structures (Packets)
- **lwjgl3**: Desktop Launcher

## Prerequisites
1.  **Java Development Kit (JDK) 11 or higher**.
2.  **IntelliJ IDEA** (Recommended) OR **Android Studio** OR **Gradle** installed globally.

## How to Run

### Using Android Studio / IntelliJ IDEA
1.  Open **Android Studio** or **IntelliJ IDEA**.
2.  Select **Open** and choose the `kotlinlab` folder.
3.  Wait for Gradle Sync to finish.
4.  **To run the Server**:
    - Navigate to `server/src/main/kotlin/com/memoryleak/server/Application.kt`.
    - Click the Green Play button next to the `main` function.
  ## 📖 Gameplay

### Controls
- **Click** on an entity to select it
- **Click** on ground to move selected unit
- **B** key to build:
  - Select your BASE → Press **B** → Build FACTORY (costs 100 Memory)
  - Select FACTORY → Press **B** → Build UNIT (costs 50 CPU)
- **WASD** or **Arrow Keys** to move the camera

### Objective
- Capture **Resource Nodes** (yellow diamonds) with your units
- Collect **Memory** and **CPU** to build an army
- Destroy the enemy **BASE** to win!

### UI Features
- **Entity Labels**: All objects show their type (BASE, FACTORY, UNIT, etc.)
- **Selection Panel**: Right side shows detailed info about selected entity
- **HP Bars**: Visual health indicators above all units and buildings
- **Combat Lasers**: Red beams show active attacks
- **Resource Display**: Top-left shows your current Memory and CPU
5.  **To run the Client (Desktop version)**:
    - Navigate to `lwjgl3/src/main/kotlin/com/memoryleak/desktop/DesktopLauncher.kt`.
    - Click the Green Play button next to the `main` function.
    - **MacOS Users**: If running from IDEA/Android Studio using the Green Play button, you MUST add `-XstartOnFirstThread` to the VM Options in the Run Configuration.
    - *Alternatively*, run via Gradle task `:lwjgl3:run` which is configured to do this automatically.

### Command Line / Gradle Tab
1.  **Run Server**:
    ```bash
    ./gradlew :server:run
    ```
2.  **Run Client**:
    ```bash
    ./gradlew :lwjgl3:run
    ```

### Troubleshooting
- **"zsh: no such file or directory: ./gradlew"**: Run `gradle wrapper` first.
- **Exit code 1 on macOS**: LibGDX requires `-XstartOnFirstThread`.
    - If running via `./gradlew :lwjgl3:run`, I have updated the build script to handle this automatically.
    - If running via the **Green Play Button** in IntelliJ/Android Studio:
        1.  Edit Configurations...
        2.  Select `DesktopLauncherKt`.
        3.  Click "Modify options" -> "Add VM options".
        4.  Enter: `-XstartOnFirstThread`
        5.  Apply and Run.
