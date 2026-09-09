package com.seggellion.britannia_mod.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft's own GUI scale arithmetic, mirrored so the acceptance matrix can be <i>derived</i>
 * rather than assumed.
 *
 * <p>Rowan farming questline M8. The playbook's visual acceptance list names "GUI scales 1, 2, 3
 * and 4" and "a 640x360 window" as if every pairing were reachable. Several are not, and the ones
 * that are do not produce the numbers a reader would guess. This class is the two vanilla methods
 * that decide, copied verbatim from Minecraft 1.21.1 so the derivation can be checked against the
 * source rather than believed:
 *
 * <pre>{@code
 * // com.mojang.blaze3d.platform.Window#calculateScale
 * public int calculateScale(int guiScale, boolean forceUnicode) {
 *     int i = 1;
 *     while (i != guiScale && i < this.framebufferWidth && i < this.framebufferHeight
 *            && this.framebufferWidth / (i + 1) >= 320
 *            && this.framebufferHeight / (i + 1) >= 240) {
 *         i++;
 *     }
 *     if (forceUnicode && i % 2 != 0) { i++; }
 *     return i;
 * }
 *
 * // com.mojang.blaze3d.platform.Window#setGuiScale  (the rounding is CEILING, not floor)
 * int i = (int)((double)this.framebufferWidth / guiScale);
 * this.guiScaledWidth = (double)this.framebufferWidth / guiScale > (double)i ? i + 1 : i;
 * }</pre>
 *
 * <h2>The two findings this produces</h2>
 * <ol>
 *   <li>The loop refuses to scale up past the point where the scaled size would fall below
 *       320x240, so <b>a 1024x768 window at GUI scale 4 does not exist</b>: it clamps to scale 3
 *       and is 342x256 units.</li>
 *   <li>The {@code forceUnicode} bump happens <b>after</b> that check, so the vanilla <b>Force
 *       Unicode Font</b> option walks straight through the floor. The same 1024x768 window with it
 *       on really is scale 4 and really is 256x192 units -- and 320x180, 342x192 and, at the
 *       smallest window, 160x120 all become reachable. Every one of those is below the 320x240
 *       floor the rest of the game assumes.</li>
 * </ol>
 * The second finding is the reason the acceptance matrix has rows nobody would have written by
 * hand, and the reason {@code screenWidth - 343} is a live defect rather than a theoretical one.
 */
public final class GuiScaleRule {

    private GuiScaleRule() {
    }

    public static final int BASE_WIDTH = 320;
    public static final int BASE_HEIGHT = 240;

    /** Requested GUI scale meaning "auto" in the vanilla options screen. */
    public static final int AUTO = 0;

    /**
     * One row of the acceptance matrix: a real window and setting, and the scaled units a
     * {@code Screen} sees as a result.
     */
    public record Row(String label, int windowWidth, int windowHeight, int requestedScale,
                      boolean forceUnicode, int effectiveScale, int scaledWidth, int scaledHeight) {

        /** True when the requested scale is not the one the player gets. */
        public boolean clamped() {
            return requestedScale != AUTO && requestedScale != effectiveScale;
        }

        /** The wrap width the legacy {@code DialogueLayout} would compute here. */
        public int legacyWrapWidth() {
            return scaledWidth - 343;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s: %dx%d window, requested scale %s%s -> effective %d -> %dx%d units",
                    label, windowWidth, windowHeight,
                    requestedScale == AUTO ? "auto" : String.valueOf(requestedScale),
                    forceUnicode ? " with Force Unicode Font" : "",
                    effectiveScale, scaledWidth, scaledHeight);
        }
    }

    /** Verbatim {@code Window#calculateScale}. */
    public static int calculateScale(int framebufferWidth, int framebufferHeight,
                                     int requestedScale, boolean forceUnicode) {
        int i = 1;
        while (i != requestedScale && i < framebufferWidth && i < framebufferHeight
                && framebufferWidth / (i + 1) >= BASE_WIDTH
                && framebufferHeight / (i + 1) >= BASE_HEIGHT) {
            i++;
        }
        if (forceUnicode && i % 2 != 0) {
            i++;
        }
        return i;
    }

    /** Verbatim {@code Window#setGuiScale}'s rounding: ceiling, not floor. */
    public static int scaled(int framebufferPixels, int scale) {
        int floor = (int) ((double) framebufferPixels / (double) scale);
        return ((double) framebufferPixels / (double) scale) > (double) floor ? floor + 1 : floor;
    }

    public static Row row(String label, int windowWidth, int windowHeight, int requestedScale,
                          boolean forceUnicode) {
        int effective = calculateScale(windowWidth, windowHeight, requestedScale, forceUnicode);
        return new Row(label, windowWidth, windowHeight, requestedScale, forceUnicode, effective,
                scaled(windowWidth, effective), scaled(windowHeight, effective));
    }

    /**
     * The acceptance matrix, derived.
     *
     * <p>Ordered narrowest first, so the row that breaks something breaks it early. The worst case
     * is the first entry: a window dragged to Minecraft's own base size with Force Unicode Font on
     * is 160x120 units, half the base the rest of the game assumes, and it is the tightest the
     * arithmetic permits.
     */
    public static List<Row> acceptanceMatrix() {
        List<Row> rows = new ArrayList<>();
        //                                                          window      req  unicode  -> units
        rows.add(row("Smallest window, Force Unicode", BASE_WIDTH, BASE_HEIGHT, 1, true));  // 160x120
        rows.add(row("1024x768 scale 4, Force Unicode", 1024, 768, 4, true));               // 256x192
        rows.add(row("1280x720 scale 4, Force Unicode", 1280, 720, 4, true));               // 320x180
        rows.add(row("640x360 window, Force Unicode", 640, 360, 3, true));                  // 320x180
        rows.add(row("Smallest window", BASE_WIDTH, BASE_HEIGHT, 4, false));                // 320x240
        rows.add(row("1280x1024 scale 4", 1280, 1024, 4, false));                           // 320x256
        rows.add(row("1366x768 scale 4, Force Unicode", 1366, 768, 4, true));               // 342x192
        rows.add(row("1024x768 scale 4", 1024, 768, 4, false));                             // 342x256
        rows.add(row("1600x900 scale 4, Force Unicode", 1600, 900, 4, true));               // 400x225
        rows.add(row("1280x720 scale 3", 1280, 720, 3, false));                             // 427x240
        rows.add(row("2560x1440 auto", 2560, 1440, AUTO, false));                           // 427x240
        rows.add(row("1366x768 scale 3", 1366, 768, 3, false));                             // 456x256
        rows.add(row("1920x1080 scale 4", 1920, 1080, 4, false));                           // 480x270
        rows.add(row("1024x768 scale 2", 1024, 768, 2, false));                             // 512x384
        rows.add(row("640x360 window", 640, 360, 1, false));                                // 640x360
        rows.add(row("1920x1080 scale 3", 1920, 1080, 3, false));                           // 640x360
        rows.add(row("1920x1080 scale 2", 1920, 1080, 2, false));                           // 960x540
        rows.add(row("1920x1080 scale 1", 1920, 1080, 1, false));                           // 1920x1080
        return List.copyOf(rows);
    }

    /** The narrowest row -- the one every layout invariant is really being tested against. */
    public static Row worstCase() {
        Row worst = acceptanceMatrix().get(0);
        for (Row row : acceptanceMatrix()) {
            if (row.scaledWidth() < worst.scaledWidth()
                    || (row.scaledWidth() == worst.scaledWidth()
                        && row.scaledHeight() < worst.scaledHeight())) {
                worst = row;
            }
        }
        return worst;
    }
}
