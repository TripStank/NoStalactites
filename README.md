# No Stalactites!
Get rid of those pesky stalactites and other scenery objects by ID#.

# Features
- Hide scenery objects by ID.
- Optional Shift+Right-Click helper to show IDs in menus and quickly add to the hide list.

# Usage
- Hold Shift and right-click an object, choose "Add to hide list" to append its ID.
- Or open the plugin config and set `Hidden object IDs` to a comma-separated list (e.g., `12577,1234`).
- Default list includes stalactites `12577`.

# Configuration
- "Shift Right-Click helper":
  - When enabled, appends object IDs to right-click menu targets and adds the "Add to hide list" option while holding Shift.
- "Hidden object IDs":
  - Comma-separated list of object IDs to remove from the scene.

# Installation
- From Plugin Hub (recommended):
  - Search for "No Stalactites" and install.
- From source (development):
  - Thin jar (Plugin Hub artifact):
    - `./gradlew.bat clean jar`
    - Output: `build/libs/NoStalactites-1.0.jar`
  - Dev (runnable) fat jar for local testing:
    - `./gradlew.bat shadowJar`
    - Run: `java -ea -jar build/libs/NoStalactites-1.0-dev.jar --developer-mode`

# Limitations
- Hiding is destructive/semi‑permanent within the current scene due to API limits.
- Hidden objects usually reappear after a scene/region reload (e.g., changing areas or re-logging).
- Some object types (e.g., certain wall/decorative objects) lack public removal APIs; matches are logged where applicable.

# License
BSD 2‑Clause. See `LICENSE`.