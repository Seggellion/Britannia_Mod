package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.client.banner.BannerAssetAvailability;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderState;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderStateExtractor;
import com.seggellion.britannia_mod.client.banner.ClientBannerRenderPublication;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Production-shaped shared fixtures for the Milestone 13 renderer tests. */
public final class Milestone13RenderFixtures {
    private static BannerBlock testBlock;
    private static BlockEntityType<BannerBlockEntity> testBlockEntityType;
    private static RegistrySnapshot registry;
    private static BannerRenderDataSnapshot renderData;

    private Milestone13RenderFixtures() {
    }

    public static synchronized void ensureLoaded() {
        if (registry != null) {
            return;
        }
        try {
            Milestone7RegisteredTestContent.ensureRegistered();
            registry = DyeResolverFixtures.productionSnapshot();
            renderData = BannerRenderDataSnapshot.fromRegistry(registry);
            testBlock = new BannerBlock(BlockBehaviour.Properties.of());
            testBlockEntityType = createBlockEntityType();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load production banner fixtures", exception);
        }
    }

    public static RegistrySnapshot registry() {
        ensureLoaded();
        return registry;
    }

    public static BannerRenderDataSnapshot renderData() {
        ensureLoaded();
        return renderData;
    }

    public static ClientBannerRenderPublication publication(long generation) {
        return new ClientBannerRenderPublication(renderData(), generation, true);
    }

    public static BannerDefinitionId definitionForGeometry(String path) {
        if ("medium".equals(path)) {
            return BannerDefinitionId.parse("britannia_mod:tournament_medium");
        }
        if ("small".equals(path)) {
            BannerDefinitionId approvedSmall =
                    BannerDefinitionId.parse("britannia_mod:silver_and_gold_pennon");
            if (renderData().banners().containsKey(approvedSmall)) {
                return approvedSmall;
            }
        }
        return renderData().banners().values().stream()
                .filter(definition -> definition.assets().geometry().getPath().equals(
                        "banner/placeholder/" + path))
                .findFirst().orElseThrow().id();
    }

    public static FabricMaterialId material(String path) {
        return FabricMaterialId.parse("britannia_mod:" + path);
    }

    public static MountId mount(String path) {
        return MountId.parse("britannia_mod:" + path);
    }

    public static ResolvedColourId natural(FabricMaterialId material) {
        return renderData().materials().get(material).naturalColourId();
    }

    public static ResolvedColourId dyed(FabricMaterialId material) {
        return renderData().materials().get(material).displaySrgbByColour().keySet().stream()
                .filter(id -> !id.equals(natural(material))).findFirst().orElseThrow();
    }

    public static BannerInstanceState state(
            BannerDefinitionId definition,
            FabricMaterialId material,
            ResolvedColourId colour,
            MountId mount) {
        return state(definition, material, colour, Optional.empty(), mount);
    }

    public static BannerInstanceState state(
            BannerDefinitionId definition,
            FabricMaterialId material,
            ResolvedColourId colour,
            Optional<PigmentId> pigment,
            MountId mount) {
        return new BannerInstanceState(1, definition, material, colour, pigment, mount);
    }

    public static BannerBlockEntity entity(
            BlockPos anchor,
            Direction facing,
            BannerOrientation orientation,
            int width,
            int height,
            BannerInstanceState state) {
        ensureLoaded();
        var blockState = testBlock.defaultBlockState()
                .setValue(BannerBlock.FACING, facing)
                .setValue(BannerBlock.ORIENTATION, orientation);
        BannerBlockEntity entity = new BannerBlockEntity(
                testBlockEntityType, anchor, blockState);
        var footprint = BannerFootprint.fromDimensions(
                new BannerDimensions(width, height, true)).footprint();
        entity.setPlacedState(state, BannerPlacedStructure.fromFootprint(orientation, footprint));
        return entity;
    }

    public static BannerPlacedRenderState placed(
            BannerBlockEntity entity, long dataGeneration, long resourceGeneration) {
        return BannerPlacedRenderStateExtractor.extract(entity, publication(dataGeneration),
                BannerAssetAvailability.allExpected(), resourceGeneration);
    }

    public static BlockEntityType<BannerBlockEntity> blockEntityType() {
        ensureLoaded();
        return testBlockEntityType;
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<BannerBlockEntity> createBlockEntityType() {
        BlockEntityType<BannerBlockEntity>[] holder = new BlockEntityType[1];
        holder[0] = BlockEntityType.Builder.of(
                (pos, state) -> new BannerBlockEntity(holder[0], pos, state), testBlock).build(null);
        return holder[0];
    }
}
