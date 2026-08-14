package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.event.WildResourceHarvestEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.neoforged.neoforge.common.NeoForge;

/** Atomic server-side removal, loot, and cooldown accounting for special harvest paths. */
public final class WildResourceHarvestService {
    private WildResourceHarvestService() {
    }

    public static boolean harvestOne(
            ServerLevel level,
            BlockPos position,
            ServerPlayer player,
            Block expectedBlock,
            Item expectedItem
    ) {
        WildResourceSavedData data = WildResourceSavedData.get(level);
        WildResourceNode node = data.nodeAt(position).orElse(null);
        WildResourceEntry entry = node == null ? null : WildResources.registry().find(node.resourceId()).orElse(null);
        if (node == null || entry == null || !level.getBlockState(position).is(expectedBlock)) {
            return false;
        }
        if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), 3)) {
            return false;
        }
        data.removeNode(position);
        scheduleRespawn(level, data, entry, position);
        ItemStack result = new ItemStack(expectedItem);
        Block.popResource(level, position, result.copy());
        postHarvest(level, position, player, entry, result, player.getMainHandItem());
        return true;
    }

    public static boolean harvestOyster(
            ServerLevel level,
            BlockPos position,
            ServerPlayer player,
            ItemStack tool
    ) {
        return DaggerTools.isDagger(tool) && harvestOne(
                level,
                position,
                player,
                BlockRegistry.BLACK_LIPPED_OYSTER.get(),
                ItemRegistry.BLACK_PEARL.get()
        );
    }

    public static ItemStack createOysterLoot(ServerLevel level, BlockPos position, ServerPlayer player) {
        return new ItemStack(ItemRegistry.BLACK_PEARL.get());
    }

    static void recordOrdinaryBreak(ServerLevel level, BlockPos position, ServerPlayer player) {
        WildResourceSavedData data = WildResourceSavedData.get(level);
        WildResourceNode node = data.removeNode(position).orElse(null);
        WildResourceEntry entry = node == null ? null : WildResources.registry().find(node.resourceId()).orElse(null);
        if (entry != null) {
            scheduleRespawn(level, data, entry, position);
            if (!player.getAbilities().instabuild) {
                ItemStack result = entry.lootStrategy().create(level, position, player);
                if (!result.isEmpty()) {
                    postHarvest(level, position, player, entry, result, player.getMainHandItem());
                }
            }
        }
    }

    private static void postHarvest(
            ServerLevel level,
            BlockPos position,
            ServerPlayer player,
            WildResourceEntry entry,
            ItemStack result,
            ItemStack tool
    ) {
        WildResourceHarvestEvent.ToolCategory toolCategory = DaggerTools.isDagger(tool)
                ? WildResourceHarvestEvent.ToolCategory.DAGGER
                : WildResourceHarvestEvent.ToolCategory.OTHER;
        NeoForge.EVENT_BUS.post(new WildResourceHarvestEvent(
                player, entry.id(), position, level.dimension(), result, toolCategory
        ));
    }

    private static void scheduleRespawn(
            ServerLevel level,
            WildResourceSavedData data,
            WildResourceEntry entry,
            BlockPos position
    ) {
        data.scheduleAttempt(
                new ChunkPos(position),
                entry.id(),
                level.getGameTime() + entry.tuning().nextRespawnDelay(level.random)
        );
    }
}
