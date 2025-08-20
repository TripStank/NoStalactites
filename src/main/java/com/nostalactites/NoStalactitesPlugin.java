package com.nostalactites;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuEntry;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "No Stalactites",
        description = "Hide selected scene objects by ID and optionally show object IDs in right-click menus",
        tags = {"object", "hide", "ids"}
)
public class NoStalactitesPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private NoStalactitesConfig config;

    @Inject
    private ConfigManager configManager;

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
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!config.showObjectIds())
        {
            return;
        }

        final int typeId = event.getType();
        final MenuAction action = MenuAction.of(typeId);
        if (action == MenuAction.EXAMINE_OBJECT ||
            action == MenuAction.GAME_OBJECT_FIRST_OPTION ||
            action == MenuAction.GAME_OBJECT_SECOND_OPTION ||
            action == MenuAction.GAME_OBJECT_THIRD_OPTION ||
            action == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
            action == MenuAction.GAME_OBJECT_FIFTH_OPTION)
        {
            final int objectId = event.getIdentifier();
            // Append id to the target preserving any color tags
            final String target = event.getTarget();
            event.getMenuEntry().setTarget(target + " <col=7f7f7f>(id: " + objectId + ")</col>");
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        // Only show helper when Shift is held and the setting is enabled
        if (!config.showObjectIds() || !client.isKeyPressed(KeyCode.KC_SHIFT))
        {
            return;
        }

        final MenuEntry[] entries = event.getMenuEntries();
        if (entries == null || entries.length == 0)
        {
            return;
        }

        // Find an object-related menu entry to extract the object id
        Integer objectId = null;
        String target = null;
        for (int i = entries.length - 1; i >= 0; i--)
        {
            final MenuAction action = entries[i].getType();
            if (action == MenuAction.EXAMINE_OBJECT ||
                action == MenuAction.GAME_OBJECT_FIRST_OPTION ||
                action == MenuAction.GAME_OBJECT_SECOND_OPTION ||
                action == MenuAction.GAME_OBJECT_THIRD_OPTION ||
                action == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
                action == MenuAction.GAME_OBJECT_FIFTH_OPTION)
            {
                objectId = entries[i].getIdentifier();
                target = entries[i].getTarget();
                break;
            }
        }

        if (objectId == null)
        {
            return;
        }

        final int idToAdd = objectId;
        final String tgt = target == null ? "" : target;

        // Inject our custom menu entry at the top via client API
        client.createMenuEntry(0)
            .setOption("Add to hide list")
            .setTarget(tgt)
            .setType(MenuAction.RUNELITE)
            .onClick(me -> {
                // Append to config set
                String raw = config.hiddenObjectIds();
                Set<Integer> ids = new HashSet<>();
                if (raw != null && !raw.trim().isEmpty())
                {
                    ids.addAll(Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> {
                            try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; }
                        })
                        .filter(v -> v != null)
                        .collect(Collectors.toSet()));
                }
                ids.add(idToAdd);

                String updated = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

                configManager.setConfiguration("nostalactites", "hiddenObjectIds", updated);

                // Rebuild and reapply hiding immediately
                rebuildHiddenIds();
                clientThread.invoke(this::applyHidingToScene);
            });
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
        final String raw = config.hiddenObjectIds();
        if (raw == null || raw.trim().isEmpty())
        {
            return;
        }
        Set<Integer> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; }
                })
                .filter(v -> v != null)
                .collect(Collectors.toSet());
        hiddenIds.addAll(parsed);
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
