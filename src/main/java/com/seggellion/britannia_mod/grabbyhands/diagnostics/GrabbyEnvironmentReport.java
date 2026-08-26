package com.seggellion.britannia_mod.grabbyhands.diagnostics;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What kind of server this is, which artifact it is running, and whether anything about the
 * environment can stop a Grabby interaction before Grabby ever sees it.
 *
 * <h2>Why an environment report is part of the feature</h2>
 *
 * <p>"Grabby Hands does nothing on the server" has now been reported twice, and both times the first
 * hour went on establishing facts the running server already knew: which jar is loaded, whether it
 * is the one carrying the fix, and whether the interaction is even reaching the mod. None of that
 * was answerable from inside the game, so it was guessed at instead.
 *
 * <p>Everything here is read-only and mutates nothing. It is printed once at server start and is
 * available on demand through {@code /grabby env}.
 */
public final class GrabbyEnvironmentReport {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Computed at most once: hashing the jar is cheap but not free, and it cannot change at runtime. */
    private static volatile String cachedArtifactFingerprint;

    private GrabbyEnvironmentReport() {
    }

    /** One fact per line, in the order an operator needs them. */
    public static List<String> lines(@Nullable MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        lines.add("artifact: " + artifactFingerprint());
        lines.add("neoforge: " + FMLLoader.versionInfo().neoForgeVersion()
                + "  minecraft: " + FMLLoader.versionInfo().mcVersion());
        lines.add("server: " + serverKind(server));
        lines.addAll(spawnProtectionLines(server));
        return lines;
    }

    /**
     * The exact identity of the loaded mod file.
     *
     * <p>A version string alone has never been enough: the version does not change on every fix, and
     * a stale jar keeps the version of the build it came from. The SHA-256 is the thing an operator
     * can actually compare against the jar they meant to deploy.
     */
    public static String artifactFingerprint() {
        String cached = cachedArtifactFingerprint;
        if (cached != null) {
            return cached;
        }
        String computed = computeArtifactFingerprint();
        cachedArtifactFingerprint = computed;
        return computed;
    }

    private static String computeArtifactFingerprint() {
        String version = ModList.get()
                .getModContainerById(BritanniaMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        Path jar = modFilePath();
        if (jar == null) {
            return BritanniaMod.MODID + " " + version + " (file unknown)";
        }
        String detail;
        try {
            detail = jar.getFileName() + " " + Files.size(jar) + "B"
                    + " modified=" + Instant.ofEpochMilli(Files.getLastModifiedTime(jar).toMillis())
                    + " sha256=" + sha256(jar);
        } catch (Exception exception) {
            // A dev run loads exploded classes rather than a jar; that is a fact worth reporting,
            // not an error worth failing the whole report over.
            detail = jar + " (not hashable: " + exception.getClass().getSimpleName() + ")";
        }
        return BritanniaMod.MODID + " " + version + " " + detail;
    }

    @Nullable
    private static Path modFilePath() {
        try {
            var file = ModList.get().getModFileById(BritanniaMod.MODID);
            return file == null ? null : file.getFile().getFilePath();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16))
               .append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }

    public static String serverKind(@Nullable MinecraftServer server) {
        if (server == null) {
            return "none";
        }
        if (server instanceof DedicatedServer) {
            return "dedicated (DedicatedServer)";
        }
        // Single player and LAN both run an IntegratedServer; GameTests run a GameTestServer. Both
        // inherit MinecraftServer.isUnderSpawnProtection, which is a bare "return false".
        return "integrated/other (" + server.getClass().getSimpleName()
                + ") - vanilla spawn protection cannot apply here";
    }

    private static List<String> spawnProtectionLines(@Nullable MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        if (!(server instanceof DedicatedServer dedicated)) {
            lines.add("spawn-protection: not applicable on this server type");
            return lines;
        }
        int radius = dedicated.getSpawnProtectionRadius();
        boolean opsEmpty = dedicated.getPlayerList().getOps().isEmpty();
        lines.add("spawn-protection: radius=" + radius + " opsListEmpty=" + opsEmpty
                + " armed=" + GrabbySpawnProtection.armed(dedicated));
        ServerLevel overworld = dedicated.overworld();
        if (overworld != null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            lines.add("world spawn: " + spawn.toShortString()
                    + " (protected square is +/-" + radius + " on X and Z, Overworld only)");
        }
        return lines;
    }

    /**
     * Says the dangerous thing once, at boot, where an operator will actually see it.
     *
     * <p>Spawn protection does not merely refuse Grabby Hands. It drops the whole
     * {@code ServerboundUseItemOnPacket} for every non-operator inside the radius, so no block
     * interaction of any kind reaches any mod code there, with no message to the player and no line
     * in the log. Britannia already holds every player in Adventure and gates building, breaking and
     * mutation through its own systems, so the vanilla radius adds nothing except a silent hole.
     * Turning it off is an operator decision rather than the mod's, so this warns rather than acts.
     */
    public static void logAtStartup(@Nullable MinecraftServer server) {
        for (String line : lines(server)) {
            LOGGER.info("[grabby-hands][env] {}", line);
        }
        if (GrabbySpawnProtection.armed(server)) {
            LOGGER.error("[grabby-hands][env] vanilla spawn protection is ARMED (spawn-protection={}). "
                            + "Inside that radius the server drops every non-operator block-use packet before "
                            + "PlayerInteractEvent.RightClickBlock is posted, so Grabby Hands - and every other "
                            + "right-click interaction - silently does nothing there. Set spawn-protection=0 in "
                            + "server.properties if Britannia's own protection is meant to be authoritative.",
                    GrabbySpawnProtection.radius(server));
        }
    }
}
