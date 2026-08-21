package com.seggellion.britannia_mod.resource.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 4: a deposit's identity, and the one derivation from it to a planner seed.
 *
 * <p>Pure JUnit — identity is a string and a hash, with no Minecraft anywhere in it, which is what
 * lets these run as ordinary unit tests.
 */
class DepositIdentityTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    /* ------------------------------------------------------------------ */
    /*  Natural                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * The rule natural generation will use, defined and tested before there is any natural
     * generation, so that when it arrives it inherits an identity contract rather than inventing
     * one.
     */
    @Test
    void naturalIdentityIsStableForIdenticalInputs() {
        long first = DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 4, -7, 0);
        long second = DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 4, -7, 0);
        assertEquals(first, second);
    }

    @Test
    void everyNaturalInputChangesTheIdentity() {
        long base = DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 4, -7, 0);
        Set<Long> ids = new HashSet<>();
        ids.add(base);
        ids.add(DepositIdentity.natural(999L, OVERWORLD, "britannia_mod:silver", 4, -7, 0));
        ids.add(DepositIdentity.natural(12345L, NETHER, "britannia_mod:silver", 4, -7, 0));
        ids.add(DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:tin", 4, -7, 0));
        ids.add(DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 5, -7, 0));
        ids.add(DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 4, -8, 0));
        ids.add(DepositIdentity.natural(12345L, OVERWORLD, "britannia_mod:silver", 4, -7, 1));
        assertEquals(7, ids.size(), "world seed, dimension, resource, cell and salt must all matter");
    }

    /* ------------------------------------------------------------------ */
    /*  Rails                                                              */
    /* ------------------------------------------------------------------ */

    /** The same curated row derives the same id, in this session and in any later one. */
    @Test
    void aCuratedRowAlwaysDerivesTheSameIdentity() {
        long first = DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver",
                120, -50, 100, 50, ShapeRotation.ZW, "britain");
        long second = DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver",
                120, -50, 100, 50, ShapeRotation.ZW, "britain");
        assertEquals(first, second, "a curated row must survive a restart as the same deposit");
    }

    @Test
    void everyRailsRowInputChangesTheIdentity() {
        Set<Long> ids = new HashSet<>();
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("other", OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", NETHER, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:tin", 120, -50, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 121, -50, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -49, 100, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -50, 101, 50, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -50, 100, 51, ShapeRotation.ZW, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.XZ, "britain"));
        ids.add(DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 120, -50, 100, 50, ShapeRotation.ZW, "minoc"));
        assertEquals(10, ids.size(), "two different curated rows must never share an identity");
    }

    /**
     * Identity depends on the row and on nothing else.
     *
     * <p>The failure this rules out is the one milestone 3 found in the shapes: a derivation that
     * quietly folded in the current time would make every import a new deposit, which is exactly
     * what makes an importer non-idempotent.
     */
    @Test
    void railsIdentityDoesNotDependOnWhenItIsAsked() throws Exception {
        long before = DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:gold",
                0, 0, 0, 20, ShapeRotation.XZ, "");
        Thread.sleep(5);
        long after = DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:gold",
                0, 0, 0, 20, ShapeRotation.XZ, "");
        assertEquals(before, after);
    }

    /**
     * The canonical encoding is an explicit, versioned string rather than an object's formatting.
     *
     * <p>A record's generated {@code toString()} is not a serialization contract: reordering two
     * fields would silently change every id in every world. Pinning the exact string here means
     * such a change fails a test instead of a world.
     */
    @Test
    void theCanonicalEncodingIsExplicitAndVersioned() {
        String encoded = DepositIdentity.railsEncoding("Britannia", OVERWORLD, "britannia_mod:silver",
                120, -50, 100, 50, ShapeRotation.ZW, "Britain");
        assertEquals("v1|rails|britannia|minecraft:overworld|britannia_mod:silver|120,-50,100|50|ZW|britain",
                encoded);
        assertEquals(1, DepositIdentity.VERSION);
    }

    /** Spelling variations of the same row must not become two deposits. */
    @Test
    void encodingNormalisesCaseAndAbsentComponents() {
        assertEquals(
                DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver", 1, 2, 3, 10, ShapeRotation.XZ, ""),
                DepositIdentity.rails("BRITANNIA", OVERWORLD, "britannia_mod:silver", 1, 2, 3, 10, ShapeRotation.XZ, null),
                "case and a null region must not fork the identity");
        assertTrue(DepositIdentity.railsEncoding(null, null, null, 0, 0, 0, 0, null, null)
                .startsWith("v1|rails|"), "absent components collapse rather than throwing");
    }

    /* ------------------------------------------------------------------ */
    /*  Admin and retrofit                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * An operator deposit has nothing immutable to derive from, so its id is minted once.
     *
     * <p>The same minted UUID always yields the same id — which is what makes persisting the UUID
     * enough — while two mintings are two deposits, because an operator placing the same thing
     * twice means two of them.
     */
    @Test
    void adminIdentityIsStableForAMintedUuidAndUniquePerMinting() {
        UUID minted = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(
                DepositIdentity.admin(minted, OVERWORLD, "britannia_mod:clay_deposit"),
                DepositIdentity.admin(minted, OVERWORLD, "britannia_mod:clay_deposit"));
        assertNotEquals(
                DepositIdentity.admin(minted, OVERWORLD, "britannia_mod:clay_deposit"),
                DepositIdentity.admin(UUID.randomUUID(), OVERWORLD, "britannia_mod:clay_deposit"));
    }

    /** The future retrofit contract: re-running a batch adopts the same terrain as the same deposits. */
    @Test
    void retrofitIdentityIsDeterministicPerBatchAndOrigin() {
        assertEquals(
                DepositIdentity.retrofit("batch-1", OVERWORLD, "britannia_mod:tin", 10, 20, 30),
                DepositIdentity.retrofit("batch-1", OVERWORLD, "britannia_mod:tin", 10, 20, 30));
        assertNotEquals(
                DepositIdentity.retrofit("batch-1", OVERWORLD, "britannia_mod:tin", 10, 20, 30),
                DepositIdentity.retrofit("batch-2", OVERWORLD, "britannia_mod:tin", 10, 20, 30));
        assertNotEquals(
                DepositIdentity.retrofit("batch-1", OVERWORLD, "britannia_mod:tin", 10, 20, 30),
                DepositIdentity.retrofit("batch-1", OVERWORLD, "britannia_mod:tin", 11, 20, 30));
    }

    /* ------------------------------------------------------------------ */
    /*  Sources are kept apart, and the seed derives from the id           */
    /* ------------------------------------------------------------------ */

    /** Two sources describing the same place are still two different deposits. */
    @Test
    void differentSourcesNeverCollide() {
        Set<Long> ids = new HashSet<>();
        ids.add(DepositIdentity.rails("", OVERWORLD, "britannia_mod:tin", 1, 2, 3, 10, ShapeRotation.XZ, ""));
        ids.add(DepositIdentity.retrofit("", OVERWORLD, "britannia_mod:tin", 1, 2, 3));
        ids.add(DepositIdentity.natural(0L, OVERWORLD, "britannia_mod:tin", 1, 3, 0));
        assertEquals(3, ids.size(), "the source is part of the encoding for exactly this reason");
    }

    /** One contract, two values: the seed is derived from the id, not computed beside it. */
    @Test
    void thePlannerSeedDerivesFromTheInstanceId() {
        long id = DepositIdentity.rails("britannia", OVERWORLD, "britannia_mod:silver",
                120, -50, 100, 50, ShapeRotation.ZW, "britain");
        assertEquals(DepositIdentity.plannerSeed(id), DepositIdentity.plannerSeed(id),
                "the derivation must be a function, not a generator");
        assertNotEquals(0L, DepositIdentity.plannerSeed(id));
    }

    /** Zero means "no deposit", so no identity may ever be it. */
    @Test
    void zeroIsReservedAndNeverProduced() {
        assertEquals(DepositInstance.NO_INSTANCE, 0L);
        // The reservation is enforced in the hash rather than hoped for.
        assertNotEquals(DepositInstance.NO_INSTANCE, DepositIdentity.hash64(""));
        for (int i = 0; i < 2000; i++) {
            assertNotEquals(DepositInstance.NO_INSTANCE,
                    DepositIdentity.rails("s", OVERWORLD, "britannia_mod:tin", i, 0, 0, 10, ShapeRotation.XZ, ""));
        }
    }
}
