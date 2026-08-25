package com.seggellion.britannia_mod.bannerdyeing;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * How much of a banner's square texture its painted artwork actually covers.
 *
 * <p>The placed renderer maps the WHOLE sprite onto the cloth quad, so a banner's visible size
 * is its quad multiplied by that coverage -- which is how two families sharing one quad still
 * render as different banners, and why quad measurements alone mislead about what a player sees.
 */
final class BannerXSmallArtwork {
    private BannerXSmallArtwork() {
    }

    private static Path texture(String banner) {
        return Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/assets/britannia_mod/textures/banner", banner, "base_texture.png");
    }

    /** Opaque-pixel bounds of the artwork, as {@code {left, top, right, bottom}} in pixels. */
    private static int[] alphaBounds(String banner) throws Exception {
        Path path = texture(banner);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("no base texture for " + banner + " at " + path);
        }
        BufferedImage image = ImageIO.read(path.toFile());
        int left = image.getWidth();
        int top = image.getHeight();
        int right = 0;
        int bottom = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
            }
        }
        if (right <= left || bottom <= top) {
            throw new IllegalStateException("banner texture is fully transparent: " + banner);
        }
        return new int[] {left, top, right, bottom, image.getWidth(), image.getHeight()};
    }

    /** Where the painted artwork begins across its texture, as a fraction of the full width. */
    static double leftEdgeFraction(String banner) throws Exception {
        int[] bounds = alphaBounds(banner);
        return bounds[0] / (double) bounds[4];
    }

    /** Where the painted artwork ends across its texture, as a fraction of the full width. */
    static double rightEdgeFraction(String banner) throws Exception {
        int[] bounds = alphaBounds(banner);
        return bounds[2] / (double) bounds[4];
    }

    static double visibleWidthPx(String banner, double clothWidthPx) throws Exception {
        int[] bounds = alphaBounds(banner);
        return clothWidthPx * (bounds[2] - bounds[0]) / (double) bounds[4];
    }

    static double visibleHeightPx(String banner, double clothHeightPx) throws Exception {
        int[] bounds = alphaBounds(banner);
        return clothHeightPx * (bounds[3] - bounds[1]) / (double) bounds[5];
    }
}
