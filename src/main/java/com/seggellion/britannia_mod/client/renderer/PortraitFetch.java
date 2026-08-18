package com.seggellion.britannia_mod.client.render;

import java.util.Locale;

/**
 * The parts of a portrait fetch that do not need Minecraft: how an NPC's canonical identity becomes
 * a URL and a cache key, and how a failure becomes a diagnosable log line.
 *
 * <h2>Why this is separate</h2>
 * {@link PortraitDownloader} cannot be unit-tested — it reads a {@code NativeImage} and registers a
 * texture, both of which need the client's natives and a running {@code Minecraft} instance. The
 * identity and classification rules are the part worth pinning, and they are pure, so they live
 * here where a test can reach them.
 *
 * <h2>Identity</h2>
 * The name used here is the NPC's <em>personal</em> name — {@code "Drake"} — not the composed
 * nameplate {@code "Drake the Bank Teller"}. Those are different fields:
 * {@code ServiceNpcEntity.updateDisplayName()} writes the composed form into vanilla's custom-name
 * field and leaves {@code personalName} alone, and the banking payload carries {@code personalName}
 * through to the portrait. Nothing here should ever see a role title; if it does, the defect is
 * upstream in whoever supplied the name, and the sanitized URL logged on failure will show it.
 */
public final class PortraitFetch {
    /** Base URL up to, and including, the portraits folder. */
    public static final String GCS_BASE_URL = "https://storage.googleapis.com/ultimacraft/portraits/";

    private PortraitFetch() {}

    /**
     * Where a portrait fetch can fail. Distinguishing these is the whole point of the instrumentation:
     * a 404 and a stalled body read and a corrupt PNG all previously surfaced as the same silence, or
     * as the same one-line "Failed to download portrait?".
     */
    public enum Stage {
        /** Connecting, and reading the response status line. A non-2xx status is reported here. */
        STATUS,
        /** Reading the response body off the socket. This is where a read timeout lands. */
        BODY_READ,
        /** Decoding those bytes as a PNG. */
        IMAGE_DECODE,
        /** Handing the decoded image to the texture manager on the render thread. */
        TEXTURE_REGISTER
    }

    /** Spaces become underscores; everything else is left alone. */
    public static String sanitizeName(String npcName) {
        return npcName == null ? "" : npcName.replace(" ", "_");
    }

    /** The gender folder, defaulting to {@code unknown} when none was supplied. */
    public static String safeGender(String gender) {
        return (gender == null || gender.isEmpty()) ? "unknown" : gender.toLowerCase(Locale.ROOT);
    }

    /** The per-NPC cache key, e.g. {@code male_lord_british}. */
    public static String cacheKey(String npcName, String gender) {
        return (safeGender(gender) + "_" + sanitizeName(npcName)).toLowerCase(Locale.ROOT);
    }

    /** The portrait URL, e.g. {@code .../portraits/male/Drake.png}. */
    public static String portraitUrl(String npcName, String gender) {
        return GCS_BASE_URL + safeGender(gender) + "/" + sanitizeName(npcName) + ".png";
    }

    /**
     * One line carrying everything needed to classify a production portrait failure without a
     * repro: which NPC, which URL, how far it got, what the server said, how long it took, and what
     * was actually thrown.
     *
     * @param httpStatus the response status, or {@code -1} if the request never produced one
     * @param error      the exception, or {@code null} when the failure was a bad status rather
     *                   than a throwable
     */
    public static String describeFailure(String npcName, String gender, Stage stage,
                                         int httpStatus, long elapsedMillis, Throwable error) {
        StringBuilder line = new StringBuilder()
                .append("Portrait fetch failed npc=").append(npcName)
                .append(" gender=").append(safeGender(gender))
                .append(" url=").append(portraitUrl(npcName, gender))
                .append(" stage=").append(stage)
                .append(" httpStatus=").append(httpStatus < 0 ? "unknown" : Integer.toString(httpStatus))
                .append(" elapsedMs=").append(elapsedMillis);

        if (error != null) {
            line.append(" error=").append(error.getClass().getName())
                    .append(" message=").append(concise(error.getMessage()));
        }
        return line.toString();
    }

    /**
     * Exception messages reach the log verbatim, and some of them are whole HTML error pages. Keep
     * one readable line: no newlines, and a hard length cap.
     */
    static String concise(String message) {
        if (message == null || message.isBlank()) return "(none)";
        String flattened = message.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 200 ? flattened : flattened.substring(0, 197) + "...";
    }
}
