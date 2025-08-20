package com.example;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ExamplePlugin.class);
        ExternalPluginManager.loadBuiltin(com.nostalactites.NoStalactitesPlugin.class);
        RuneLite.main(args);
    }
}