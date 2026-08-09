package com.seggellion.britannia_mod.dye.colour;

/** Canonical eight-bit sRGB channels. */
public record SrgbColour(int red, int green, int blue) {
    public SrgbColour {
        requireByte(red, "red");
        requireByte(green, "green");
        requireByte(blue, "blue");
    }

    private static void requireByte(int value, String channel) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(channel + " channel must be between 0 and 255");
        }
    }
}
