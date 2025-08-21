package com.nostalactites;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "No Stalactites",
        description = "Hide cave stalactites and columns.",
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

    private final Set<Integer> hiddenIds = new HashSet<>();

    @Override
    protected void startUp()
    {
        rebuildHiddenIds();
        // Apply to existing scene shortly after startup on the game thread
        clientThread.invoke(this::applyHidingToScene);
        log.info("No Stalactites started");
    }

    @Override
    protected void shutDown()
    {
        hiddenIds.clear();
        // No persistent changes; objects will reappear on next rebuild/scene load.
        log.info("No Stalactites stopped");
    }

    @Provides
    NoStalactitesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(NoStalactitesConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e)
    {
        if (!"nostalactites".equals(e.getGroup()))
        {
            return;
        }
        rebuildHiddenIds();
        clientThread.invoke(this::applyHidingToScene);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject obj = event.getGameObject();
        if (obj == null)
        {
            return;
        }
        if (!hiddenIds.contains(obj.getId()))
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
            scene.removeGameObject(obj);
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

    private void rebuildHiddenIds()
    {
        hiddenIds.clear();
        // Stalactites: 12577, 11187, 11189
        if (config.hideStalactites())
        {
            hiddenIds.add(12577);
            hiddenIds.add(11187);
            hiddenIds.add(11189);
        }
        // Columns: 11184, 11185, 11186
        if (config.hideColumns())
        {
            hiddenIds.add(11184);
            hiddenIds.add(11185);
            hiddenIds.add(11186);
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
}
