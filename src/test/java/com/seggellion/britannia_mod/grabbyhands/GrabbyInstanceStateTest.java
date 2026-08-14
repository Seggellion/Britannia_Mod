package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrabbyInstanceStateTest {
    private static final UUID PLACER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    // ------------------------------------------------------------------
    // The no-migration guarantee
    // ------------------------------------------------------------------

    @Test
    void aBlockEntityTagWithNoGrabbyMarkReadsAsWorldPlacedAndIsNotGrabbyManaged() {
        // This is the whole reason the mark is positive: every chair, table, bottle and chest already
        // saved in every existing world hits this path and comes out protected, with nothing rewritten.
        GrabbyInstanceState state = GrabbyInstanceState.read(new CompoundTag());

        assertEquals(GrabbyProvenance.WORLD, state.provenance());
        assertFalse(state.grabbyManaged());
        assertTrue(state.placerUuid().isEmpty());
    }

    @Test
    void aNullHostTagAlsoReadsAsWorldPlaced() {
        assertEquals(GrabbyProvenance.WORLD, GrabbyInstanceState.read(null).provenance());
    }

    @Test
    void writingWorldProvenanceLeavesNoKeyBehindSoUntouchedSavesStayIdentical() {
        CompoundTag host = new CompoundTag();
        host.putString("SomeExistingField", "untouched");

        GrabbyInstanceState.worldPlaced().write(host);

        assertFalse(host.contains(GrabbyInstanceState.TAG_KEY));
        assertEquals("untouched", host.getString("SomeExistingField"));
    }

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    void playerPlacedStateSurvivesAFullNbtRoundTripExactly() {
        GrabbyInstanceState original = GrabbyInstanceState.playerPlaced(PLACER, 123456L);

        CompoundTag host = new CompoundTag();
        original.write(host);
        GrabbyInstanceState restored = GrabbyInstanceState.read(host);

        assertEquals(original, restored);
        assertEquals(GrabbyProvenance.PLAYER, restored.provenance());
        assertEquals(Optional.of(PLACER), restored.placerUuid());
        assertEquals(123456L, restored.placedAtGameTime());
        assertTrue(restored.grabbyManaged());
    }

    @Test
    void repeatedRoundTripsAreStable() {
        GrabbyInstanceState state = GrabbyInstanceState.playerPlaced(PLACER, 7L);
        for (int cycle = 0; cycle < 3; cycle++) {
            CompoundTag host = new CompoundTag();
            state.write(host);
            state = GrabbyInstanceState.read(host);
        }
        assertEquals(GrabbyInstanceState.playerPlaced(PLACER, 7L), state);
    }

    // ------------------------------------------------------------------
    // Client redaction
    // ------------------------------------------------------------------

    @Test
    void theClientFormOmitsThePlacerUuidButKeepsProvenance() {
        GrabbyInstanceState original = GrabbyInstanceState.playerPlaced(PLACER, 99L);

        CompoundTag clientTag = original.toClientTag();
        assertFalse(clientTag.hasUUID("PlacerUuid"));
        assertEquals(GrabbyProvenance.PLAYER.name(), clientTag.getString("Provenance"));

        CompoundTag fullTag = original.toTag();
        assertTrue(fullTag.hasUUID("PlacerUuid"));
    }

    @Test
    void aRedactedClientTagStillDecodesAndStaysGrabbyManaged() {
        // The client must be able to load what the server redacted. Provenance is what drives the
        // client-visible behaviour; the UUID is a server-side audit detail.
        CompoundTag host = new CompoundTag();
        GrabbyInstanceState.playerPlaced(PLACER, 42L).writeClient(host);

        GrabbyInstanceState restored = GrabbyInstanceState.read(host);

        assertEquals(GrabbyProvenance.PLAYER, restored.provenance());
        assertTrue(restored.grabbyManaged());
        assertTrue(restored.placerUuid().isEmpty());
    }

    // ------------------------------------------------------------------
    // Failing closed
    // ------------------------------------------------------------------

    @Test
    void anUnrecognisedProvenanceNameFallsBackToProtectedRatherThanMovable() {
        CompoundTag inner = new CompoundTag();
        inner.putInt("SchemaVersion", GrabbyInstanceState.CURRENT_SCHEMA_VERSION);
        inner.putString("Provenance", "SOMETHING_FROM_THE_FUTURE");
        inner.putLong("PlacedAtGameTime", 5L);
        CompoundTag host = new CompoundTag();
        host.put(GrabbyInstanceState.TAG_KEY, inner);

        assertEquals(GrabbyProvenance.WORLD, GrabbyInstanceState.read(host).provenance());
    }

    @Test
    void anUnsupportedSchemaVersionFallsBackToProtected() {
        for (int version : new int[]{0, -1, GrabbyInstanceState.CURRENT_SCHEMA_VERSION + 1}) {
            CompoundTag inner = new CompoundTag();
            inner.putInt("SchemaVersion", version);
            inner.putString("Provenance", GrabbyProvenance.PLAYER.name());
            inner.putUUID("PlacerUuid", PLACER);
            CompoundTag host = new CompoundTag();
            host.put(GrabbyInstanceState.TAG_KEY, inner);

            assertFalse(GrabbyInstanceState.read(host).grabbyManaged(), "schema version " + version);
        }
    }

    @Test
    void aWorldProvenanceStateCanNeverCarryAPlacer() {
        assertThrows(IllegalArgumentException.class, () -> new GrabbyInstanceState(
                GrabbyInstanceState.CURRENT_SCHEMA_VERSION,
                GrabbyProvenance.WORLD,
                Optional.of(PLACER),
                0L));
    }

    @Test
    void constructorRejectsUnsupportedVersionsAndNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () -> new GrabbyInstanceState(
                0, GrabbyProvenance.PLAYER, Optional.of(PLACER), 0L));
        assertThrows(IllegalArgumentException.class, () -> new GrabbyInstanceState(
                GrabbyInstanceState.CURRENT_SCHEMA_VERSION, GrabbyProvenance.PLAYER, Optional.of(PLACER), -1L));
    }

    @Test
    void playerPlacedRequiresAnActualPlacer() {
        assertThrows(NullPointerException.class, () -> GrabbyInstanceState.playerPlaced(null, 0L));
    }
}
