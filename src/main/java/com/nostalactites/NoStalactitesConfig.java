package com.nostalactites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("nostalactites")
public interface NoStalactitesConfig extends Config
{
    @ConfigItem(
        keyName = "hideStalactites",
        name = "Hide stalactites",
        description = "Hide stalactite cave objects (IDs: 12577, 11187, 11189)."
    )
    default boolean hideStalactites()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hideColumns",
        name = "Hide cave columns",
        description = "Hide cave column objects (IDs: 11184, 11185, 11186)."
    )
    default boolean hideColumns()
    {
        return false;
    }
}
