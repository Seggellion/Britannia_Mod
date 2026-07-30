package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone13RenderFixtures;
import com.seggellion.britannia_mod.client.banner.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacedSynchronizationTest {
    @BeforeAll
    static void setup() {
        Milestone13RenderFixtures.ensureLoaded();
    }

    @Test
    void initialChunkTagAndTrackingPacketContainCompleteBannerAndPlacedStructure() throws Exception {
        BannerBlockEntity server = entity("large", "cotton", "brass",
                Direction.WEST, BannerOrientation.WALL_PARALLEL, 2, 2);
        CompoundTag tag = server.getUpdateTag(RegistryAccess.EMPTY);
        assertTrue(tag.contains(BannerBlockEntity.STATE_TAG));
        assertTrue(tag.contains(BannerBlockEntity.PLACEMENT_TAG));
        assertTrue(tag.get(BannerBlockEntity.STATE_TAG).toString().contains("resolved_colour_id"));
        assertTrue(tag.get(BannerBlockEntity.STATE_TAG).toString().contains("mount_id"));
        assertTrue(tag.get(BannerBlockEntity.PLACEMENT_TAG).toString().contains("occupied_offsets"));
        assertTrue(tag.get(BannerBlockEntity.PLACEMENT_TAG).toString().contains("wall_parallel"));

        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                server.getBlockPos(), server.getType(), tag.copy());
        BannerBlockEntity trackingClient = emptyLike(server);
        trackingClient.onDataPacket(null, packet, RegistryAccess.EMPTY);
        assertEquals(server.bannerState(), trackingClient.bannerState());
        assertEquals(server.placedStructure(), trackingClient.placedStructure());
    }

    @Test
    void lateJoinAndInitialTrackingProduceEqualRenderState() {
        BannerBlockEntity server = entity("medium_wall", "linen", "iron",
                Direction.SOUTH, BannerOrientation.WALL_PARALLEL, 1, 2);
        BannerBlockEntity initialClient = emptyLike(server);
        initialClient.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        BannerBlockEntity lateClient = emptyLike(server);
        lateClient.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        BannerPlacedRenderState initial = Milestone13RenderFixtures.placed(initialClient, 5, 6);
        BannerPlacedRenderState late = Milestone13RenderFixtures.placed(lateClient, 5, 6);
        assertEquals(initial.key(), late.key());
        assertEquals(initial.appearance(), late.appearance());
        assertEquals(initial.renderBounds(), late.renderBounds());
        assertFalse(late.fallback());
    }

    @Test
    void colourAndMountUpdatesChangeRenderKeysWithoutReplacingBlockOrFootprint() {
        BannerBlockEntity server = entity("large", "cotton", "brass",
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 2, 2);
        BannerBlockEntity client = emptyLike(server);
        client.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        var blockBefore = client.getBlockState();
        var structureBefore = client.placedStructure();
        BannerPlacedRenderKey original = Milestone13RenderFixtures.placed(client, 7, 8).key();

        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var dyed = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.dyed(cotton), Milestone13RenderFixtures.mount("iron"));
        assertTrue(server.setBannerState(dyed));
        client.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        BannerPlacedRenderKey updated = Milestone13RenderFixtures.placed(client, 7, 8).key();
        assertNotEquals(original, updated);
        assertEquals(blockBefore, client.getBlockState());
        assertEquals(structureBefore, client.placedStructure());
        assertEquals(dyed, client.bannerState().orElseThrow());
    }

    @Test
    void synchronizedFootprintUpdateChangesBoundsWithoutTouchingBannerAppearance() {
        BannerBlockEntity server = entity("small", "cotton", "brass",
                Direction.EAST, BannerOrientation.WALL_PERPENDICULAR, 1, 1);
        BannerBlockEntity client = emptyLike(server);
        client.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        BannerPlacedRenderState before = Milestone13RenderFixtures.placed(client, 2, 3);

        var largeDefinition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var largeState = Milestone13RenderFixtures.state(largeDefinition, cotton,
                Milestone13RenderFixtures.natural(cotton), Milestone13RenderFixtures.mount("brass"));
        var large = Milestone13RenderFixtures.entity(server.getBlockPos(), Direction.EAST,
                BannerOrientation.WALL_PARALLEL, 2, 2, largeState);
        client.handleUpdateTag(large.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        BannerPlacedRenderState after = Milestone13RenderFixtures.placed(client, 2, 3);
        assertNotEquals(before.renderBounds(), after.renderBounds());
        assertEquals(2, after.persistedWidth());
        assertEquals(2, after.persistedHeight());
        assertEquals(4, after.lightingSamplePositions().size());
    }

    @Test
    void metadataBeforeStateAndStateBeforeMetadataBothRecoverWithoutReplacement() {
        BannerBlockEntity server = entity("medium", "wool", "iron",
                Direction.NORTH, BannerOrientation.WALL_PERPENDICULAR, 1, 2);

        BannerBlockEntity metadataFirst = emptyLike(server);
        BannerPlacedRenderState noState = BannerPlacedRenderStateExtractor.extract(
                metadataFirst, Milestone13RenderFixtures.publication(30),
                BannerAssetAvailability.allExpected(), 40);
        assertEquals(BannerPlacedRenderFailure.MISSING_BANNER_STATE, noState.failure());
        metadataFirst.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertFalse(Milestone13RenderFixtures.placed(metadataFirst, 30, 40).fallback());

        BannerBlockEntity stateFirst = emptyLike(server);
        stateFirst.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        var blockBefore = stateFirst.getBlockState();
        BannerPlacedRenderState noMetadata = BannerPlacedRenderStateExtractor.extract(
                stateFirst, new ClientBannerRenderPublication(
                        com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot.empty(),
                        31, false),
                BannerAssetAvailability.allExpected(), 40);
        assertEquals(BannerRenderFailure.REGISTRY_UNAVAILABLE, noMetadata.appearance().failure());
        BannerPlacedRenderState recovered = Milestone13RenderFixtures.placed(stateFirst, 32, 40);
        assertFalse(recovered.fallback());
        assertEquals(blockBefore, stateFirst.getBlockState());
        assertEquals(server.bannerState(), stateFirst.bannerState());
    }

    @Test
    void dataRemovalAndReintroductionRecoverExistingTrackedEntity() {
        BannerBlockEntity entity = entity("small", "silk", "brass",
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 1, 1);
        var blockBefore = entity.getBlockState();
        var bannerBefore = entity.bannerState();
        var structureBefore = entity.placedStructure();
        BannerPlacedRenderState present = Milestone13RenderFixtures.placed(entity, 1, 1);
        var missingPublication = new ClientBannerRenderPublication(
                com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot.empty(), 2, true);
        BannerPlacedRenderState missing = BannerPlacedRenderStateExtractor.extract(
                entity, missingPublication, BannerAssetAvailability.allExpected(), 1);
        BannerPlacedRenderState restored = Milestone13RenderFixtures.placed(entity, 3, 1);
        assertFalse(present.fallback());
        assertEquals(BannerRenderFailure.MISSING_DEFINITION, missing.appearance().failure());
        assertFalse(restored.fallback());
        assertEquals(present.appearance().definitionId(), restored.appearance().definitionId());
        assertEquals(blockBefore, entity.getBlockState());
        assertEquals(bannerBefore, entity.bannerState());
        assertEquals(structureBefore, entity.placedStructure());
    }

    @Test
    void missingAssetsDuringReloadRecoverOnNextResourceGeneration() {
        BannerBlockEntity entity = entity("small", "cotton", "brass",
                Direction.WEST, BannerOrientation.WALL_PARALLEL, 1, 1);
        var blockBefore = entity.getBlockState();
        var bannerBefore = entity.bannerState();
        var structureBefore = entity.placedStructure();
        BannerPlacedRenderState missing = BannerPlacedRenderStateExtractor.extract(
                entity, Milestone13RenderFixtures.publication(1),
                new BannerAssetAvailability(java.util.Set.of(), java.util.Set.of()), 10);
        BannerPlacedRenderState restored = BannerPlacedRenderStateExtractor.extract(
                entity, Milestone13RenderFixtures.publication(1),
                BannerAssetAvailability.allExpected(), 11);
        assertTrue(missing.fallback());
        assertEquals(BannerRenderFailure.MISSING_GEOMETRY, missing.appearance().failure());
        assertFalse(restored.fallback());
        assertNotEquals(missing.key(), restored.key());
        assertEquals(blockBefore, entity.getBlockState());
        assertEquals(bannerBefore, entity.bannerState());
        assertEquals(structureBefore, entity.placedStructure());
    }

    @Test
    void clientTagApplicationCannotMutateServerEntity() {
        BannerBlockEntity server = entity("small", "cotton", "brass",
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 1, 1);
        var originalServerState = server.bannerState();
        BannerBlockEntity client = emptyLike(server);
        client.handleUpdateTag(server.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        var definition = Milestone13RenderFixtures.definitionForGeometry("small");
        var cotton = Milestone13RenderFixtures.material("cotton");
        client.setBannerState(Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.dyed(cotton), Milestone13RenderFixtures.mount("iron")));
        assertEquals(originalServerState, server.bannerState());
        assertNotEquals(server.bannerState(), client.bannerState());
    }

    @Test
    void futureAuthoritativeSetterIsServerOnlyAndPreservesPlacementBoundary() throws Exception {
        BannerBlockEntity detached = entity("large", "cotton", "brass",
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 2, 2);
        var before = detached.bannerState();
        var structure = detached.placedStructure();
        assertFalse(detached.setBannerStateAndSynchronize(before.orElseThrow()));
        assertEquals(before, detached.bannerState());
        assertEquals(structure, detached.placedStructure());

        String source = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/banner/blockentity/BannerBlockEntity.java"));
        assertTrue(source.contains("level == null || level.isClientSide"));
        assertTrue(source.contains("setChanged()"));
        assertTrue(source.contains("sendBlockUpdated"));
        String method = source.substring(source.indexOf("setBannerStateAndSynchronize"),
                source.indexOf("public void synchronize()"));
        assertFalse(method.contains("setBlock"));
        assertFalse(method.contains("placedStructure ="));
        assertFalse(method.contains("mountId"));
        assertFalse(method.contains("orientation"));
    }

    @Test
    void builtInBlockEntitySyncRemainsTheOnlyFullPlacedStateTransport() throws Exception {
        String payloads = readTree(Path.of(
                "src/main/java/com/seggellion/britannia_mod/network/payload/banner"));
        assertTrue(payloads.contains("S2CBannerRenderDataPayload"));
        assertFalse(payloads.contains("BannerInstanceState"));
        assertFalse(payloads.matches("(?is).*(PlacedState|BlockEntityState|BannerPart).*Payload.*"));
        String entity = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/banner/blockentity/BannerBlockEntity.java"));
        assertTrue(entity.contains("getUpdateTag"));
        assertTrue(entity.contains("ClientboundBlockEntityDataPacket.create(this)"));
    }

    private static BannerBlockEntity entity(
            String family, String materialPath, String mountPath,
            Direction facing, BannerOrientation orientation, int width, int height) {
        var definition = Milestone13RenderFixtures.definitionForGeometry(family);
        var material = Milestone13RenderFixtures.material(materialPath);
        var state = Milestone13RenderFixtures.state(definition, material,
                Milestone13RenderFixtures.natural(material), Milestone13RenderFixtures.mount(mountPath));
        return Milestone13RenderFixtures.entity(
                new BlockPos(15, 72, -16), facing, orientation, width, height, state);
    }

    private static BannerBlockEntity emptyLike(BannerBlockEntity source) {
        return new BannerBlockEntity(Milestone13RenderFixtures.blockEntityType(),
                source.getBlockPos(), source.getBlockState());
    }

    private static String readTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).map(path -> {
                try {
                    return Files.readString(path);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).reduce("", String::concat);
        }
    }
}
