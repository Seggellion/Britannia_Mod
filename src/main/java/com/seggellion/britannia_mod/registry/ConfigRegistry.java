// ConfigRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.config.ModConfig;

public class ConfigRegistry {

    // Register configurations by loading them
    public static void register() {
        // Load the configuration
        ModConfig.loadConfig();
    }
}
