package com.nostalactites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("nostalactites")
public interface NoStalactitesConfig extends Config
{
    // No global enable/disable toggle; plugin always applies configured hidden IDs

    @ConfigItem(
        keyName = "showObjectIds",
        name = "Shift Right-Click helper",
        description = "Hold Shift and right-click an object to show 'Add to hide list'. When enabled, also appends object IDs to right-click menus for convenience."
    )
    default boolean showObjectIds()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hiddenObjectIds",
        name = "Hidden object IDs",
        description = "Comma-separated list of object IDs to hide (e.g., 1234,5678). Defaults include stalactites (12577)."
    )
    default String hiddenObjectIds()
    {
        return "12577";
    }
}
