package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TrainingDummyBlock;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.TrainingDummyHitC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** Captures attacks that Adventure mode suppresses before a server block-click packet exists. */
@EventBusSubscriber(modid = BritanniaMod.MODID, value = Dist.CLIENT)
public final class TrainingDummyClientAttackHandler {
    private TrainingDummyClientAttackHandler() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() != GameType.ADVENTURE
                || minecraft.player.isShiftKeyDown()
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof TrainingDummyBlock)) {
            return;
        }

        NetworkHandler.sendToServer(new TrainingDummyHitC2SPayload(hit.getBlockPos()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
