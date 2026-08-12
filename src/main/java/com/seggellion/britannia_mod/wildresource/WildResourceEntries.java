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
        bootstrapped = true;
    }
}
