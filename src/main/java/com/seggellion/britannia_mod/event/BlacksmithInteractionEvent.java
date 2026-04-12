package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.SmithsHammerItem;
import com.seggellion.britannia_mod.network.payload.OpenBlacksmithGuiS2CPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = "britannia_mod")
public class BlacksmithInteractionEvent {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAnvilInteract(PlayerInteractEvent.RightClickBlock event) {
        // 1. Did they click an anvil?
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.ANVIL)) {
            return;
        }

        ItemStack mainHand = event.getEntity().getMainHandItem();
        ItemStack offHand = event.getEntity().getOffhandItem();

        // 2. Are they holding the Smith's Hammer?
        if (mainHand.getItem() instanceof SmithsHammerItem) {
            LOGGER.info("Hammer detected on Anvil! Intercepting...");

            // Let the client know the action was successful so it swings the arm
            if (event.getLevel().isClientSide) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            ServerPlayer player = (ServerPlayer) event.getEntity();

            // 3. Crosscheck for ingots in the offhand
            if (offHand.is(Tags.Items.INGOTS)) {
                LOGGER.info("Ingots found. Sending UI payload to client.");
                
                // TODO: Send your S2C payload to open the Britannia UI
                String ingotId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
                 NetworkHandler.sendToPlayer(player, new OpenBlacksmithGuiS2CPayload(ingotId));
                
            } else {
                LOGGER.info("No ingots in offhand.");
                player.displayClientMessage(
                    Component.literal("You must hold metal ingots in your offhand to smith here.")
                        .withStyle(ChatFormatting.RED), 
                    true // true sends it to the action bar above the hotbar
                );
            }
            
            // Cancel the rest of the standard interaction pipeline (stops Vanilla Anvil UI)
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}