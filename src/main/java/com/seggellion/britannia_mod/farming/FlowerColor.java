package com.seggellion.britannia_mod.farming;

/**
 * Compact renderer tint value. The active Farming branch has no Dye Tub source
 * implementation, so this deliberately uses the standard 24-bit RGB tint
 * boundary and can be adapted without changing flower save data.
 */
public record FlowerColor(int tintValue) {
    public static final int MIN_VALUE = 0x000000;
    public static final int MAX_VALUE = 0xFFFFFF;
    public static final int CAPACITY = MAX_VALUE + 1;

    public FlowerColor {
        if (tintValue < MIN_VALUE || tintValue > MAX_VALUE) {
            throw new IllegalArgumentException("Flower tint must be a 24-bit RGB value (0x000000..0xFFFFFF): " + tintValue);
        }
    }

    public static FlowerColor fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Flower tint hex value is required");
        }
        String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!normalized.matches("[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Flower tint must contain exactly six hexadecimal RGB digits: " + hex);
        }
        return new FlowerColor(Integer.parseInt(normalized, 16));
    }

    public int opaqueArgb() {
        return 0xFF000000 | tintValue;
    }

    public String hex() {
        return String.format("#%06X", tintValue);
    }
}
