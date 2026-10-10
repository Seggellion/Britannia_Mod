package com.seggellion.britannia_mod.winery;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

public enum GrapeColor implements StringRepresentable {
    BLUE("blue"),
    DARK_GREEN("dark_green"),
    DARK_PURPLE("dark_purple"),
    GREEN("green"),
    LIGHT_GREEN("light_green"),
    PURPLE("purple"),
    RED("red"),
    YELLOW("yellow");

    private final String name;

    GrapeColor(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * The colour a name stands for, accepting either the serialized form ({@code dark_purple}) or the
     * enum constant ({@code DARK_PURPLE}), and returning {@code fallback} for anything else.
     *
     * <p>Lenient by design: this is what the bootstrap parser and the clientbound colour catalogue
     * both read untrusted names through, and a shard that publishes a colour this build has never
     * heard of must leave the grape rendering rather than break the connection.
     */
    public static GrapeColor fromName(String name, GrapeColor fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (GrapeColor color : values()) {
            if (color.name.equals(normalized) || color.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return color;
            }
        }
        return fallback;
    }
}
