package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.util.HouseDataAPI;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

/**
 * Housing Deed Milestone 2: puts every house region back after a restart.
 *
 * <p>{@link StructureRegionManager} is a static map with one production writer -- placement --
 * and nothing that ever saved or loaded it. A restart emptied it, and eight consumers read it:
 * the owner's right to break blocks inside their own house and under it, every door's lock
 * identity, farm plots, Grabby policy, survival zones and structure protection. All of them
 * silently changed answer. That is the reported basement defect, and the permanently locked
 * door is the same defect wearing a different hat: {@code getStructureLockId()} returns null
 * when no region encloses the door, so {@code LockableDoorBlock} never reaches the branch that
 * would accept a key, and {@code locked} defaults to true and persists in block-entity NBT.
 *
 * <p>The world cannot be the source. Rebuilding from lot blocks would mean loading every chunk
 * on the shard at boot. Rails already holds one row per house and can answer for all of them in
 * a single request, so that is where this reads from.
 *
 * <h2>Failing safe</h2>
 * A shard that cannot reach Rails must not quietly strip every player of their house rights and
 * then say nothing about it. Nor may it say something about it sixty times a second -- that is
 * the console-flood shape that has taken this server down before. So: a small bounded retry off
 * the server thread, and exactly one log line at the end, whichever way it went.
 *
 * <p>When it does give up, the map stays empty. That is not good, but it is precisely today's
 * behaviour rather than something new, and it is now visible in the log instead of invisible.
 */
public final class StructureRegionRehydrator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Three tries over roughly ten seconds; Rails is usually up or it is not. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MILLIS = { 2_000L, 8_000L };

    private StructureRegionRehydrator() {}

    /**
     * What a rehydration attempt did, so the caller can say so in one line.
     *
     * @param registered      regions put back into the manager
     * @param withoutStructure rows carrying no {@code structure} payload -- houses placed before
     *                        this milestone, which cannot be rebuilt and never will be
     * @param malformed       rows that had a payload this build could not read
     */
    public record Result(int registered, int withoutStructure, int malformed) {
        public int total() {
            return registered + withoutStructure + malformed;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Entry point                                                        */
    /* ------------------------------------------------------------------ */

    /** Called from {@code ServerStartedEvent}. Returns immediately; the work is off-thread. */
    public static void start(MinecraftServer server) {
        if (server == null) return;

        // A shard with no Rails credentials -- single player, a GameTest server -- has no house
        // rows to fetch and never had. Saying nothing is correct here; there is no degradation.
        if (ServerAuthRegistry.credentials(server).isEmpty()) return;

        try {
            ServerHttpExecutor.submit(server, () -> fetchWithRetry(server))
                .whenComplete((rows, failure) -> server.execute(() -> {
                    if (failure != null || rows == null) {
                        LOGGER.warn("House regions could not be restored after {} attempts: every "
                                + "player's build rights inside their own house, and every door lock, "
                                + "stay unresolved until the next restart reaches Rails.",
                                MAX_ATTEMPTS);
                        return;
                    }
                    Result result = apply(rows);
                    LOGGER.info("Restored {} house regions from Rails ({} of {} rows had no stored "
                            + "structure and predate region persistence; {} could not be read).",
                            result.registered(), result.withoutStructure(), result.total(),
                            result.malformed());
                }));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("House region restore was refused by a full request queue; regions stay empty.");
        }
    }

    @Nullable
    private static JsonArray fetchWithRetry(MinecraftServer server) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            JsonArray rows = HouseDataAPI.fetchShardHouses(server);
            if (rows != null) return rows;

            if (attempt < BACKOFF_MILLIS.length) {
                try {
                    Thread.sleep(BACKOFF_MILLIS[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*  The testable half                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Register every row that carries a readable region. Pure with respect to the network, so a
     * GameTest can hand it a payload and prove a door resolves its lock afterwards.
     *
     * <p>Re-registering is safe: {@code registerStructure} removes any existing entry for the
     * same house UUID before inserting, so running this twice leaves one record per house.
     */
    public static Result apply(JsonArray rows) {
        if (rows == null) return new Result(0, 0, 0);

        int registered = 0;
        int withoutStructure = 0;
        int malformed = 0;

        for (JsonElement element : rows) {
            if (element == null || !element.isJsonObject()) {
                malformed++;
                continue;
            }
            var row = element.getAsJsonObject();
            Optional<StructureRecord> record = StructureRegionCodec.decode(row);
            if (record.isPresent()) {
                StructureRegionManager.registerStructure(record.get());
                registered++;
            } else if (!row.has("structure") || row.get("structure").isJsonNull()) {
                withoutStructure++;
            } else {
                malformed++;
            }
        }
        return new Result(registered, withoutStructure, malformed);
    }
}
