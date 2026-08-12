package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Central registration and balance values for configured environmental resources. */
public final class WildResourceEntries {
    public static final ResourceLocation SULPHUROUS_ASH = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "sulphurous_ash_patch"
    );
    public static final WildResourceTuning SULPHUROUS_ASH_TUNING = new WildResourceTuning(
            20 * 60 * 3,
            20 * 60 * 6,
            20 * 60 * 20,
            20 * 60 * 40,
            4,
            8
    );
    public static final ResourceLocation BLACK_LIPPED_OYSTER = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "black_lipped_oyster"
    );
    /** The repository's established UltimaCraft limestone identity is vanilla calcite. */
    public static final ResourceLocation CANONICAL_LIMESTONE = ResourceLocation.withDefaultNamespace("calcite");
    public static final WildResourceTuning BLACK_LIPPED_OYSTER_TUNING = new WildResourceTuning(
            20 * 60 * 4,
            20 * 60 * 8,
            20 * 60 * 30,
            20 * 60 * 60,
            4,
            6
    );
    private static boolean bootstrapped;

    private WildResourceEntries() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        WildResources.registry().register(new WildResourceEntry(
                SULPHUROUS_ASH,
                1,
                2,
                SULPHUROUS_ASH_TUNING,
                WildResourcePlacementRules::surfaceCandidate,
                WildResourcePlacementRules::isSafeTarget,
                WildResourcePlacementRules::isAshSupport,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceProximity::hasLavaWithin,
                WildResourceEntries::inspectAsh,
                (level, position) -> level.setBlock(position, BlockRegistry.SULPHUROUS_ASH_PATCH.get().defaultBlockState(), 3),
                (level, position, player, tool) -> WildResourceHarvestService.harvestOne(
                        level,
                        position,
                        player,
                        BlockRegistry.SULPHUROUS_ASH_PATCH.get(),
                        ItemRegistry.SULPHUROUS_ASH.get()
                ),
                (level, position, player) -> new ItemStack(ItemRegistry.SULPHUROUS_ASH.get())
        ));
        WildResources.registry().register(new WildResourceEntry(
                BLACK_LIPPED_OYSTER,
                1,
                3,
                BLACK_LIPPED_OYSTER_TUNING,
                WildResourcePlacementRules::surfaceCandidate,
                WildResourcePlacementRules::isSafeTarget,
                WildResourcePlacementRules::isOysterSubstrate,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceProximity::hasWaterWithin,
                WildResourceEntries::inspectOyster,
                (level, position) -> level.setBlock(
                        position, BlockRegistry.BLACK_LIPPED_OYSTER.get().defaultBlockState(), 3
                ),
                WildResourceHarvestService::harvestOyster,
                WildResourceHarvestService::createOysterLoot
        ));
        bootstrapped = true;
    }

    private static WildResourceEntry.ExistingNodeState inspectAsh(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos position
    ) {
        if (!level.getBlockState(position).is(BlockRegistry.SULPHUROUS_ASH_PATCH.get())) {
            return WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED;
        }
        if (!WildResourcePlacementRules.isAshSupport(level, position)) {
            return WildResourceEntry.ExistingNodeState.OWNED_INVALID;
        }
        return fromQuery(WildResourceProximity.lavaStatus(level, position));
    }

    private static WildResourceEntry.ExistingNodeState inspectOyster(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos position
    ) {
        if (!level.getBlockState(position).is(BlockRegistry.BLACK_LIPPED_OYSTER.get())) {
            return WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED;
        }
        if (!WildResourcePlacementRules.isOysterSubstrate(level, position)) {
            return WildResourceEntry.ExistingNodeState.OWNED_INVALID;
        }
        return fromQuery(WildResourceProximity.waterStatus(level, position));
    }

    private static WildResourceEntry.ExistingNodeState fromQuery(WildResourceProximity.QueryResult result) {
        return switch (result) {
            case FOUND -> WildResourceEntry.ExistingNodeState.VALID;
            case NOT_FOUND -> WildResourceEntry.ExistingNodeState.OWNED_INVALID;
            case INCOMPLETE -> WildResourceEntry.ExistingNodeState.DEFER;
        };
    }
}
