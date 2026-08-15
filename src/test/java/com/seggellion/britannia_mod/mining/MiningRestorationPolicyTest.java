package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** One scheduler only: restoration must not grow a second timer or queue. */
    @Test
    void restorationKeepsASingleSchedulerAndNeverForceLoads() throws Exception {
        String handler = code("block/blockrestore/BlockRestoreHandler.java");
        assertTrue(handler.contains("ServerTickEvent.Pre"), "the one existing tick hook must remain");
        assertEquals(1, handler.split("@SubscribeEvent", -1).length - 1,
                "restoration must keep exactly one event listener");
        for (String forbidden : List.of("setChunkForced", "forceLoad", "addRegionTicket")) {
            assertFalse(handler.contains(forbidden),
                    "restoration must never force-load chunks (found " + forbidden + ")");
        }
        assertTrue(handler.contains("level.isLoaded(data.pos)"),
                "an unloaded cell must be retried later, not force-loaded");
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

    /** Restoration must never overwrite construction or bury an entity. */
    @Test
    void restorationRefusesOccupiedCells() throws Exception {
        String handler = code("block/blockrestore/BlockRestoreHandler.java");
        assertTrue(handler.contains("canRestoreInto"), "an occupancy guard must exist");
        assertTrue(handler.contains("canBeReplaced"), "a built-over cell must be detected");
        assertTrue(handler.contains("getEntitiesOfClass"), "an occupied cell must be detected");
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
