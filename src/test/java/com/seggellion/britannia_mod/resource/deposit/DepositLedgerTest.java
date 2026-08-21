package com.seggellion.britannia_mod.resource.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 4: what the ledger does when the same deposit turns up twice.
 *
 * <p>The distinction these exist to pin is between <em>the same deposit again</em> — which must
 * resume, because milestone 3 made materialisation budget-bounded and resumable — and <em>a
 * different deposit wearing the same id</em>, which must be refused. A ledger that treated both as
 * "already seen, skip" would strand every partially written deposit permanently; one that treated
 * both as "overwrite" would lose one of two real deposits.
 */
class DepositLedgerTest {

    private static final long SEED = 0x5EEDL;
    private static final BlockPos ORIGIN = new BlockPos(120, -50, 100);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DepositInstance instance(
            long id, String resource, BlockPos origin, long seed, int radius, int revision) {
        return new DepositInstance(
                id, resource, revision, DepositSource.RAILS, "v1|rails|fixture",
                origin, seed, radius, ShapeRotation.XZ,
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius),
                500, DepositInstance.UNKNOWN_COUNT, DepositInstance.UNKNOWN_COUNT,
                DepositInstance.CURRENT_MATERIALIZATION_VERSION);
    }

    private static DepositInstance fixture() {
        return instance(0xABCDL, "britannia_mod:silver", ORIGIN, SEED, 20, 1);
    }

    /* ------------------------------------------------------------------ */
    /*  The three outcomes                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void aNewDepositIsRegisteredAndMayMaterialize() {
        DepositLedger ledger = new DepositLedger();
        DepositLedger.Registration registration = ledger.register(fixture());

        assertEquals(DepositLedger.Outcome.REGISTERED, registration.outcome());
        assertTrue(registration.mayMaterialize());
        assertEquals(1, ledger.size());
        assertTrue(ledger.byId(0xABCDL).isPresent());
    }

    /**
     * The same deposit again resumes rather than duplicating.
     *
     * <p>This is the case a naive "skip if seen" ledger would break. Milestone 3's materialisation
     * stops at a work budget and finishes on a later run, so a deposit that was half written must
     * be allowed to carry on.
     */
    @Test
    void theSameDepositAgainResumesRatherThanDuplicating() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());

        DepositLedger.Registration second = ledger.register(fixture());

        assertEquals(DepositLedger.Outcome.ALREADY_REGISTERED, second.outcome());
        assertTrue(second.mayMaterialize(), "a partially materialised deposit must be able to resume");
        assertEquals(1, ledger.size(), "no second instance may be created");
    }

    /** A partially materialised deposit keeps its progress and is still allowed to continue. */
    @Test
    void aPartiallyMaterializedDepositKeepsItsProgressAndMayContinue() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());
        ledger.recordProgress(0xABCDL, 120, 30);

        DepositInstance stored = ledger.byId(0xABCDL).orElseThrow();
        assertEquals(120, stored.materializedCells());
        assertEquals(30, stored.blockedCells());
        assertFalse(stored.fullyMaterialized(), "150 of 500 is not finished");

        assertTrue(ledger.register(fixture()).mayMaterialize());
        assertEquals(120, ledger.byId(0xABCDL).orElseThrow().materializedCells(),
                "re-registering must not reset what is already known");
    }

    /** Every piece of immutable metadata is part of what "the same deposit" means. */
    @Test
    void anyDisagreementOnImmutableMetadataIsRefusedAsACollision() {
        List<DepositInstance> impostors = List.of(
                instance(0xABCDL, "britannia_mod:tin", ORIGIN, SEED, 20, 1),
                instance(0xABCDL, "britannia_mod:silver", ORIGIN.above(), SEED, 20, 1),
                instance(0xABCDL, "britannia_mod:silver", ORIGIN, SEED + 1, 20, 1),
                instance(0xABCDL, "britannia_mod:silver", ORIGIN, SEED, 21, 1));

        for (DepositInstance impostor : impostors) {
            DepositLedger ledger = new DepositLedger();
            ledger.register(fixture());

            DepositLedger.Registration registration = ledger.register(impostor);

            assertEquals(DepositLedger.Outcome.CONFLICT, registration.outcome(),
                    "expected a collision for " + impostor.describeIdentity());
            assertFalse(registration.mayMaterialize(), "a collision must not be written");
            assertTrue(registration.message().contains("refusing rather than overwriting"));
            assertEquals("britannia_mod:silver", ledger.byId(0xABCDL).orElseThrow().resourceId(),
                    "the deposit already there must be untouched");
        }
    }

    /** A different source with the same id is a different deposit too. */
    @Test
    void aDifferentSourceIsAlsoACollision() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());

        DepositInstance sameButAdmin = new DepositInstance(
                0xABCDL, "britannia_mod:silver", 1, DepositSource.ADMIN, "v1|admin|fixture",
                ORIGIN, SEED, 20, ShapeRotation.XZ,
                ORIGIN.offset(-20, -20, -20), ORIGIN.offset(20, 20, 20),
                500, DepositInstance.UNKNOWN_COUNT, DepositInstance.UNKNOWN_COUNT, 1);

        assertEquals(DepositLedger.Outcome.CONFLICT, ledger.register(sameButAdmin).outcome());
    }

    /* ------------------------------------------------------------------ */
    /*  Revision                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * A definition revision change keeps the deposit's identity and stops further materialisation.
     *
     * <p>Not a collision — it is the same deposit, and a definition file changing is expected. But
     * continuing would write a deposit half in one shape and half in another, so the conservative
     * answer is to notice, report, and leave reconciliation to milestone 8.
     */
    @Test
    void aRevisionChangeIsDetectedRatherThanSilentlyRegenerating() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());

        DepositLedger.Registration registration =
                ledger.register(instance(0xABCDL, "britannia_mod:silver", ORIGIN, SEED, 20, 2));

        assertEquals(DepositLedger.Outcome.REVISION_MISMATCH, registration.outcome());
        assertFalse(registration.mayMaterialize(),
                "a deposit must not be half-written under two different definitions");
        assertTrue(registration.message().contains("revision"));
        assertEquals(1, ledger.byId(0xABCDL).orElseThrow().definitionRevision(),
                "the deposit keeps the revision it was created under");
    }

    /* ------------------------------------------------------------------ */
    /*  Persistence                                                        */
    /* ------------------------------------------------------------------ */

    /** Everything a deposit is survives a save and a load. */
    @Test
    void theLedgerRoundTripsThroughNbt() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());
        ledger.register(instance(0x1234L, "britannia_mod:tin", new BlockPos(-500, 30, 900), 9L, 12, 1));
        ledger.recordProgress(0xABCDL, 400, 100);

        DepositLedger reloaded = new DepositLedger(ledger.save(new CompoundTag(), null), null);

        assertEquals(2, reloaded.size());
        DepositInstance silver = reloaded.byId(0xABCDL).orElseThrow();
        assertEquals("britannia_mod:silver", silver.resourceId());
        assertEquals(DepositSource.RAILS, silver.source());
        assertEquals(ORIGIN, silver.origin());
        assertEquals(SEED, silver.seed());
        assertEquals(20, silver.radius());
        assertEquals(ShapeRotation.XZ, silver.rotation());
        assertEquals(500, silver.plannedCells());
        assertEquals(400, silver.materializedCells());
        assertEquals(100, silver.blockedCells());
        assertTrue(silver.fullyMaterialized(), "400 + 100 of 500 is finished");
    }

    /**
     * The same deposit after a restart is still the same deposit.
     *
     * <p>Registering it again against the reloaded ledger must resume, not duplicate — which is the
     * whole point of the identity being derived from immutable inputs rather than minted per run.
     */
    @Test
    void aDepositSurvivesRestartWithItsIdentityAndStillResumes() {
        DepositLedger before = new DepositLedger();
        before.register(fixture());
        before.recordProgress(0xABCDL, 200, 0);

        DepositLedger after = new DepositLedger(before.save(new CompoundTag(), null), null);
        DepositLedger.Registration registration = after.register(fixture());

        assertEquals(DepositLedger.Outcome.ALREADY_REGISTERED, registration.outcome());
        assertTrue(registration.mayMaterialize());
        assertEquals(1, after.size());
        assertEquals(200, after.byId(0xABCDL).orElseThrow().materializedCells());
    }

    /** Progress is unknown until a complete pass says otherwise. */
    @Test
    void progressIsUnknownUntilACompletePassEstablishesIt() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());

        DepositInstance fresh = ledger.byId(0xABCDL).orElseThrow();
        assertFalse(fresh.progressKnown(), "a deposit that has not been swept knows nothing yet");
        assertFalse(fresh.fullyMaterialized());
    }

    /* ------------------------------------------------------------------ */
    /*  Ownership lookup                                                   */
    /* ------------------------------------------------------------------ */

    /** A position inside a deposit's bounds resolves to it, by resource. */
    @Test
    void aPositionResolvesToTheDepositThatOwnsIt() {
        DepositLedger ledger = new DepositLedger();
        ledger.register(fixture());

        assertTrue(ledger.owning(ORIGIN, "britannia_mod:silver").isPresent());
        assertTrue(ledger.owning(ORIGIN.offset(5, 5, 5), "britannia_mod:silver").isPresent());
        assertTrue(ledger.owning(ORIGIN.offset(500, 0, 0), "britannia_mod:silver").isEmpty(),
                "outside the bounds is not owned");
        assertTrue(ledger.owning(ORIGIN, "britannia_mod:tin").isEmpty(),
                "a different resource in the same box is not this deposit");
    }

    /** The lookup is chunk-indexed, and the index survives a reload. */
    @Test
    void theChunkIndexIsRebuiltOnLoad() {
        DepositLedger before = new DepositLedger();
        before.register(fixture());

        DepositLedger after = new DepositLedger(before.save(new CompoundTag(), null), null);

        assertTrue(after.owning(ORIGIN, "britannia_mod:silver").isPresent(),
                "the chunk index must be rebuilt from the stored bounds");
        List<ChunkPos> touched = after.byId(0xABCDL).orElseThrow().touchedChunks();
        assertTrue(touched.contains(new ChunkPos(ORIGIN)), "its own chunk must be in the footprint");
    }
}
