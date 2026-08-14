package com.seggellion.britannia_mod.service;

/**
 * Vendor/Trader Milestone 5 transport convention: an economic NPC type key
 * travels the existing spawn-post pipeline (block entity NBT, pending outbox,
 * receipts, collision repair) inside the same opaque type-key string used for
 * service keys, marked with the {@code economic:} prefix. The colon is illegal
 * in real type keys on both sides, so the marker can never collide with a
 * service key; it is decoded exactly once, when the wire request chooses which
 * JSON field to emit. This keeps the durable outbox schema unchanged.
 */
public final class EconomicNpcTypeKeys {
    public static final String PREFIX = "economic:";

    private EconomicNpcTypeKeys() {
    }

    public static boolean isEconomic(String storedKey) {
        return storedKey != null && storedKey.startsWith(PREFIX) && storedKey.length() > PREFIX.length();
    }

    /** The raw Rails economic type key, e.g. {@code baker_vendor}. */
    public static String strip(String storedKey) {
        return isEconomic(storedKey) ? storedKey.substring(PREFIX.length()) : storedKey;
    }

    /** The pipeline form, e.g. {@code economic:baker_vendor}. */
    public static String prefixed(String economicTypeKey) {
        return PREFIX + economicTypeKey;
    }
}
