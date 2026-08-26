package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.diagnostics.GrabbySpawnProtection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one rule on the Grabby path whose answer depends on which kind of server is running.
 *
 * <h2>Why this test is the important one</h2>
 *
 * <p>Grabby Hands was reported working in single player and dead on the dedicated server. Every
 * other check on the path — the gesture, the tags, provenance, policy, reach, the transaction — is
 * identical code reading identical state on both, and the whole existing suite exercises them. This
 * is the exception: {@code MinecraftServer.isUnderSpawnProtection} returns a flat {@code false},
 * and {@code DedicatedServer} overrides it with a real check.
 *
 * <p>An integrated single-player server is a {@code MinecraftServer}. So is a {@code GameTestServer}.
 * That is why no GameTest in this repository can reproduce the failure and why this rule has to be
 * asserted as a rule rather than as a scenario.
 *
 * <p>The consequence when it trips is not a Grabby refusal. It is
 * {@code ServerGamePacketListenerImpl.handleUseItemOn} returning before it calls
 * {@code ServerPlayerGameMode.useItemOn}, which means NeoForge never posts
 * {@code PlayerInteractEvent.RightClickBlock} — no handler runs, no refusal message can be sent, and
 * nothing is logged.
 */
@DisplayName("Vanilla spawn protection is the single-player / dedicated-server divergence")
class GrabbySpawnProtectionTest {

    private static final boolean DEDICATED = true;
    private static final boolean INTEGRATED = false;
    private static final boolean OVERWORLD = true;
    private static final boolean OPS_EMPTY = true;
    private static final boolean OPS_PRESENT = false;
    private static final boolean IS_OP = true;
    private static final boolean NOT_OP = false;

    @Nested
    @DisplayName("no test server in this repository can be a dedicated server")
    class ServerKind {

        /**
         * The reason this rule needs a unit test at all.
         *
         * <p>{@code isUnderSpawnProtection} is only overridden on {@code DedicatedServer}. Every
         * server this project can start in a test - {@code GameTestServer} - inherits
         * {@code MinecraftServer}'s bare {@code return false}, and so does the
         * {@code IntegratedServer} behind single player. No scenario test can therefore reach the
         * refusing branch, however it is named, so the rule is asserted directly instead.
         */
        @Test
        @DisplayName("GameTestServer is not a DedicatedServer, so no GameTest can reach the refusing branch")
        void gameTestServerCannotExposeTheDivergence() {
            assertFalse(DedicatedServer.class.isAssignableFrom(GameTestServer.class),
                    "if a GameTestServer ever became a DedicatedServer, a GameTest could cover this "
                            + "for real and GrabbyServerPacketPathGameTests should be extended to do so");
        }

        @Test
        @DisplayName("IntegratedServer is not a DedicatedServer either, which is why single player passes")
        void integratedServerCannotExposeTheDivergence() {
            assertFalse(DedicatedServer.class.isAssignableFrom(IntegratedServer.class));
        }

        @Test
        @DisplayName("both inherit MinecraftServer, whose implementation is unconditionally permissive")
        void bothInheritTheFlatFalse() {
            assertTrue(MinecraftServer.class.isAssignableFrom(GameTestServer.class));
            assertTrue(MinecraftServer.class.isAssignableFrom(IntegratedServer.class));
        }
    }

    @Nested
    @DisplayName("the same click, on the two kinds of server")
    class Parity {

        @Test
        @DisplayName("an integrated single-player server never applies it, whatever the settings say")
        void integratedServerNeverBlocks() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    INTEGRATED, OVERWORLD, OPS_PRESENT, NOT_OP, 16, 0),
                    "single player must not be able to reproduce the production refusal");
        }

        @Test
        @DisplayName("a dedicated server blocks the identical click for a non-operator at spawn")
        void dedicatedServerBlocksTheSameClick() {
            assertTrue(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_PRESENT, NOT_OP, 16, 0),
                    "this is the divergence the parity defect was reported as");
        }
    }

    @Nested
    @DisplayName("every clause that disarms it")
    class Exemptions {

        @Test
        @DisplayName("operators are exempt, which is why the person testing never sees it")
        void operatorsAreExempt() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_PRESENT, IS_OP, 16, 0));
        }

        @Test
        @DisplayName("an empty ops list disables the rule for everyone")
        void emptyOpListDisablesIt() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_EMPTY, NOT_OP, 16, 0));
        }

        @Test
        @DisplayName("spawn-protection=0 disables it")
        void zeroRadiusDisablesIt() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_PRESENT, NOT_OP, 0, 0));
        }

        @Test
        @DisplayName("only the Overworld is protected")
        void otherDimensionsAreExempt() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, !OVERWORLD, OPS_PRESENT, NOT_OP, 16, 0));
        }
    }

    @Nested
    @DisplayName("the radius is a square measured on X and Z only")
    class Radius {

        @Test
        @DisplayName("the boundary block is inside the protected square")
        void boundaryIsInclusive() {
            assertTrue(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_PRESENT, NOT_OP, 16, 16),
                    "vanilla uses <=, so the last ring is protected too");
        }

        @Test
        @DisplayName("one block further out is free")
        void justOutsideIsFree() {
            assertFalse(GrabbySpawnProtection.blocksInteraction(
                    DEDICATED, OVERWORLD, OPS_PRESENT, NOT_OP, 16, 17));
        }

        @Test
        @DisplayName("distance is the larger of the X and Z offsets, and ignores Y entirely")
        void chebyshevOnTheHorizontalPlane() {
            BlockPos spawn = new BlockPos(100, 64, -40);
            assertEquals(7, GrabbySpawnProtection.chebyshevDistanceToSpawn(new BlockPos(107, 64, -40), spawn));
            assertEquals(9, GrabbySpawnProtection.chebyshevDistanceToSpawn(new BlockPos(103, 64, -49), spawn));
            assertEquals(0, GrabbySpawnProtection.chebyshevDistanceToSpawn(new BlockPos(100, 200, -40), spawn),
                    "a tower directly above spawn is just as protected as the ground under it");
        }
    }
}
