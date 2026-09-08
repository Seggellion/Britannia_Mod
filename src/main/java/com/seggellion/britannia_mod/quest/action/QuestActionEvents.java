package com.seggellion.britannia_mod.quest.action;

import com.seggellion.britannia_mod.quest.QuestObjectiveWatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The one place a farming success point says what it just did (protocol section 2.1).
 *
 * <p>Every method here is called AFTER the real mutation has succeeded on the server, and every
 * one of them is a no-op off the server thread, on the client, for a non-player, or when nothing
 * in the player's journal subscribes. That is the whole point of the seam: a farming class states
 * a fact, and this package decides -- against the SERVER's journal -- whether Rails needs to hear
 * it. A failed, denied, cooled-down or wrong-item interaction never reaches these methods at all,
 * because the callers return before them.
 *
 * <p>Subject fields are written in the order protocol section 2.1 lists them, which is also the
 * order the frozen request fixture shows, so the encoder never has to reorder anything.
 */
public final class QuestActionEvents {
    private QuestActionEvents() {}

    /** {@code "<dimension_key>:<x>:<y>:<z>"} (section 2.1). */
    public static String plotKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /** The registry id of an item, or an empty string for an empty or unregistered stack. */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return itemId(stack.getItem());
    }

    public static String itemId(Item item) {
        if (item == null) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    // --- stage 1: gathering ------------------------------------------------------------------

    /** After {@code WildResourceHarvestService.harvestOne} removed the node and popped the loot. */
    public static void wildResourceHarvest(ServerPlayer player, ServerLevel level, BlockPos position,
                                           String resourceId, ItemStack tool) {
        if (resourceId == null || resourceId.isBlank()) return;
        publish(player, QuestAction.WILD_RESOURCE_HARVEST, level, position, QuestActionSubject.builder()
            .text("resource_id", resourceId)
            .optionalText("tool_item_id", itemId(tool))
            .build());
    }

    /**
     * After {@code DirtGatheringService.attempt} claimed the cooldown and granted the dirt.
     *
     * <p>Takes the item id rather than the stack on purpose: {@code Inventory#add} empties the
     * stack it consumed, so a caller that reports after delivering has nothing left to name.
     */
    public static void dirtGather(ServerPlayer player, ServerLevel level, BlockPos position,
                                  String outputItemId, ItemStack tool) {
        if (outputItemId == null || outputItemId.isBlank()) return;
        publish(player, QuestAction.DIRT_GATHER, level, position, QuestActionSubject.builder()
            .text("item_id", outputItemId)
            .optionalText("tool_item_id", itemId(tool))
            .build());
    }

    /** After {@code WaterSourceInteraction.fillFromSource} completed the exchange. */
    public static void waterContainerFill(Player player, Level level, BlockPos position,
                                          String containerItemId, String source) {
        if (containerItemId == null || containerItemId.isBlank()) return;
        publish(player, level, QuestAction.WATER_CONTAINER_FILL, position, QuestActionSubject.builder()
            .text("container_item_id", containerItemId)
            .text("source", source == null || source.isBlank() ? QuestAction.SOURCE_BLOCK : source)
            .build());
    }

    // --- stage 2: bowls ----------------------------------------------------------------------

    /** After {@code BowlPreparationService.apply} answered {@code APPLIED}. */
    public static void bowlPrepare(ServerPlayer player, String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) return;
        publishAtPlayer(player, QuestAction.BOWL_PREPARE, QuestActionSubject.builder()
            .text("output_item_id", outputItemId)
            .build());
    }

    /** After {@code BowlWaterFillingService.apply} answered {@code APPLIED}. */
    public static void bowlWaterFill(ServerPlayer player, String outputItemId, String source) {
        if (outputItemId == null || outputItemId.isBlank()) return;
        publishAtPlayer(player, QuestAction.BOWL_WATER_FILL, QuestActionSubject.builder()
            .text("output_item_id", outputItemId)
            .optionalText("source", source)
            .build());
    }

    /** After {@code FertileDirtMixingService.apply} answered {@code APPLIED}. */
    public static void fertileDirtMix(ServerPlayer player, String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) return;
        publishAtPlayer(player, QuestAction.FERTILE_DIRT_MIX, QuestActionSubject.builder()
            .text("output_item_id", outputItemId)
            .build());
    }

    // --- stage 3: the plot -------------------------------------------------------------------

    /** After {@code FarmingHoeItem.prepareCommunityPlot}'s {@code setBlock} succeeded. */
    public static void plotHoe(Player player, Level level, BlockPos position) {
        publish(player, level, QuestAction.PLOT_HOE, position, QuestActionSubject.builder()
            .text("plot_key", plotKey(level, position))
            .flag("community_plot", true)
            .build());
    }

    /** After {@code CommunityHoedFarmBlock.fertilizeCommunityPlot}'s {@code setBlock} succeeded. */
    public static void plotFertilize(Player player, Level level, BlockPos position) {
        publish(player, level, QuestAction.PLOT_FERTILIZE, position, QuestActionSubject.builder()
            .text("plot_key", plotKey(level, position))
            .flag("community_plot", true)
            .build());
    }

    /** After {@code FarmingBlock.tryPlantSeed} planted the crop and shrank the seed. */
    public static void cropPlant(Player player, Level level, BlockPos position, String cropId,
                                 UUID cropCycleUuid, UUID planterUuid, boolean communityPlot) {
        if (cropId == null || cropId.isBlank() || cropCycleUuid == null || planterUuid == null) return;
        publish(player, level, QuestAction.CROP_PLANT, position, QuestActionSubject.builder()
            .text("plot_key", plotKey(level, position))
            .text("crop_id", cropId)
            .text("crop_cycle_uuid", cropCycleUuid.toString())
            .text("planter_uuid", planterUuid.toString())
            .flag("community_plot", communityPlot)
            .build());
    }

    /** After {@code WateringCanItem.waterFarmingBlock}'s {@code water(1)} raised the hydration. */
    public static void cropWater(Player player, Level level, BlockPos position, String cropId,
                                 UUID cropCycleUuid, int hydration, String careState) {
        if (cropId == null || cropId.isBlank() || cropCycleUuid == null) return;
        publish(player, level, QuestAction.CROP_WATER, position, QuestActionSubject.builder()
            .text("plot_key", plotKey(level, position))
            .text("crop_id", cropId)
            .text("crop_cycle_uuid", cropCycleUuid.toString())
            .number("hydration", hydration)
            .optionalText("care_state", careState)
            .build());
    }

    /** After {@code FarmingBlock.tryHarvestCrop} popped the produce. */
    public static void cropHarvest(Player player, Level level, BlockPos position, String cropId,
                                   UUID cropCycleUuid, UUID planterUuid, boolean communityPlot, int yield) {
        if (cropId == null || cropId.isBlank() || cropCycleUuid == null || planterUuid == null) return;
        publish(player, level, QuestAction.CROP_HARVEST, position,
            cropHarvestSubject(plotKey(level, position), cropId, cropCycleUuid, planterUuid, communityPlot, yield));
    }

    /**
     * The harvest subject, in the frozen fixture's field order. Public so the wire-contract test
     * byte-compares the bytes this very method produces rather than a hand-built stand-in.
     */
    public static QuestActionSubject cropHarvestSubject(String plotKey, String cropId, UUID cropCycleUuid,
                                                        UUID planterUuid, boolean communityPlot, int yield) {
        return QuestActionSubject.builder()
            .text("plot_key", plotKey)
            .text("crop_id", cropId)
            .text("crop_cycle_uuid", cropCycleUuid.toString())
            .text("planter_uuid", planterUuid.toString())
            .flag("community_plot", communityPlot)
            .number("yield", Math.max(0, yield))
            .build();
    }

    // --- plumbing ----------------------------------------------------------------------------

    private static void publish(Player player, Level level, QuestAction action, BlockPos position,
                                QuestActionSubject subject) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return;
        publish(serverPlayer, action, serverLevel, position, subject);
    }

    private static void publishAtPlayer(ServerPlayer player, QuestAction action, QuestActionSubject subject) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        publish(player, action, level, player.blockPosition(), subject);
    }

    private static void publish(ServerPlayer player, QuestAction action, ServerLevel level,
                                BlockPos position, QuestActionSubject subject) {
        QuestObjectiveWatcher.onQuestAction(player, action, level, position, subject);
    }
}
