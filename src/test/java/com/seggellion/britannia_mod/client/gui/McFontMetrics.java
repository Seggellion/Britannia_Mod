package com.seggellion.britannia_mod.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * An approximation of Minecraft's {@code minecraft:default} font metrics, for the offline layout
 * renderer.
 *
 * <p>The layout records take a wrapped line <i>count</i> and a line height, exactly as the real
 * screen supplies them from {@code Font#split} and {@code Font#lineHeight}. The renderer has no
 * {@code Font}, so it needs its own measure to produce that count and to place glyphs. This is
 * that measure: the standard ASCII advance table for the vanilla font, where a glyph is six pixels
 * wide including its one-pixel gap, with the well-known narrow exceptions.
 *
 * <p><b>It is an approximation.</b> It covers ASCII only, ignores the unicode fallback font
 * entirely, and takes no account of style. The renderings it produces are therefore accurate about
 * <i>where the layout puts things</i> -- which is what they exist to show -- and only
 * representative about where individual glyphs fall inside a line. They are layout renderings, not
 * screenshots.
 */
final class McFontMetrics {

    private McFontMetrics() {
    }

    /** {@code Font#lineHeight} for the vanilla font. */
    static final int LINE_HEIGHT = 9;

    /** Height of a glyph itself, inside the nine-unit line box. */
    static final int GLYPH_HEIGHT = 8;

    private static final int DEFAULT_ADVANCE = 6;

    static int charWidth(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', ',', '.', ':', ';', 'i', '|' -> 2;
            case '\'', 'l', '`' -> 3;
            case 'I', '[', ']', 't' -> 4;
            case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> DEFAULT_ADVANCE;
        };
    }

    static int width(String text) {
        if (text == null) return 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += charWidth(text.charAt(i));
        }
        return total;
    }

    /**
     * Wraps like {@code Font#split}: on whitespace where it can, mid-word only when a single word
     * is wider than the whole column.
     */
    static List<String> wrap(String text, int wrapWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int limit = Math.max(1, wrapWidth);

        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        for (String word : text.split(" ", -1)) {
            int wordWidth = width(word);
            int spaceWidth = line.length() == 0 ? 0 : charWidth(' ');

            if (lineWidth + spaceWidth + wordWidth <= limit) {
                if (spaceWidth > 0) line.append(' ');
                line.append(word);
                lineWidth += spaceWidth + wordWidth;
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
            // A word wider than the column has to break somewhere.
            while (width(word) > limit) {
                int taken = 0;
                int cut = 0;
                while (cut < word.length() && taken + charWidth(word.charAt(cut)) <= limit) {
                    taken += charWidth(word.charAt(cut));
                    cut++;
                }
                cut = Math.max(1, cut);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
            lineWidth = width(word);
        }
        lines.add(line.toString());
        return lines;
    }

    /** Truncates with an ellipsis, the way {@code QuestScreenDraw#fit} does. */
    static String fit(String text, int maxWidth) {
        if (text == null) return "";
        if (width(text) <= maxWidth) return text;
        int room = Math.max(0, maxWidth - width("..."));
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            int advance = charWidth(text.charAt(i));
            if (used + advance > room) break;
            out.append(text.charAt(i));
            used += advance;
        }
        return out + "...";
    }
}
