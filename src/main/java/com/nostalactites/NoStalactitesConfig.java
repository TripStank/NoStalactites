package com.nostalactites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration interface for the No Stalactites plugin.
 * <p>
 * This interface defines all the configuration options available for the plugin,
 * including toggles for hiding different types of cave formations and debug features.
 */
@ConfigGroup("nostalactites")
public interface NoStalactitesConfig extends Config
{
    /**
     * Determines whether to hide stalactite objects in caves.
     *
     * @return true if stalactites should be hidden, false otherwise
     */
    @ConfigItem(
        keyName = "hideStalactites",
        name = "Hide stalactites",
        description = "Hide hanging stalactites from cave ceilings"
    )
    default boolean hideStalactites()
    {
        return true;
    }

    /**
     * Determines whether to hide column objects in caves.
     *
     * @return true if columns should be hidden, false otherwise
     */
    @ConfigItem(
        keyName = "hideColumns",
        name = "Hide columns",
        description = "Hide cave support columns"
    )
    default boolean hideColumns()
    {
        return true;
    }
    
    /**
     * Determines whether to replace hidden columns with decorative rock formations.
     *
     * @return true if columns should be replaced with rocks, false to simply hide them
     */
    @ConfigItem(
        keyName = "replaceWithRocks",
        name = "Replace with rocks",
        description = "Replace hidden columns with rock formations (Credit: u/BioMasterZap)"
    )
    default boolean replaceWithRocks()
    {
        return true;
    }
    
    /**
     * The model ID used for rock formations when replacing columns.
     * This is the base model ID for the decorative rock formation.
     */
    int ROCK_FORMATION_ID = 1784;
}
