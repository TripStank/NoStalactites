package com.nostalactites;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.GameState;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A RuneLite plugin that hides cave stalactites and columns, with an option to replace
 * columns with a stalagmite floor pattern for better visibility.
 * 
 * <p>Features:
 * <ul>
 *   <li>Hides various cave stalactites and column objects</li>
 *   <li>Optionally replaces hidden columns with decorative stalagmite models</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
    name = "No Stalactites",
    description = "Hide cave stalactites and columns. Optionally swap columns with a stalagmite floor pattern.",
    tags = {"object", "hide", "cave"}
)
public class NoStalactitesPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private NoStalactitesConfig config;

    /**
     * Set of object IDs that should be hidden based on configuration.
     */
    private final Set<Integer> hiddenIds = new HashSet<>();

    /**
     * Tracks the southwest corner (anchor point) of each detected column footprint.
     * Used for placing replacement stalagmite models.
     */
    private final Set<WorldPoint> columnAnchors = new HashSet<>();

    /**
     * Maps anchor points to their corresponding RuneLiteObject models for stalagmites.
     * This allows us to manage the lifecycle of dynamically created objects.
     */
    private final Map<WorldPoint, List<RuneLiteObject>> stalagmiteObjects = new HashMap<>();
    
    /**
     * Cache for loaded models to avoid reloading them multiple times.
     */
    private final Map<Integer, Model> modelCache = new HashMap<>();
    
    /**
     * Random number generator for varying model orientations.
     */
    // Rotation values for rock placement (in 1/2048 units, 512 = 90 degrees)
    private static final int ROTATION_90_DEG = 512;
    private static final int[] ROTATIONS = {0, ROTATION_90_DEG, ROTATION_90_DEG * 2, ROTATION_90_DEG * 3};
    private int rotationIndex = 0; // Track rotation for each object

    Set<WorldPoint> getColumnAnchors()
    {
        return columnAnchors;
    }

    /**
     * Called when the plugin is started.
     * Initializes the plugin state and applies initial object hiding.
     */
    @Override
    protected void startUp()
    {
        rebuildHiddenIds();
        columnAnchors.clear();
        
        // Apply to existing scene shortly after startup on the game thread
        clientThread.invoke(() -> {
            applyHidingToScene();
            updateStalagmiteObjects();
        });
        
        log.info("No Stalactites started");
    }

    /**
     * Called when the plugin is stopped.
     * Cleans up any resources and restores the game state.
     */
    @Override
    protected void shutDown()
    {
        log.info("No Stalactites stopping...");
        
        // Clear all custom objects and caches on the client thread
        clientThread.invoke(() -> {
            // First despawn all stalagmite objects
            for (List<RuneLiteObject> objects : stalagmiteObjects.values()) {
                for (RuneLiteObject obj : objects) {
                    try {
                        obj.setActive(false);
                    } catch (Exception e) {
                        log.debug("Error deactivating object: {}", e.getMessage());
                    }
                }
            }
            stalagmiteObjects.clear();
            
            // Clear model cache
            modelCache.clear();
            
            // Clear tracking collections
            hiddenIds.clear();
            columnAnchors.clear();
            
            // Reset rotation index
            rotationIndex = 0;
            
            // Force scene reload to restore hidden objects
            if (client.getGameState() == GameState.LOGGED_IN) {
                client.setGameState(GameState.LOADING);
            }
        });
        
        log.info("No Stalactites stopped");
    }

    /**
     * Provides the plugin configuration.
     *
     * @param configManager the RuneLite config manager
     * @return the plugin configuration
     */
    @Provides
    NoStalactitesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(NoStalactitesConfig.class);
    }

    /**
     * Handles configuration changes.
     * Updates the plugin state when configuration values are modified.
     *
     * @param event the configuration change event
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("nostalactites"))
        {
            return;
        }

        // Rebuild the set of hidden IDs based on current config
        rebuildHiddenIds();
        
        // Clear existing stalagmite objects when config changes
        clearStalagmiteObjects();
        
        // Force a scene reload to apply changes
        clientThread.invokeLater(() -> {
            try {
                if (client.getGameState() == GameState.LOGGED_IN) {
                    client.setGameState(GameState.LOADING);
                } else {
                    applyHidingToScene();
                    if (config.hideColumns() && config.replaceWithRocks()) {
                        updateStalagmiteObjects();
                    }
                }
            } catch (Exception e) {
                log.error("Error updating objects: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Handles the spawning of game objects.
     * Hides objects that match the configured hidden IDs and manages column replacement.
     *
     * @param event the game object spawned event
     */
    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject obj = event.getGameObject();
        if (obj == null)
        {
            return;
        }
        
        final int id = obj.getId();
        if (!hiddenIds.contains(id))
        {
            return;
        }

        // Remove the object from the scene on the game thread
        clientThread.invoke(() -> {
            WorldView wv = client.getTopLevelWorldView();
            Scene scene = wv != null ? wv.getScene() : null;
            if (scene == null)
            {
                return;
            }
            
            // Record column footprint if replacing with rocks is enabled
            if (isColumnId(id) && config.hideColumns() && config.replaceWithRocks())
            {
                recordColumnFootprint(obj);
            }
            
            // Remove the object from the scene
            scene.removeGameObject(obj);
            
            // Update stalagmite objects if this was a column
            if (isColumnId(id) && config.hideColumns() && config.replaceWithRocks())
            {
                updateStalagmiteObjects();
            }
        });
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        if (event.getGroundObject() == null)
        {
            return;
        }
        final int id = event.getGroundObject().getId();
        if (!hiddenIds.contains(id))
        {
            return;
        }

        clientThread.invoke(() -> {
            Tile tile = event.getTile();
            if (tile != null)
            {
                tile.setGroundObject(null);
            }
        });
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        if (event.getWallObject() == null)
        {
            return;
        }
        final int id = event.getWallObject().getId();
        if (hiddenIds.contains(id))
        {
            // No public API to remove wall objects; log a debug match for now.
            log.debug("Matched hidden wall object id {} at {} - no public removal API", id, event.getTile().getWorldLocation());
        }
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        if (event.getDecorativeObject() == null)
        {
            return;
        }
        final int id = event.getDecorativeObject().getId();
        if (hiddenIds.contains(id))
        {
            // No public API to remove decorative objects; log a debug match for now.
            log.debug("Matched hidden decorative object id {} at {} - no public removal API", id, event.getTile().getWorldLocation());
        }
    }

    /**
     * Rebuilds the set of hidden object IDs based on the current configuration.
     * This is called when the configuration changes to update which objects should be hidden.
     */
    private void rebuildHiddenIds()
    {
        hiddenIds.clear();
        
        // Add stalactite object IDs if enabled
        if (config.hideStalactites())
        {
            // Stalactite objects
            hiddenIds.add(12577); // Large stalactite
            hiddenIds.add(11187); // Medium stalactite
            hiddenIds.add(11189); // Small stalactite
        }
        
        // Add column object IDs if enabled
        if (config.hideColumns())
        {
            // Column objects (different rotations/states)
            hiddenIds.add(11184); // Column 1
            hiddenIds.add(11185); // Column 2
            hiddenIds.add(11186); // Column 3
        }
    }


    private void applyHidingToScene()
    {
        WorldView wv = client.getTopLevelWorldView();
        Scene scene = wv != null ? wv.getScene() : null;
        if (scene == null)
        {
            return;
        }
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null)
        {
            return;
        }

        for (int z = 0; z < tiles.length; z++)
        {
            Tile[][] plane = tiles[z];
            if (plane == null)
            {
                continue;
            }
            for (int x = 0; x < plane.length; x++)
            {
                Tile[] col = plane[x];
                if (col == null)
                {
                    continue;
                }
                for (int y = 0; y < col.length; y++)
                {
                    Tile t = col[y];
                    if (t == null)
                    {
                        continue;
                    }

                    // Remove matching game objects
                    GameObject[] objs = t.getGameObjects();
                    if (objs != null)
                    {
                        for (GameObject go : objs)
                        {
                            if (go != null && hiddenIds.contains(go.getId()))
                            {
                                if (isColumnId(go.getId()) && config.hideColumns() && config.replaceWithRocks())
                                {
                                    recordColumnFootprint(go);
                                }
                                scene.removeGameObject(go);
                            }
                        }
                    }

                    // Remove matching ground object
                    if (t.getGroundObject() != null && hiddenIds.contains(t.getGroundObject().getId()))
                    {
                        t.setGroundObject(null);
                    }
                }
            }
        }
    }

    /**
     * Checks if the given object ID corresponds to a column.
     *
     * @param id the object ID to check
     * @return true if the ID is a column, false otherwise
     */
    private static boolean isColumnId(int id)
    {
        // Standard columns
        if (id == 11184 || id == 11185 || id == 11186) {
            return true;
        }
        
        // Additional column variants if they exist
        return id == 25080 || id == 25081 || id == 25082;
    }

    private void recordColumnFootprint(GameObject go)
    {
        try
        {
            WorldPoint wp = WorldPoint.fromLocalInstance(client, go.getLocalLocation());
            if (wp == null)
            {
                return;
            }

            // Determine object size; default to 2x2 per user spec
            int sizeX = 2, sizeY = 2;
            ObjectComposition oc = client.getObjectDefinition(go.getId());
            if (oc != null)
            {
                sizeX = oc.getSizeX();
                sizeY = oc.getSizeY();
                int rot = go.getOrientation() & 3;
                if ((rot & 1) == 1)
                {
                    int tmp = sizeX;
                    sizeX = sizeY;
                    sizeY = tmp;
                }
            }

            // Shift anchor to SW corner of the object's footprint to avoid NE offset
            int anchorX = wp.getX() - (sizeX - 1);
            int anchorY = wp.getY() - (sizeY - 1);
            WorldPoint sw = new WorldPoint(anchorX, anchorY, wp.getPlane());

            // Use SW tile of the footprint as anchor. If not 2x2, still anchor at SW and overlay 2x2.
            columnAnchors.add(sw);
        }
        catch (Exception ex)
        {
            log.debug("Failed recording column footprint: {}", ex.getMessage());
        }
    }

    private void updateStalagmiteObjects()
    {
        if (!config.replaceWithRocks() || !config.hideColumns()) {
            return;
        }
        
        // Get the model ID from config
        int modelId = NoStalactitesConfig.ROCK_FORMATION_ID;
        
        // Clear existing objects first
        clearStalagmiteObjects();
        
        // Load the model if not already cached
        Model model = modelCache.get(modelId);
        if (model == null) {
            model = client.loadModel(modelId);
            if (model != null) {
                modelCache.put(modelId, model);
            } else {
                log.warn("Failed to load model: {}", modelId);
                return;
            }
        }
        
        // Spawn models at each anchor
        for (WorldPoint anchor : columnAnchors) {
            spawnAtAnchor(anchor, modelId);
        }
    }
    
    private void clearStalagmiteObjects()
    {
        // This method is now a wrapper that ensures we're on the client thread
        if (client.isClientThread()) {
            clearStalagmiteObjectsInternal();
        } else {
            clientThread.invoke(this::clearStalagmiteObjectsInternal);
        }
    }
    
    private void clearStalagmiteObjectsInternal()
    {
        try {
            for (List<RuneLiteObject> objects : stalagmiteObjects.values()) {
                for (RuneLiteObject obj : objects) {
                    try {
                        if (obj != null) {
                            obj.setActive(false);
                        }
                    } catch (Exception e) {
                        log.debug("Error deactivating object: {}", e.getMessage());
                    }
                }
            }
            stalagmiteObjects.clear();
            modelCache.clear();
        } catch (Exception e) {
            log.error("Error clearing stalagmite objects: {}", e.getMessage(), e);
        }
    }

    private void spawnAtAnchor(WorldPoint anchor, int modelId)
    {
        try
        {
            List<RuneLiteObject> list = new ArrayList<>();
            
            // Place one model per tile center in the 2x2 footprint starting at SW anchor
            for (int dx = 0; dx < 2; dx++)
            {
                for (int dy = 0; dy < 2; dy++)
                {
                    WorldPoint wp = new WorldPoint(anchor.getX() + dx, anchor.getY() + dy, anchor.getPlane());
                    var lp = net.runelite.api.coords.LocalPoint.fromWorld(client, wp);
                    if (lp == null)
                    {
                        continue;
                    }

                    // Create and configure the RuneLiteObject
                    RuneLiteObject obj = client.createRuneLiteObject();
                    Model model = modelCache.get(modelId);
                    if (model == null) {
                        model = client.loadModel(modelId);
                        if (model == null) {
                            log.warn("Failed to load model: {}", modelId);
                            continue;
                        }
                        modelCache.put(modelId, model);
                    }
                    
                    // Calculate position using world coordinates
                    LocalPoint localPoint = LocalPoint.fromWorld(client, wp.getX(), wp.getY());
                    if (localPoint == null) continue;
                    
                    // Set up the object with fixed rotation
                    obj.setModel(model);
                    obj.setActive(true);
                    
                    // Position the object at the tile center
                    int plane = wp.getPlane();
                    
                    // Use a different rotation for each object (0°, 90°, 180°, 270°)
                    int rotation = ROTATIONS[rotationIndex % ROTATIONS.length];
                    rotationIndex++;
                    
                    // Apply position and rotation
                    obj.setLocation(localPoint, -plane * 128);
                    obj.setOrientation(rotation);
                    
                    list.add(obj);
                }
            }
            
            // Store the created objects
            if (!list.isEmpty()) {
                stalagmiteObjects.put(anchor, list);
            }
        }
        catch (Exception ex)
        {
            log.debug("Failed spawning stalagmite models: {}", ex.getMessage());
        }
    }
}

