package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import org.slf4j.Logger;

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
             LOGGER.info("Wood block is about to break");

        // Only proceed if it's a log block, and the player is using your custom axe
        if (usingTwoHandedAxe && state.is(BlockTags.LOGS)) {
            // 1) Play your chop-tree sound
            player.level().playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 2.0F, 2.0F);

            LOGGER.info("Chopping log at {} with TwoHandedAxe. Cancelling default drop...", pos);

            // 2) Cancel normal drops (so no default logs drop)
            event.setCanceled(true);

            // 3) Actually remove the block from the world
            //    (If you want it to vanish. Or you can do nothing if you prefer.)
            serverLevel.setBlock(pos, serverLevel.getFluidState(pos).createLegacyBlock(), 2);

            // 4) Identify the wood type from block name
            String woodType = deduceWoodType(state);
            Double[] range = WOOD_TYPE_RANGES.getOrDefault(woodType, new Double[]{4.5, 5.5});
            double min = range[0], max = range[1];

            // 5) Generate random weight
            double weight = generateRandomWeight(min, max);

            // 6) Create WeightedWoodItem (1 piece). If you want multiple, adjust setCount or spawn multiple items
            ItemStack woodStack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
            WeightedWoodItem woodItem = (WeightedWoodItem) woodStack.getItem();
            woodItem.setWoodType(woodStack, woodType);
            woodItem.setWeight(woodStack, weight);

            // 7) Drop it in the world
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
    return; 
    }
    TreeKarmaHandler.treeCutTimestamps.put(playerId, currentTime);

                    player.sendSystemMessage(Component.literal("You cut down a tree. Replant a sapling to avoid karma loss"));

        }
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
