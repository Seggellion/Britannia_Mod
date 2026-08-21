package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Milestone 7 source contracts for the restoration and provenance policy — the properties that are
 * easy to regress silently and expensive to notice in a running world.
 */
class MiningRestorationPolicyTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    private static String source(String relative) throws Exception {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative))
                .replace("\r\n", "\n");
    }

    /**
     * Source with comments stripped. These contracts are about what the code does, and the
     * explanatory comments necessarily quote the very patterns being forbidden.
     */
    private static String code(String relative) throws Exception {
        return source(relative)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    @Test
    void everyActiveMineableStaysRestorable() throws Exception {
        MineableCatalog catalog = MineableCatalog.parse(Files.newBufferedReader(
                PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json")));
        for (MineableDefinition definition : catalog.active()) {
            assertTrue(definition.restorable(), definition.id() + " must cooperate with restoration");
        }
    }

    /**
     * Restoration never forces a chunk to load, and there is still only one system doing it.
     *
     * <p>Rewritten at milestone 4, and the rewrite is the second time this file has had to give
     * way to a correct change. It used to assert that {@code BlockRestoreHandler} contained exactly
     * one {@code @SubscribeEvent} and the literal string {@code level.isLoaded(data.pos)} — both
     * true of the every-tick full scan, and both false of the event-driven scheduler that replaced
     * it. The invariant was never "one listener"; it was "one scheduler, and it never force-loads".
     *
     * <p>What is left here is the part a static check is genuinely good at: proving an API is
     * <em>absent</em>. That restoration actually leaves unloaded chunks alone is proved by driving
     * it — {@code RestorationSchedulerTest.tenThousandUnloadedDebtsAreNeverInspected} and the
     * {@code DepositLifecycleGameTests} force-load case — rather than by reading it.
     */
    @Test
    void restorationNeverForceLoadsAChunk() throws Exception {
        for (String source : List.of(
                "block/blockrestore/BlockRestoreHandler.java",
                "block/blockrestore/RestorationScheduler.java",
                "block/blockrestore/BrokenBlockDataStorage.java")) {
            String body = code(source);
            // The APIs that would create or keep a chunk loaded. Deliberately not a blanket ban on
            // touching a chunk at all: the load handler is handed its own chunk by the event, and
            // the store asks getChunkNow whether a chunk is already there -- which returns null
            // rather than loading one, and is exactly the question that has to be asked.
            for (String forbidden : List.of("setChunkForced", "forceLoad", "addRegionTicket",
                    "getChunkAt", "getChunkFuture")) {
                assertFalse(body.contains(forbidden),
                        source + " must never make a chunk exist to restore into it (found "
                                + forbidden + ")");
            }
        }

        String storage = code("block/blockrestore/BrokenBlockDataStorage.java");
        if (storage.contains("getChunkSource()")) {
            assertTrue(storage.contains("getChunkNow"),
                    "the only permitted chunk-source question is the non-loading one");
        }
    }

    /** Still exactly one thing scheduling restorations, however many events it listens to. */
    @Test
    void thereIsStillOnlyOneRestorationScheduler() throws Exception {
        String handler = code("block/blockrestore/BlockRestoreHandler.java");
        assertTrue(handler.contains("ServerTickEvent.Pre"),
                "the cadenced pass still rides the server tick");
        assertTrue(handler.contains("ChunkEvent.Load") && handler.contains("ChunkEvent.Unload"),
                "and it is driven by chunk lifecycle rather than by sweeping everything");
        assertEquals(1, handler.split("runPass", -1).length - 1,
                "exactly one place may drain the due queue");
    }

    /** Restoration must cover every dimension, not just the Overworld it used to hard-code. */
    @Test
    void restorationCoversEveryDimension() throws Exception {
        String handler = code("block/blockrestore/BlockRestoreHandler.java");
        assertTrue(handler.contains("server.getAllLevels()"),
                "records are stored per level, so every level must be swept");
        assertFalse(handler.contains("Level.OVERWORLD"),
                "the Overworld-only restriction stranded Nether nodes (Basalt, Blackstone)");
    }

    /**
     * The occupancy policy, driven rather than read.
     *
     * <p>This test used to assert that {@code BlockRestoreHandler.java} <em>contained the string</em>
     * {@code canBeReplaced}. That is what a source-text contract costs: the call it was pinning was
     * the bug. In 1.21.1 {@code minecraft:water} and {@code minecraft:lava} are both declared
     * {@code .replaceable()}, so "air or replaceable" meant restoration came back by deleting the
     * fluid standing in the cell. Pinning the spelling of the defect made it look protected.
     *
     * <p>It now exercises the policy function itself across every combination that matters. Real
     * water and lava in a real world are covered by {@code MiningRestorationGameTests}; this is
     * the exhaustive half, and it needs no Minecraft to run.
     */
    @Test
    void restorationRefusesOccupiedCells() {
        // The ordinary case: the node was mined, nothing moved in, it comes back.
        assertTrue(BlockRestoreHandler.cellStateAllowsRestoration(false, true, true, false),
                "an empty cell must accept the node back");
        // Replaceable ground cover is explicitly allowed: grass or a snow layer is not construction.
        assertTrue(BlockRestoreHandler.cellStateAllowsRestoration(false, false, true, false),
                "a replaceable non-fluid state must accept the node back");

        // Fluids block, whatever else is true of the cell. This is the milestone 1 correction.
        assertFalse(BlockRestoreHandler.cellStateAllowsRestoration(true, false, true, false),
                "water or lava must block restoration rather than be deleted by it");
        assertFalse(BlockRestoreHandler.cellStateAllowsRestoration(true, true, true, false),
                "a fluid must block even when the state also reports air");

        // Somebody built here.
        assertFalse(BlockRestoreHandler.cellStateAllowsRestoration(false, false, false, false),
                "a solid non-replaceable state is construction and must block");
        // ...and a block entity is never overwritten, even if its state claimed to be replaceable.
        assertFalse(BlockRestoreHandler.cellStateAllowsRestoration(false, false, true, true),
                "a block entity must block restoration");
        assertFalse(BlockRestoreHandler.cellStateAllowsRestoration(false, true, true, true),
                "a block entity must block even in a cell that reports air");
    }

    /** The guard is still wired into the real level check, and still consults entities. */
    @Test
    void theOccupancyGuardIsWiredIntoTheLevelCheck() throws Exception {
        String handler = code("block/blockrestore/BlockRestoreHandler.java");
        assertTrue(handler.contains("cellStateAllowsRestoration"),
                "canRestoreInto must delegate to the policy the test above drives");
        assertTrue(handler.contains("getFluidState"),
                "the fluid question must be asked of the level, not inferred from the block state");
        assertTrue(handler.contains("getEntitiesOfClass"),
                "an entity standing in the cell must still block");
    }

    /**
     * The marker must outlive every reader on a break. Clearing it in the HIGH-priority gate would
     * erase the evidence before the managed flow and the award consult it, handing the place-break
     * loop straight back.
     */
    @Test
    void provenanceIsClearedOnlyAfterEveryReaderHasRun() throws Exception {
        String gate = code("mining/MiningGateHandler.java");
        assertTrue(gate.contains("EventPriority.LOWEST"), "the clearing listener must run last");
        int lowest = gate.indexOf("EventPriority.LOWEST");
        int forget = gate.indexOf("MiningProvenance.forget");
        assertTrue(forget > lowest,
                "forget() must live in the LOWEST-priority listener, not the gate itself");
        assertEquals(1, gate.split("MiningProvenance.forget", -1).length - 1,
                "exactly one place may clear the marker");
    }

    /** Absence means natural, which is what lets existing worlds work without a migration. */
    @Test
    void provenanceDefaultsToNatural() throws Exception {
        String provenance = code("mining/MiningProvenance.java");
        assertTrue(provenance.contains("extends SavedData"), "provenance must persist with the level");
        assertTrue(provenance.contains("putLongArray"), "positions must be stored compactly");

        String handler = code("mining/MiningProvenanceHandler.java");
        assertTrue(handler.contains("EntityPlaceEvent"), "only real placements may mark a position");
        assertTrue(handler.contains("FakePlayer"), "automation must not mark positions");
        assertTrue(handler.contains("Mineables.resolve"),
                "only catalogued mineables are tracked, so ordinary building costs nothing");
    }

    /** The managed flow must consult provenance before it drops, schedules, or awards. */
    @Test
    void managedFlowSkipsPlayerPlacedBlocks() throws Exception {
        String flow = code("event/CustomBlockBreakHandler.java");
        int provenanceCheck = flow.indexOf("MiningProvenance.isPlayerPlaced");
        assertTrue(provenanceCheck > 0, "the managed flow must check provenance");
        assertTrue(provenanceCheck < flow.indexOf("recordBrokenBlock"),
                "provenance must be checked before restoration is scheduled");
        assertTrue(provenanceCheck < flow.indexOf("MiningSkill.awardForBreak"),
                "provenance must be checked before Mining is awarded");
    }
}
