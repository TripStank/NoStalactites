# No Stalactites!

A RuneLite plugin that hides cave stalactites and columns, with an option to replace columns with a stalagmite floor pattern for better visibility.

## Features
- Hide stalactites and cave columns
- Simple on/off toggles - no user-provided IDs needed
- Optional: Replace hidden columns with decorative stalagmite models

## Usage
1. Open the RuneLite configuration panel
2. Navigate to the "No Stalactites" plugin section
3. Configure the following options:
   - **Hide Stalactites** (default: on) - Hides stalactite objects (IDs: 12577, 11187, 11189)
   - **Hide Cave Columns** (default: off) - Hides column objects (IDs: 11184, 11185, 11186)
   - **Replace with Rocks** (default: off) - Replaces hidden columns with decorative rock models

## Installation
### From Plugin Hub (recommended)
1. Open the RuneLite client
2. Go to Configuration > Plugin Hub
3. Search for "No Stalactites"
4. Click "Install"

### From Source
1. Clone the repository
2. Build the plugin:
   - Thin JAR (for Plugin Hub):
     ```
     ./gradlew clean jar
     ```
     Output: `build/libs/NoStalactites-1.2.1.jar`
   - Development JAR (for testing):
     ```
     ./gradlew shadowJar
     java -ea -jar build/libs/NoStalactites-1.2.1-dev.jar --developer-mode
     ```

## Known Issues
- Hidden objects may reappear after changing areas or relogging
- Some object types cannot be removed due to RuneScape API limitations
- The plugin only affects visual elements and does not modify game mechanics

## Credits
- Original concept by the RuneLite community
- 3D model replacement functionality inspired by [Xyriella's Immersive Ground Markers](https://github.com/Xyriella/Immersive-Ground-Markers)

## License
This project is licensed under the BSD 2-Clause License - see the [LICENSE](LICENSE) file for details.


## Generative AI Usage Notice
This plugin was created with the use of AI code editing tools. 
The README.md file was generated using AI.