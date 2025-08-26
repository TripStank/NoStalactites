# No Stalactites Plugin — Development Plan

## Overview
The **No Stalactites** plugin is built using the RuneLite plugin template from GitHub. Its purpose is to allow players to hide specific objects (stalactites, trees, rock columns, etc.) by ID, while providing an option to display object IDs in the right-click context menu for easier customization.

## Features and Implementation
- **Object ID Context Menu Toggle:** A configuration toggle (`showObjectIds`) will add object IDs to the right-click context menu of examinable objects. Example:  
  `Examine Tree (ID: 1276)`

- **Hidden Object ID List:** A configuration field (`hiddenObjectIds`) will store a comma-separated list of object IDs. Any objects matching the IDs in this list will not render in RuneLite.

- **Rendering Control:** The plugin will intercept object spawn and render events (`GameObjectSpawned`, `WallObjectSpawned`, etc.) and suppress rendering for objects whose IDs match the hidden list. This will only affect visuals, leaving interactions and collisions intact.

## Development Tasks
1. **Setup:**  
   - Fork RuneLite plugin template.  
   - Update `plugin.properties` with:  
     - `plugin.id=no-stalactites`  
     - `plugin.name=No Stalactites`  
     - `plugin.description=Hides stalactites and other unwanted objects by ID.`  

2. **Plugin + Config Classes:**  
   - `NoStalactitesPlugin` as the entry point.  
   - `NoStalactitesConfig` with:  
     - `boolean showObjectIds`  
     - `String hiddenObjectIds`  

3. **Menu Entry Hook:**  
   - Use `MenuEntryAdded` to append IDs to context menus when `showObjectIds` is enabled.  

4. **Rendering Hook:**  
   - Listen to object spawn/render events.  
   - Skip rendering if object ID is in `hiddenObjectIds`.  

5. **Testing:**  
   - Verify IDs appear correctly in menus.  
   - Confirm objects disappear when IDs are added.  
   - Ensure unaffected objects remain visible.  
   - Validate changes apply at runtime without client restart.  
