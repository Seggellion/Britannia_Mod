package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.OrangeTreeBranchBlock;
import com.seggellion.britannia_mod.block.OrangeTreeRootBlock;
import com.seggellion.britannia_mod.block.OrangeTreeTrunkBlock;
import com.seggellion.britannia_mod.block.WeightedWoodBlock;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.block.entity.WeightedWoodBlockEntity;
import com.seggellion.britannia_mod.farming.OrangeTreeUtils;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.item.WeightedWoodType;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.AxeHarvestRules;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Replaces the old GlobalBreakSoundHandler with an expanded version:
 * 1) Plays chop sound when logs are broken with your TwoHandedAxe.
 * 2) Cancels normal log drops and spawns WeightedWoodItem with random weight.
 * 3) Uses approximate real-world "stones" ranges for each wood type.
 */
public class WoodChopEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Holds min–max "stones" (approx. densities) for known wood types
    // e.g. Oak logs might be ~4.5–5.5 stones, Birch might be ~3–4, etc.
    // Adjust these to your desired ranges.
    private static final Map<Block, ItemStack> LEAF_SAPLING_DROPS = new HashMap<>();

    static {
        LEAF_SAPLING_DROPS.put(Blocks.OAK_LEAVES, new ItemStack(Items.OAK_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.SPRUCE_LEAVES, new ItemStack(Items.SPRUCE_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.BIRCH_LEAVES, new ItemStack(Items.BIRCH_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.JUNGLE_LEAVES, new ItemStack(Items.JUNGLE_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.ACACIA_LEAVES, new ItemStack(Items.ACACIA_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.DARK_OAK_LEAVES, new ItemStack(Items.DARK_OAK_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.MANGROVE_LEAVES, new ItemStack(Items.MANGROVE_PROPAGULE));
        LEAF_SAPLING_DROPS.put(Blocks.CHERRY_LEAVES, new ItemStack(Items.CHERRY_SAPLING));
        LEAF_SAPLING_DROPS.put(Blocks.AZALEA_LEAVES, new ItemStack(Items.AZALEA));
        LEAF_SAPLING_DROPS.put(Blocks.FLOWERING_AZALEA_LEAVES, new ItemStack(Items.FLOWERING_AZALEA));
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Must be server-side
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Check if the player is using your TwoHandedAxe
        ItemStack stack = player.getMainHandItem();
        boolean usingTwoHandedAxe = stack.is(ItemRegistry.TWO_HANDED_AXE.get());

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        if (usingTwoHandedAxe) {
            // Whose ground is this, and is this even a player? Asked before the harvest through
            // the shared boundary, because this listener cancels the event and takes the block
            // itself -- a cancelled event never reaches StructureProtectionHandler, so this
            // handler is the authority for its own break.
            if (com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization
                    .refuses(serverLevel, pos, player)) {
                // Cancelled without harvesting: the tree stays standing and nothing is minted.
                event.setCanceled(true);
                return;
            }
            event.setCanceled(true);
            handleAxeHarvest(serverLevel, pos, state, player);
        }
    }

    public static boolean handleAxeHarvest(ServerLevel serverLevel, BlockPos pos, BlockState state, Player player) {
        if (AxeHarvestRules.isAllowedFruitBlock(state)) {
            OrangeTreeRootBlockEntity root = OrangeTreeUtils.findRoot(serverLevel, pos).orElse(null);
            if (root != null) {
                OrangeFruitBlock.dropFruitFromTree(serverLevel, pos, root, player, false);
            }
            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);
            return true;
        }

        if (state.getBlock() instanceof WeightedWoodBlock) {
            player.level().playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 2.0F, 2.0F);
            WeightedWoodType woodType = state.getValue(WeightedWoodBlock.WOOD_TYPE);
            double weight = woodType.averageWeight();
            if (serverLevel.getBlockEntity(pos) instanceof WeightedWoodBlockEntity weightedWoodBlockEntity) {
                woodType = weightedWoodBlockEntity.getWoodTypeDefinition();
                weight = weightedWoodBlockEntity.getWoodWeight();
            }
            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);
            player.displayClientMessage(
                    Component.literal(String.format("You chop %s. Weight=%.2f stones", woodType.displayName().toLowerCase(), weight)),
                    true
            );
            return true;
        }

        if (state.is(ModTags.Blocks.FRUIT_TREE_LOGS)
                || state.is(ModTags.Blocks.FRUIT_TREE_TRUNKS)
                || state.is(ModTags.Blocks.FRUIT_TREE_BRANCHES)
                || state.getBlock() instanceof OrangeTreeRootBlock
                || state.getBlock() instanceof OrangeTreeTrunkBlock
                || state.getBlock() instanceof OrangeTreeBranchBlock) {
            player.level().playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 2.0F, 2.0F);
            OrangeTreeRootBlockEntity root = OrangeTreeUtils.findRoot(serverLevel, pos).orElse(null);
            if (root != null) {
                root.cleanupTree(serverLevel, player, true);
                player.displayClientMessage(Component.literal("You chop down the " + root.definition().displayName().toLowerCase() + " tree."), true);
            } else {
                serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);
            }
            return true;
        }

        if (AxeHarvestRules.isAllowedLeafBlock(state)) {
            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);

            if (state.is(ModTags.Blocks.FRUIT_TREE_LEAVES)) {
                return true;
            }

            if (serverLevel.random.nextInt(4) == 0) {
                ItemStack saplingStack = LEAF_SAPLING_DROPS
                    .getOrDefault(state.getBlock(), new ItemStack(Items.OAK_SAPLING))
                    .copy();

                ItemEntity drop = new ItemEntity(
                    serverLevel,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    saplingStack
                );

                serverLevel.addFreshEntity(drop);

                player.displayClientMessage(
                    Component.literal("You recover a sapling from the leaves."),
                    true
                );
            }

            return true;
        }

        if (AxeHarvestRules.isAllowedLogBlock(state)) {
            player.level().playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 2.0F, 2.0F);

            LOGGER.info("Chopping log at {} with TwoHandedAxe. Cancelling default drop...", pos);

            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);

            WeightedWoodType woodType = deduceWoodType(state);
            double weight = generateRandomWeight(woodType);

            ItemStack woodStack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
            WeightedWoodItem woodItem = (WeightedWoodItem) woodStack.getItem();
            woodItem.setWoodType(woodStack, woodType.id());
            woodItem.setWeight(woodStack, weight);

            ItemEntity drop = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, woodStack);
            serverLevel.addFreshEntity(drop);

            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();

            LOGGER.info("Dropped WeightedWoodItem for type={}, weight={}", woodType.id(), weight);

            // Optionally message the player
            player.displayClientMessage(
                Component.literal(String.format("You chop %s. Weight=%.2f stones", woodType.displayName().toLowerCase(), weight)), true
            );

            if (player.isCreative() || player.hasPermissions(2)) {
                LOGGER.info("Skipping TreeKarmaHandler: Player {} is in Creative or an OP.", player.getName().getString());
                return true;
            }

            TreeKarmaHandler.treeCutTimestamps.put(playerId, currentTime);
            player.sendSystemMessage(Component.literal("You cut down a tree. Replant a sapling to avoid karma loss"));

            return true;
        }

        return false;
    }


    private static WeightedWoodType deduceWoodType(BlockState state) {
        // e.g. "block.minecraft.oak_log"
        String name = state.getBlock().getDescriptionId().toLowerCase();

        if (name.contains("spruce")) return WeightedWoodType.SPRUCE;
        if (name.contains("birch")) return WeightedWoodType.BIRCH;
        if (name.contains("jungle")) return WeightedWoodType.JUNGLE;
        if (name.contains("acacia")) return WeightedWoodType.ACACIA;
        if (name.contains("dark_oak")) return WeightedWoodType.DARK_OAK;
        if (name.contains("mangrove")) return WeightedWoodType.MANGROVE;
        if (name.contains("cherry")) return WeightedWoodType.CHERRY;
        if (name.contains("bamboo")) return WeightedWoodType.BAMBOO;
        if (name.contains("crimson")) return WeightedWoodType.CRIMSON;
        if (name.contains("warped")) return WeightedWoodType.WARPED;
        // default fallback
        return WeightedWoodType.OAK;
    }

    private static double generateRandomWeight(WeightedWoodType woodType) {
        return woodType.randomWeight(RandomSource.create());
    }
}
