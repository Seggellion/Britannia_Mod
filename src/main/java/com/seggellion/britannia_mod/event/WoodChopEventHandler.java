package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.AxeHarvestRules;
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
    private static final Map<String, Double[]> WOOD_TYPE_RANGES = new HashMap<>();
    static {
        WOOD_TYPE_RANGES.put("oak",       new Double[]{4.5, 5.5});
        WOOD_TYPE_RANGES.put("spruce",    new Double[]{3.5, 4.5});
        WOOD_TYPE_RANGES.put("birch",     new Double[]{3.0, 4.0});
        WOOD_TYPE_RANGES.put("jungle",    new Double[]{4.0, 5.0});
        WOOD_TYPE_RANGES.put("acacia",    new Double[]{4.0, 5.0});
        WOOD_TYPE_RANGES.put("dark_oak",  new Double[]{5.0, 6.0});
        // Add more if you have more types
        // Everything else can default to "oak" if unknown
    }

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
            event.setCanceled(true);
            handleAxeHarvest(serverLevel, pos, state, player);
        }
    }

    public static boolean handleAxeHarvest(ServerLevel serverLevel, BlockPos pos, BlockState state, Player player) {
        if (AxeHarvestRules.isAllowedLeafBlock(state)) {
            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);

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

            String woodType = deduceWoodType(state);
            Double[] range = WOOD_TYPE_RANGES.getOrDefault(woodType, new Double[]{4.5, 5.5});
            double weight = generateRandomWeight(range[0], range[1]);

            ItemStack woodStack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
            WeightedWoodItem woodItem = (WeightedWoodItem) woodStack.getItem();
            woodItem.setWoodType(woodStack, woodType);
            woodItem.setWeight(woodStack, weight);

            ItemEntity drop = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, woodStack);
            serverLevel.addFreshEntity(drop);

            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();

            LOGGER.info("Dropped WeightedWoodItem for type={}, weight={}", woodType, weight);

            // Optionally message the player
            player.displayClientMessage(
                Component.literal(String.format("You chop %s log. Weight=%.2f stones", woodType, weight)), true
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


    private static String deduceWoodType(BlockState state) {
        // e.g. "block.minecraft.oak_log"
        String name = state.getBlock().getDescriptionId().toLowerCase();

        if (name.contains("spruce"))    return "spruce";
        if (name.contains("birch"))     return "birch";
        if (name.contains("jungle"))    return "jungle";
        if (name.contains("acacia"))    return "acacia";
        if (name.contains("dark_oak"))  return "dark_oak";
        // default fallback
        return "oak";
    }

    private static double generateRandomWeight(double min, double max) {
        return min + RandomSource.create().nextDouble() * (max - min);
    }
}
