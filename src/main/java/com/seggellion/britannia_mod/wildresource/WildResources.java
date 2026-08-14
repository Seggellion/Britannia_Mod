package com.seggellion.britannia_mod.wildresource;

/** Owns the configured entry registry consumed by the server scheduler. */
public final class WildResources {
    private static final WildResourceRegistry REGISTRY = new WildResourceRegistry();

    private WildResources() {
    }

    public static WildResourceRegistry registry() {
        return REGISTRY;
    }
}
