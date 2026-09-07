package com.seggellion.britannia_mod.dirtgathering;

import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The sole server transaction that grants Patch 18's loose dirt commodity. */
public final class DirtGatheringService {
    private DirtGatheringService() {
    }

    public static Result attempt(ServerLevel level, BlockPos position, ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        if (!DirtGatheringTarget.isGatherable(level.getBlockState(position))
                || !tool.is(ToolRegistry.SHOVEL.get())) {
            return Result.NOT_OURS;
        }

        DirtGatheringPolicy.Assessment permission = DirtGatheringPolicy.evaluate(level, position, player);
        if (!permission.allowed()) {
            Component feedback = permission.feedback();
            if (feedback != null) {
                player.displayClientMessage(feedback.copy().withStyle(ChatFormatting.YELLOW), true);
            }
            return Result.DENIED;
        }

        DirtGatheringCooldown.Status cooldown = DirtGatheringCooldown.inspect(player);
        if (!cooldown.ready()) {
            if (DirtGatheringCooldown.claimCooldownFeedback(player)) {
                player.displayClientMessage(
                        Component.translatable(
                                        "message.britannia_mod.dirt_gather.cooldown",
                                        cooldown.remainingSeconds())
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
            return Result.COOLDOWN;
        }

        // Claim the inexhaustible target before creating value. All callbacks run on the server
        // thread, so a repeated packet observes this write before it can grant another item.
        DirtGatheringCooldown.claim(player);

        ItemStack output = new ItemStack(ItemRegistry.DIRT.get());
        // Read the identity before delivering it: Inventory#add EMPTIES the stack it consumed.
        String outputItemId = QuestActionEvents.itemId(output);
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.displayClientMessage(
                Component.translatable("message.britannia_mod.dirt_gather.success")
                        .withStyle(ChatFormatting.GREEN),
                true);
        // Rowan questline M5 (protocol section 2.1): the authoritative success point. Every earlier
        // return -- not ours, denied, still on cooldown -- has already left, so nothing that failed
        // can reach this line, and the dirt is in the player's hands before it is reported.
        QuestActionEvents.dirtGather(player, level, position, outputItemId, tool);
        return Result.GATHERED;
    }

    public enum Result {
        NOT_OURS(false),
        GATHERED(true),
        DENIED(true),
        COOLDOWN(true);

        private final boolean ownedGesture;

        Result(boolean ownedGesture) {
            this.ownedGesture = ownedGesture;
        }

        public boolean ownedGesture() {
            return ownedGesture;
        }
    }
}
