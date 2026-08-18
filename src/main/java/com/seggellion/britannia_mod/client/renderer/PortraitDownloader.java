package com.seggellion.britannia_mod.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fetches an NPC portrait from cloud storage, falling back to the generic peasant.
 *
 * <h2>What changed here, and what deliberately did not</h2>
 * This class previously reported almost nothing. A non-200 response took the {@code if} with no
 * {@code else} and vanished without a single log line, so a 404 or a 500 was indistinguishable from
 * a portrait nobody had asked for. Every throwable - connect timeout, read timeout, corrupt PNG -
 * collapsed into one message carrying only the NPC name: no status, no timing, no exception, not
 * even which of those had happened. Production showed exactly that: a {@code CONNECTION SUCECSS} at
 * status 200, then five seconds of nothing, then {@code Failed to download portrait? Drake} - enough
 * to know it broke, not enough to know where.
 *
 * <p>So the failure paths now record stage, status, elapsed time and the actual exception, and the
 * per-request success chatter is gone. The timeouts are unchanged (5s connect, 5s read), no retry
 * has been added, the fallback and caching behaviour is exactly as before, and the name used to
 * build the URL is untouched - see {@link PortraitFetch} on why that name is already correct.
 *
 * <h2>Known limitation, deliberately left alone</h2>
 * The fallback is written into the cache before the request starts and is only overwritten on
 * success, so a single failure pins that NPC to the generic portrait for the rest of the session.
 * That is pre-existing behaviour, and it is what stops a broken portrait re-requesting on every
 * frame; changing it would mean adding the retry policy this hotfix is explicitly not adding.
 */
public class PortraitDownloader {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/portraits/generic_peasant.png");
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 5000;

    public static ResourceLocation getPortrait(String npcName, String gender) {
        String safeGender = PortraitFetch.safeGender(gender);
        String cacheKey = PortraitFetch.cacheKey(npcName, gender);

        if (CACHE.containsKey(cacheKey)) {
            return CACHE.get(cacheKey);
        }

        CACHE.put(cacheKey, FALLBACK);

        CompletableFuture.runAsync(() -> {
            long startedAt = System.currentTimeMillis();
            // Tracked so a failure can say how far the request actually got, rather than leaving
            // one message to cover connect, status, body read and decode alike.
            PortraitFetch.Stage[] stage = {PortraitFetch.Stage.STATUS};
            int status = -1;

            try {
                URL url = new URL(PortraitFetch.portraitUrl(npcName, gender));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);

                status = connection.getResponseCode();
                if (status != 200) {
                    // Previously the silent path: no branch, no log, permanent fallback.
                    LOGGER.warn(PortraitFetch.describeFailure(npcName, gender, PortraitFetch.Stage.STATUS,
                            status, System.currentTimeMillis() - startedAt, null));
                    connection.disconnect();
                    return;
                }

                // A 200 only means the headers arrived. The body is still to come, and in
                // production this is where the five seconds went.
                stage[0] = PortraitFetch.Stage.BODY_READ;
                NativeImage nativeImage;
                try (InputStream is = connection.getInputStream()) {
                    // NativeImage.read both reads and decodes, so a failure inside it could be
                    // either. A read timeout surfaces as an IOException from the socket, a bad PNG
                    // as one from the decoder; the exception class in the log separates them.
                    stage[0] = PortraitFetch.Stage.IMAGE_DECODE;
                    nativeImage = NativeImage.read(is);
                }

                long decodedAtMillis = System.currentTimeMillis() - startedAt;
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture texture = new DynamicTexture(nativeImage);
                        ResourceLocation dynamicLocation = ResourceLocation.fromNamespaceAndPath(
                                "britannia_mod", "downloaded_portrait_" + cacheKey);
                        Minecraft.getInstance().getTextureManager().register(dynamicLocation, texture);
                        CACHE.put(cacheKey, dynamicLocation);
                        LOGGER.debug("Portrait fetch succeeded npc={} gender={} elapsedMs={}",
                                npcName, safeGender, decodedAtMillis);
                    } catch (Exception registerFailure) {
                        // This runs on the render thread, where an escaping exception is a crash
                        // rather than a missing portrait.
                        nativeImage.close();
                        LOGGER.warn(PortraitFetch.describeFailure(npcName, gender,
                                PortraitFetch.Stage.TEXTURE_REGISTER, 200,
                                System.currentTimeMillis() - startedAt, registerFailure));
                    }
                });
            } catch (Exception failure) {
                LOGGER.warn(PortraitFetch.describeFailure(npcName, gender, stage[0], status,
                        System.currentTimeMillis() - startedAt, failure));
            }
        });

        return FALLBACK;
    }
}
