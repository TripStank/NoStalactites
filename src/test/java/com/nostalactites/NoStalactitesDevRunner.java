package com.nostalactites;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NoStalactitesDevRunner {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(NoStalactitesPlugin.class);
        RuneLite.main(args);
    }
}
