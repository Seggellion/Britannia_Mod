package com.seggellion.britannia_mod.grabbyhands.destruction;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R-2.11 confirmation contract.
 *
 * <p>The prompt is an accident guard, and the session is what stops it becoming an attack surface.
 * A confirmation carries a session id and nothing else, so every fact it was authorised against has
 * to be re-established server-side — otherwise a replayed or forged packet would destroy something.
 */
class GrabbyDestructionConfirmationTest {
    private static final BlockPos POS = new BlockPos(1, 64, 1);
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOMEBODY_ELSE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static BlockState state;
    private static long now;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        state = Blocks.OAK_STAIRS.defaultBlockState();
    }

    @BeforeEach
    void setUp() {
        GrabbyDestructionSessions.clear();
        now = 1_000_000L;
    }

    private static ItemStack axe() {
        return new ItemStack(Items.IRON_AXE);
    }

    private static GrabbyDestructionSession issue() {
        return GrabbyDestructionSessions.issue(PLAYER, POS, state, axe(), now);
    }

    // ------------------------------------------------------------------
    // Single use
    // ------------------------------------------------------------------

    @Test
    void aConfirmationCanBeAnsweredExactlyOnce() {
        GrabbyDestructionSession session = issue();

        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), now).isPresent());
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), now).isEmpty(),
                "a replayed confirmation packet must do nothing at all");
    }

    @Test
    void anUnknownSessionIdIsRejected() {
        issue();
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, UUID.randomUUID(), now).isEmpty());
    }

    @Test
    void aSessionIssuedToSomebodyElseCannotBeAnswered() {
        GrabbyDestructionSession session = issue();

        assertTrue(GrabbyDestructionSessions.consume(SOMEBODY_ELSE, session.sessionId(), now).isEmpty());
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), now).isPresent(),
                "and the rightful owner's session is left intact");
    }

    @Test
    void anExpiredSessionIsRejected() {
        GrabbyDestructionSession session = issue();

        long afterExpiry = now + GrabbyDestructionSessions.TTL_MILLIS;
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), afterExpiry).isEmpty());
    }

    @Test
    void aSessionIsStillGoodJustBeforeItExpires() {
        GrabbyDestructionSession session = issue();

        long justInTime = now + GrabbyDestructionSessions.TTL_MILLIS - 1;
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), justInTime).isPresent());
    }

    @Test
    void onlyOneQuestionIsOutstandingPerPlayer() {
        // Otherwise a player could accumulate prompts and answer the wrong one.
        GrabbyDestructionSession first = issue();
        GrabbyDestructionSession second = GrabbyDestructionSessions.issue(
                PLAYER, POS.above(), state, axe(), now);

        assertTrue(GrabbyDestructionSessions.consume(PLAYER, first.sessionId(), now).isEmpty(),
                "the superseded question must no longer be answerable");
        assertTrue(GrabbyDestructionSessions.consume(PLAYER, second.sessionId(), now).isPresent());
    }

    // ------------------------------------------------------------------
    // The world must not have moved
    // ------------------------------------------------------------------

    @Test
    void aChangedBlockInvalidatesTheAnswer() {
        GrabbyDestructionSession session = issue();

        assertTrue(session.stateUnchanged(state));
        assertFalse(session.stateUnchanged(Blocks.STONE.defaultBlockState()),
                "the object asked about is not the object now standing there");
        assertFalse(session.stateUnchanged(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void aSwappedAxeInvalidatesTheAnswer() {
        GrabbyDestructionSession session = issue();

        assertTrue(session.toolUnchanged(axe()));
        assertFalse(session.toolUnchanged(new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(session.toolUnchanged(ItemStack.EMPTY),
                "putting the axe away should abandon the question, not complete it");
    }

    @Test
    void anAxeWithDifferentComponentsCountsAsADifferentAxe() {
        GrabbyDestructionSession session = issue();
        ItemStack named = axe();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Seggellion's Axe"));

        assertFalse(session.toolUnchanged(named));
    }

    @Test
    void theSessionSnapshotsRatherThanReferencesTheTool() {
        ItemStack held = axe();
        GrabbyDestructionSession session = GrabbyDestructionSessions.issue(PLAYER, POS, state, held, now);

        held.setCount(0);

        assertFalse(session.expectedTool().isEmpty(),
                "mutating the player's stack afterwards must not rewrite what they were asked about");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    void invalidatingAPlayerDropsTheirOutstandingQuestion() {
        GrabbyDestructionSession session = issue();

        GrabbyDestructionSessions.invalidate(PLAYER);

        assertTrue(GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), now).isEmpty());
    }

    @Test
    void clearingDropsEverything() {
        issue();
        GrabbyDestructionSessions.issue(SOMEBODY_ELSE, POS, state, axe(), now);

        GrabbyDestructionSessions.clear();

        assertEquals(0, outstandingSessions());
    }

    @Test
    void answeringDoesNotLeaveTheSessionBehind() {
        GrabbyDestructionSession session = issue();
        GrabbyDestructionSessions.consume(PLAYER, session.sessionId(), now);
        assertEquals(0, outstandingSessions());
    }

    @Test
    void aNullPlayerOrSessionIsRejectedRatherThanThrowing() {
        issue();
        assertEquals(Optional.empty(), GrabbyDestructionSessions.consume(null, UUID.randomUUID(), now));
        assertEquals(Optional.empty(), GrabbyDestructionSessions.consume(PLAYER, null, now));
    }

    @Test
    void aSessionMustExpireAfterItWasIssued() {
        assertThrows(IllegalArgumentException.class, () -> new GrabbyDestructionSession(
                UUID.randomUUID(), PLAYER, POS, state, axe(), now, now));
    }

    /** Package-private probe on the real registry, so these assertions cannot pass vacuously. */
    private static int outstandingSessions() {
        return GrabbyDestructionSessions.outstanding();
    }
}
