# No Stalactites!
Hide cave stalactites and columns with simple toggles.

# Features
- Hide stalactites and cave columns.
- Simple on/off toggles. No user-provided IDs.

# Usage
- Open the plugin’s configuration and enable/disable the categories you want hidden.
  - Hide stalactites (IDs: 12577, 11187, 11189) — default: on
  - Hide cave columns (IDs: 11184, 11185, 11186) — default: off

# Configuration
- Hide stalactites:
  - Removes stalactite cave objects.
- Hide cave columns:
  - Removes cave column objects.

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
