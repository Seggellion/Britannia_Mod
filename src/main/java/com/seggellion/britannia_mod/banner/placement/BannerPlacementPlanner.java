package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionRegistry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Ordered, mutation-free creation of an immutable single-cell placement plan. */
public final class BannerPlacementPlanner {
    private BannerPlacementPlanner() {
    }

    public static BannerPlacementPlanningResult plan(
            BannerItem item,
            ItemStack stack,
            RegistrySnapshot snapshot,
            boolean registryAvailable,
            BlockPos clickedPos,
            Direction clickedFace,
            BannerBlock bannerBlock,
            BannerPlacementWorld world) {
        if (stack.getItem() != item) return fail(BannerPlacementFailure.INVALID_ITEM);
        BannerInstanceState state = item.stateAccess().read(stack).orElse(null);
        if (state == null) return fail(BannerPlacementFailure.UNCONFIGURED_BANNER);
        if (!registryAvailable) return fail(BannerPlacementFailure.REGISTRY_UNAVAILABLE);

        BannerDefinition definition = snapshot.banners().find(state.bannerDefinitionId()).orElse(null);
        if (definition == null) {
            return fail(missingOrDisabled(snapshot.banners(), state.bannerDefinitionId(),
                    BannerPlacementFailure.DEFINITION_MISSING, BannerPlacementFailure.DEFINITION_DISABLED));
        }
        if (!snapshot.fabricMaterials().contains(state.materialId())) {
            return fail(missingOrDisabled(snapshot.fabricMaterials(), state.materialId(),
                    BannerPlacementFailure.MATERIAL_MISSING, BannerPlacementFailure.MATERIAL_DISABLED));
        }
        var material = snapshot.fabricMaterials().require(state.materialId());
        var palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) return fail(BannerPlacementFailure.PALETTE_MISSING);
        if (palette.entries().stream().noneMatch(entry -> entry.id().equals(state.resolvedColourId()))) {
            return fail(BannerPlacementFailure.COLOUR_MISSING);
        }
        if (!snapshot.mounts().contains(state.mountId())) {
            return fail(missingOrDisabled(snapshot.mounts(), state.mountId(),
                    BannerPlacementFailure.MOUNT_MISSING, BannerPlacementFailure.MOUNT_DISABLED));
        }
        if (!definition.supportedMounts().contains(state.mountId())) {
            return fail(BannerPlacementFailure.UNSUPPORTED_MOUNT);
        }
        BannerSingleBlockEligibility.Result eligibility = BannerSingleBlockEligibility.evaluate(definition);
        if (eligibility == BannerSingleBlockEligibility.Result.WALL_PARALLEL_UNSUPPORTED) {
            return fail(BannerPlacementFailure.UNSUPPORTED_ORIENTATION);
        }
        if (eligibility == BannerSingleBlockEligibility.Result.MULTI_BLOCK_DEFERRED) {
            return fail(BannerPlacementFailure.MULTI_BLOCK_PLACEMENT_DEFERRED);
        }
        if (!clickedFace.getAxis().isHorizontal()) return fail(BannerPlacementFailure.INVALID_CLICKED_FACE);

        BlockPos targetPos = clickedPos.relative(clickedFace);
        if (!world.targetReplaceable(targetPos)) return fail(BannerPlacementFailure.TARGET_OCCUPIED);
        if (!world.inWorldBounds(targetPos)) return fail(BannerPlacementFailure.WORLD_BOUND_FAILURE);
        if (!world.validWallSupport(clickedPos, clickedFace)) {
            return fail(BannerPlacementFailure.INVALID_WALL_SUPPORT);
        }
        if (!world.placementAllowed(targetPos, clickedFace, stack)) {
            return fail(BannerPlacementFailure.PROTECTED_PLACEMENT);
        }
        var bannerState = bannerBlock.defaultBlockState().setValue(BannerBlock.FACING, clickedFace);
        if (!world.canCreateBannerBlockEntity(bannerState)) {
            return fail(BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        }
        if (!world.canAcceptState(state)) return fail(BannerPlacementFailure.STATE_TRANSFER_FAILURE);

        return BannerPlacementPlanningResult.success(new BannerPlacementPlan(
                clickedPos.immutable(), targetPos.immutable(), clickedFace,
                world.blockState(targetPos), bannerState, state));
    }

    private static BannerPlacementPlanningResult fail(BannerPlacementFailure failure) {
        return BannerPlacementPlanningResult.failure(failure);
    }

    private static <I, T> BannerPlacementFailure missingOrDisabled(
            DefinitionRegistry<I, T> registry, I id,
            BannerPlacementFailure missing, BannerPlacementFailure disabled) {
        boolean isDisabled = registry.disabledEntries().stream().map(DefinitionEntry::id).anyMatch(id::equals);
        return isDisabled ? disabled : missing;
    }
}
