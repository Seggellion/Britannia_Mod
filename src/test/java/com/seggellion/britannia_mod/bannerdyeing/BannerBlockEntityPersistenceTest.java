package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntityStatus;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.placement.BannerBlockItemTransfer;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerBlockEntityPersistenceTest {
    private static final BannerDefinitionId SMALL = BannerDefinitionId.parse("britannia_mod:silver_and_gold_pennon");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static RegistrySnapshot production;
    private static BannerItem item;
    private static BannerInstanceState natural;
    private static BannerInstanceState dyed;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        item = Milestone7RegisteredTestContent.banner();
        BannerItemFactory factory = new BannerItemFactory(item, item.stateAccess());
        natural = item.stateAccess().read(factory.naturalCottonAdminBanner(SMALL, production, true)
                .stack().orElseThrow()).orElseThrow();
        dyed = new BannerInstanceState(natural.schemaVersion(), natural.bannerDefinitionId(), natural.materialId(),
                ResolvedColourId.parse("britannia_mod:cotton_red"),
                Optional.of(PigmentId.parse("britannia_mod:madder_red")), natural.mountId());
    }

    @Test
    void naturalAndDyedStateRoundTripThroughCompleteCodecNbt() {
        assertRoundTrip(natural);
        assertRoundTrip(dyed);
        BannerInstanceState absentPigment = new BannerInstanceState(
                dyed.schemaVersion(), dyed.bannerDefinitionId(), dyed.materialId(), dyed.resolvedColourId(),
                Optional.empty(), dyed.mountId());
        assertRoundTrip(absentPigment);
    }

    @Test
    void saveDataContainsVersionedCompleteStateAndNoDisplayProjection() {
        BannerBlockEntity entity = entity();
        assertTrue(entity.setBannerState(dyed));
        CompoundTag saved = entity.saveWithoutMetadata(RegistryAccess.EMPTY);
        assertTrue(saved.contains(BannerBlockEntity.STATE_TAG));
        assertTrue(saved.contains(BannerBlockEntity.PLACEMENT_TAG));
        String encoded = saved.get(BannerBlockEntity.STATE_TAG).toString();
        assertTrue(encoded.contains("schema_version"));
        assertTrue(encoded.contains("banner_definition_id"));
        assertTrue(encoded.contains("material_id"));
        assertTrue(encoded.contains("resolved_colour_id"));
        assertTrue(encoded.contains("source_pigment_id"));
        assertTrue(encoded.contains("mount_id"));
        assertFalse(encoded.contains("display_srgb"));
        assertFalse(encoded.contains("render"));
    }

    @Test
    void milestoneTenSaveWithoutPlacementMigratesToOneCellAndWritesCurrentSchema() {
        BannerBlockEntity source = entity();
        source.setBannerState(dyed);
        CompoundTag legacy = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        legacy.remove(BannerBlockEntity.PLACEMENT_TAG);

        BannerBlockEntity migrated = entity();
        migrated.loadWithComponents(legacy, RegistryAccess.EMPTY);
        assertEquals(dyed, migrated.bannerState().orElseThrow());
        assertEquals(BannerPlacedStructure.legacyOneCell(), migrated.placedStructure().orElseThrow());
        assertTrue(migrated.migratedLegacyPlacement());
        assertTrue(migrated.saveWithoutMetadata(RegistryAccess.EMPTY).contains(BannerBlockEntity.PLACEMENT_TAG));
    }

    @Test
    void multiCellPlacementRoundTripsThroughSaveUpdateTagAndPacketData() {
        for (BannerOrientation orientation : BannerOrientation.values()) {
            BannerPlacedStructure structure = BannerPlacedStructure.fromFootprint(
                    orientation, BannerFootprint.fromDimensions(new BannerDimensions(3, 2, true)).footprint());
            BannerBlockEntity entity = entity();
            assertTrue(entity.setPlacedState(dyed, structure));
            BannerBlockEntity decoded = entity();
            decoded.loadWithComponents(entity.saveWithoutMetadata(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
            assertEquals(dyed, decoded.bannerState().orElseThrow());
            assertEquals(structure, decoded.placedStructure().orElseThrow());

            BannerBlockEntity updated = entity();
            updated.handleUpdateTag(entity.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
            assertEquals(structure, updated.placedStructure().orElseThrow());
        }
    }

    @Test
    void unknownFuturePlacementSchemaIsRejectedWithoutErasingBannerState() {
        BannerBlockEntity source = entity();
        source.setBannerState(dyed);
        CompoundTag saved = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        saved.getCompound(BannerBlockEntity.PLACEMENT_TAG).putInt("schema_version", 999);
        BannerBlockEntity decoded = entity();
        decoded.loadWithComponents(saved, RegistryAccess.EMPTY);
        assertEquals(dyed, decoded.bannerState().orElseThrow());
        assertTrue(decoded.placedStructure().isEmpty());
        assertEquals(BannerBlockEntityStatus.STRUCTURALLY_INVALID, decoded.status(production, true));
    }

    @Test
    void updateTagAndPacketApplyTheSameCompleteStateOnClientBoundary() throws Exception {
        BannerBlockEntity server = entity();
        server.setBannerState(dyed);
        CompoundTag updateTag = server.getUpdateTag(RegistryAccess.EMPTY);
        BannerBlockEntity tagClient = entity();
        tagClient.handleUpdateTag(updateTag, RegistryAccess.EMPTY);
        assertEquals(dyed, tagClient.bannerState().orElseThrow());

        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                server.getBlockPos(), server.getType(), updateTag.copy());
        BannerBlockEntity packetClient = entity();
        packetClient.onDataPacket(null, packet, RegistryAccess.EMPTY);
        assertEquals(dyed, packetClient.bannerState().orElseThrow());
        assertEquals(updateTag, packet.getTag());
        assertEquals(dyed, server.bannerState().orElseThrow());
    }

    @Test
    void structurallyInvalidAndMissingStateFailSafelyWithoutInventingState() {
        BannerBlockEntity invalid = entity();
        CompoundTag malformed = new CompoundTag();
        malformed.putString(BannerBlockEntity.STATE_TAG, "not_a_record");
        invalid.loadWithComponents(malformed, RegistryAccess.EMPTY);
        assertEquals(BannerBlockEntityStatus.STRUCTURALLY_INVALID,
                invalid.status(production, true));
        assertTrue(invalid.bannerState().isEmpty());
        ItemStack diagnostic = BannerBlockItemTransfer.create(item,
                Milestone7RegisteredTestContent.component(), invalid.bannerState());
        assertEquals(item, diagnostic.getItem());
        assertTrue(item.stateAccess().read(diagnostic).isEmpty());

        BannerBlockEntity unconfigured = entity();
        unconfigured.loadWithComponents(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(BannerBlockEntityStatus.UNCONFIGURED, unconfigured.status(production, true));
    }

    @Test
    void registryAbsenceAndRemovedReferencesNeverEraseStoredStableIds() {
        BannerBlockEntity entity = entity();
        entity.setBannerState(dyed);
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES,
                entity.status(RegistrySnapshot.empty(), false));

        RegistrySnapshot noDefinition = RegistrySnapshotTestFactory.withoutBanner(production, SMALL);
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES, entity.status(noDefinition, true));
        assertEquals(dyed, entity.bannerState().orElseThrow());

        RegistrySnapshot noMaterial = RegistrySnapshotTestFactory.withoutMaterial(production, COTTON);
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES, entity.status(noMaterial, true));
        RegistrySnapshot noPalette = RegistrySnapshotTestFactory.withoutPalette(
                production, production.fabricMaterials().require(COTTON).paletteId());
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES, entity.status(noPalette, true));
        RegistrySnapshot noPigment = RegistrySnapshotTestFactory.withoutPigment(
                production, dyed.sourcePigmentId().orElseThrow());
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES, entity.status(noPigment, true));
        RegistrySnapshot noMount = RegistrySnapshotTestFactory.withoutMount(production, dyed.mountId());
        assertEquals(BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES, entity.status(noMount, true));
        assertEquals(dyed, entity.bannerState().orElseThrow());
    }

    @Test
    void breakingAndPickTransferUseSharedItemAndPreserveAllSixFieldsWithoutRegistryLookup() {
        ItemStack recovered = BannerBlockItemTransfer.create(
                item, Milestone7RegisteredTestContent.component(), Optional.of(dyed));
        assertEquals(item, recovered.getItem());
        assertEquals(1, recovered.getCount());
        assertEquals(dyed, item.stateAccess().read(recovered).orElseThrow());
        assertEquals(dyed.schemaVersion(), item.stateAccess().read(recovered).orElseThrow().schemaVersion());
        assertEquals(dyed.bannerDefinitionId(), item.stateAccess().read(recovered).orElseThrow().bannerDefinitionId());
        assertEquals(dyed.materialId(), item.stateAccess().read(recovered).orElseThrow().materialId());
        assertEquals(dyed.resolvedColourId(), item.stateAccess().read(recovered).orElseThrow().resolvedColourId());
        assertEquals(dyed.sourcePigmentId(), item.stateAccess().read(recovered).orElseThrow().sourcePigmentId());
        assertEquals(dyed.mountId(), item.stateAccess().read(recovered).orElseThrow().mountId());
    }

    private static void assertRoundTrip(BannerInstanceState state) {
        BannerBlockEntity original = entity();
        original.setBannerState(state);
        BannerBlockEntity decoded = entity();
        decoded.loadWithComponents(original.saveWithoutMetadata(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(state, decoded.bannerState().orElseThrow());
    }

    private static BannerBlockEntity entity() {
        return new BannerBlockEntity(BlockEntityType.BANNER, BlockPos.ZERO,
                Blocks.WHITE_WALL_BANNER.defaultBlockState());
    }
}
