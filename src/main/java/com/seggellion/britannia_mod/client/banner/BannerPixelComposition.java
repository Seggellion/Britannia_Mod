package com.seggellion.britannia_mod.client.banner;

/**
 * Deterministic CPU reference for the standard source-over blend used by the two-file renderer.
 *
 * <p>The GPU renders the complete base first and the resolved-colour-tinted grayscale mask second. This helper
 * makes that pixel contract directly testable without introducing a custom shader.</p>
 */
public final class BannerPixelComposition {
    private BannerPixelComposition() {
    }

    public static int render(int baseArgb, int maskArgb, int dyeRgb, boolean recolourActive) {
        return recolourActive ? recolour(baseArgb, maskArgb, dyeRgb) : baseArgb;
    }

    public static int recolour(int baseArgb, int maskArgb, int dyeRgb) {
        int maskAlpha = channel(maskArgb, 24);
        if (maskAlpha == 0) {
            return baseArgb;
        }
        int red = blend(channel(baseArgb, 16),
                multiply(channel(maskArgb, 16), channel(dyeRgb, 16)), maskAlpha);
        int green = blend(channel(baseArgb, 8),
                multiply(channel(maskArgb, 8), channel(dyeRgb, 8)), maskAlpha);
        int blue = blend(channel(baseArgb, 0),
                multiply(channel(maskArgb, 0), channel(dyeRgb, 0)), maskAlpha);
        return channel(baseArgb, 24) << 24 | red << 16 | green << 8 | blue;
    }

    private static int multiply(int first, int second) {
        return (first * second + 127) / 255;
    }

    private static int blend(int base, int tintedMask, int alpha) {
        return (base * (255 - alpha) + tintedMask * alpha + 127) / 255;
    }

    private static int channel(int value, int shift) {
        return value >>> shift & 0xFF;
    }
}
