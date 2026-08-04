package com.seggellion.britannia_mod.structure.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeStructurePersistenceTest {
    private static final List<LocalOffset> FOOTPRINT = List.of(
            LocalOffset.ANCHOR, new LocalOffset(1, 0, 0),
            new LocalOffset(0, 0, 1), new LocalOffset(1, 0, 1));

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void diskSaveLoadPreservesSchemaIdentityFacingAndExactOrderedFootprint() {
        PlacedStructureState expected = state("shrine", "honesty", Direction.WEST);
        LargeStructureAnchorBlockEntity source = entity(Direction.WEST);
        assertTrue(source.initialize(expected));
        CompoundTag saved = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        CompoundTag encoded = saved.getCompound(LargeStructureAnchorBlockEntity.STATE_TAG);
        assertEquals(ShrineItemState.CURRENT_SCHEMA_VERSION, encoded.getInt("schema_version"));
        assertEquals("shrine", encoded.getString("family_id"));
        assertEquals("honesty", encoded.getString("variant_id"));
        assertEquals("west", encoded.getString("facing"));
        assertEquals(4, encoded.getList("placed_footprint", Tag.TAG_COMPOUND).size());

        LargeStructureAnchorBlockEntity decoded = entity(Direction.WEST);
        decoded.loadWithComponents(saved, RegistryAccess.EMPTY);
        assertEquals(expected, decoded.placedState().orElseThrow());
        assertEquals(PlacedStructureStatus.VALID, decoded.structuralStatus());
    }

    @Test
    void updateTagAndInitialChunkApplicationPreserveAllFields() {
        PlacedStructureState expected = state("missing_family", "missing_variant", Direction.SOUTH);
        LargeStructureAnchorBlockEntity source = entity(Direction.SOUTH);
        assertTrue(source.initialize(expected));
        CompoundTag updateTag = source.getUpdateTag(RegistryAccess.EMPTY);
        LargeStructureAnchorBlockEntity receiver = entity(Direction.SOUTH);
        receiver.handleUpdateTag(updateTag, RegistryAccess.EMPTY);
        assertEquals(expected, receiver.placedState().orElseThrow());
        assertEquals(updateTag, receiver.getUpdateTag(RegistryAccess.EMPTY));
    }

    @Test
    void actualBlockEntityPacketPathAppliesEqualStateOnReceivingEntity() throws Exception {
        PlacedStructureState expected = state("shrine", "missing_variant", Direction.EAST);
        LargeStructureAnchorBlockEntity source = entity(Direction.EAST);
        assertTrue(source.initialize(expected));
        CompoundTag updateTag = source.getUpdateTag(RegistryAccess.EMPTY);
        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                source.getBlockPos(), source.getType(), updateTag.copy());
        assertEquals(updateTag, packet.getTag());
        LargeStructureAnchorBlockEntity receiver = entity(Direction.EAST);
        receiver.onDataPacket(null, packet, RegistryAccess.EMPTY);
        assertEquals(expected, receiver.placedState().orElseThrow());
    }

    @Test
    void missingFamilyAndVariantStatusesNeverEraseStableIds() {
        LargeStructureAnchorBlockEntity missingFamily = entity(Direction.NORTH);
        assertTrue(missingFamily.initialize(state("lost_family", "honesty", Direction.NORTH)));
        assertEquals(PlacedStructureStatus.MISSING_FAMILY_DEFINITION,
                missingFamily.status(ShrineMonolithDefinitions.catalogue()));
        assertEquals("lost_family", missingFamily.placedState().orElseThrow().familyId().value());

        LargeStructureAnchorBlockEntity missingVariant = entity(Direction.NORTH);
        assertTrue(missingVariant.initialize(state("shrine", "lost_variant", Direction.NORTH)));
        assertEquals(PlacedStructureStatus.MISSING_VARIANT_DEFINITION,
                missingVariant.status(ShrineMonolithDefinitions.catalogue()));
        assertEquals("lost_variant", missingVariant.placedState().orElseThrow().variantId().value());
    }

    @Test
    void malformedUnsupportedAndStructurallyInvalidNbtFailSafely() {
        LargeStructureAnchorBlockEntity malformed = entity(Direction.NORTH);
        CompoundTag malformedTag = new CompoundTag();
        malformedTag.putString(LargeStructureAnchorBlockEntity.STATE_TAG, "not_a_record");
        malformed.loadWithComponents(malformedTag, RegistryAccess.EMPTY);
        assertEquals(PlacedStructureStatus.MALFORMED, malformed.structuralStatus());
        assertTrue(malformed.placedState().isEmpty());

        CompoundTag valid = saved(state("shrine", "honesty", Direction.NORTH));
        valid.getCompound(LargeStructureAnchorBlockEntity.STATE_TAG).putInt("schema_version", 999);
        LargeStructureAnchorBlockEntity future = entity(Direction.NORTH);
        future.loadWithComponents(valid, RegistryAccess.EMPTY);
        assertEquals(PlacedStructureStatus.UNSUPPORTED_FUTURE_SCHEMA, future.structuralStatus());
        assertTrue(future.placedState().isEmpty());

        CompoundTag invalidFootprint = saved(state("shrine", "honesty", Direction.NORTH));
        invalidFootprint.getCompound(LargeStructureAnchorBlockEntity.STATE_TAG)
                .getList("placed_footprint", Tag.TAG_COMPOUND).remove(3);
        LargeStructureAnchorBlockEntity invalid = entity(Direction.NORTH);
        invalid.loadWithComponents(invalidFootprint, RegistryAccess.EMPTY);
        assertEquals(PlacedStructureStatus.STRUCTURALLY_INVALID, invalid.structuralStatus());
        assertTrue(invalid.placedState().isEmpty());
    }

    @Test
    void blockStateFacingIsAuthoritativeAndPersistedMismatchIsRejected() {
        CompoundTag north = saved(state("shrine", "honesty", Direction.NORTH));
        LargeStructureAnchorBlockEntity westBlockState = entity(Direction.WEST);
        westBlockState.loadWithComponents(north, RegistryAccess.EMPTY);
        assertEquals(PlacedStructureStatus.STRUCTURALLY_INVALID, westBlockState.structuralStatus());
        assertTrue(westBlockState.placedState().isEmpty());
    }

    @Test
    void uninitializedDiagnosticAnchorDoesNotInventHonestyOrFootprint() {
        LargeStructureAnchorBlockEntity entity = entity(Direction.NORTH);
        entity.loadWithComponents(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(PlacedStructureStatus.UNINITIALIZED, entity.structuralStatus());
        assertTrue(entity.placedState().isEmpty());
        assertFalse(entity.saveWithoutMetadata(RegistryAccess.EMPTY)
                .contains(LargeStructureAnchorBlockEntity.STATE_TAG));
    }

    @Test
    void persistedFootprintSnapshotSurvivesLaterDefinitionShapeChanges() {
        PlacedStructureState original = state("shrine", "honesty", Direction.NORTH);
        CompoundTag saved = saved(original);
        List<LocalOffset> hypotheticalChangedDefinition = List.of(
                LocalOffset.ANCHOR, new LocalOffset(2, 0, 0), new LocalOffset(2, 0, 1));
        assertFalse(hypotheticalChangedDefinition.equals(original.footprint()));
        LargeStructureAnchorBlockEntity reloaded = entity(Direction.NORTH);
        reloaded.loadWithComponents(saved, RegistryAccess.EMPTY);
        assertEquals(FOOTPRINT, reloaded.placedState().orElseThrow().footprint());
    }

    @Test
    void compareAndSetVariantPersistsThroughDiskUpdateTagAndPacketPaths() throws Exception {
        PlacedStructureState honesty = state("shrine", "honesty", Direction.SOUTH);
        PlacedStructureState compassion = state("shrine", "compassion", Direction.SOUTH);
        LargeStructureAnchorBlockEntity source = entity(Direction.SOUTH);
        assertTrue(source.initialize(honesty));
        assertTrue(source.replacePlacedState(honesty, compassion));
        assertEquals(compassion, source.placedState().orElseThrow());

        CompoundTag disk = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        assertEquals("compassion", disk.getCompound(LargeStructureAnchorBlockEntity.STATE_TAG)
                .getString("variant_id"));
        LargeStructureAnchorBlockEntity diskReceiver = entity(Direction.SOUTH);
        diskReceiver.loadWithComponents(disk, RegistryAccess.EMPTY);
        assertEquals(compassion, diskReceiver.placedState().orElseThrow());

        CompoundTag update = source.getUpdateTag(RegistryAccess.EMPTY);
        LargeStructureAnchorBlockEntity tagReceiver = entity(Direction.SOUTH);
        tagReceiver.handleUpdateTag(update, RegistryAccess.EMPTY);
        assertEquals(compassion, tagReceiver.placedState().orElseThrow());

        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                source.getBlockPos(), source.getType(), update.copy());
        LargeStructureAnchorBlockEntity packetReceiver = entity(Direction.SOUTH);
        packetReceiver.onDataPacket(null, packet, RegistryAccess.EMPTY);
        assertEquals(compassion, packetReceiver.placedState().orElseThrow());
    }

    @Test
    void compareAndSetRejectsStaleOrNonVariantStructuralChanges() {
        PlacedStructureState honesty = state("shrine", "honesty", Direction.NORTH);
        LargeStructureAnchorBlockEntity source = entity(Direction.NORTH);
        assertTrue(source.initialize(honesty));
        assertFalse(source.replacePlacedState(state("shrine", "valor", Direction.NORTH),
                state("shrine", "compassion", Direction.NORTH)));
        assertFalse(source.replacePlacedState(honesty,
                state("monolith", "compassion", Direction.NORTH)));
        assertFalse(source.replacePlacedState(honesty,
                state("shrine", "compassion", Direction.EAST)));
        assertEquals(honesty, source.placedState().orElseThrow());
    }

    @Test
    void eighteenCellMonolithRoundTripsDiskUpdateTagAndActualPacketWithoutCatalogueResize()
            throws Exception {
        List<LocalOffset> footprint = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow().footprint();
        PlacedStructureState expected = new PlacedStructureState(
                ShrineItemState.CURRENT_SCHEMA_VERSION,
                ShrineMonolithDefinitions.MONOLITH,
                new VariantId("diagnostic_missing_content"), Direction.WEST, footprint);
        LargeStructureAnchorBlockEntity source = entity(Direction.WEST);
        assertTrue(source.initialize(expected));
        CompoundTag disk = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        assertEquals(18, disk.getCompound(LargeStructureAnchorBlockEntity.STATE_TAG)
                .getList("placed_footprint", Tag.TAG_COMPOUND).size());

        LargeStructureAnchorBlockEntity diskReceiver = entity(Direction.WEST);
        diskReceiver.loadWithComponents(disk, RegistryAccess.EMPTY);
        assertEquals(expected, diskReceiver.placedState().orElseThrow());

        CompoundTag update = source.getUpdateTag(RegistryAccess.EMPTY);
        LargeStructureAnchorBlockEntity tagReceiver = entity(Direction.WEST);
        tagReceiver.handleUpdateTag(update, RegistryAccess.EMPTY);
        assertEquals(expected, tagReceiver.placedState().orElseThrow());

        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                source.getBlockPos(), source.getType(), update.copy());
        LargeStructureAnchorBlockEntity packetReceiver = entity(Direction.WEST);
        packetReceiver.onDataPacket(null, packet, RegistryAccess.EMPTY);
        assertEquals(expected, packetReceiver.placedState().orElseThrow());
        assertEquals(footprint, packetReceiver.placedState().orElseThrow().footprint());
    }

    private static CompoundTag saved(PlacedStructureState state) {
        LargeStructureAnchorBlockEntity entity = entity(state.facing());
        assertTrue(entity.initialize(state));
        return entity.saveWithoutMetadata(RegistryAccess.EMPTY);
    }

    private static PlacedStructureState state(String family, String variant, Direction facing) {
        return new PlacedStructureState(ShrineItemState.CURRENT_SCHEMA_VERSION,
                new FamilyId(family), new VariantId(variant), facing, FOOTPRINT);
    }

    private static LargeStructureAnchorBlockEntity entity(Direction facing) {
        return new LargeStructureAnchorBlockEntity(
                MilestoneTwoRegisteredTestContent.blockEntityType(), BlockPos.ZERO,
                MilestoneTwoRegisteredTestContent.anchor().defaultBlockState()
                        .setValue(LargeStructureAnchorBlock.FACING, facing));
    }
}
