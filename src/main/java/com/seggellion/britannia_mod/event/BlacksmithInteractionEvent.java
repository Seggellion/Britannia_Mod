package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.BlackSmithsHammerItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.network.payload.OpenBlacksmithGuiS2CPayload;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundSource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = "britannia_mod")
public class BlacksmithInteractionEvent {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAnvilInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.ANVIL)) {
            return;
        }

        ItemStack mainHand = event.getEntity().getMainHandItem();
        ItemStack offHand = event.getEntity().getOffhandItem();

        if (mainHand.getItem() instanceof BlackSmithsHammerItem) {
            LOGGER.info("Hammer detected on Anvil! Intercepting...");

            if (event.getLevel().isClientSide) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            ServerPlayer player = (ServerPlayer) event.getEntity();

            // Check if the offhand item matches any ingot in our Enum
            UOMetalToolMaterial metal = UOMetalToolMaterial.getMaterialByIngot(offHand.getItem());

            if (metal != null) {
                LOGGER.info("Valid {} Ingot found. Sending UI payload.", metal.getMetalName());

                event.getLevel().playSound(
                        null, 
                        event.getPos(), 
                        ModSounds.ANVIL.get(), 
                        SoundSource.BLOCKS, 
                        1.0F, 1.0F
                );

                // Send the clean string (e.g., "valorite") to the UI payload
                NetworkHandler.sendToPlayer(player, new OpenBlacksmithGuiS2CPayload(metal.getMetalName()));
                
            } else {
                LOGGER.info("No valid ingots in offhand.");
                player.displayClientMessage(
                    Component.literal("You must hold a valid forging metal in your offhand to smith here.")
                        .withStyle(ChatFormatting.RED), 
                    true 
                );
            }
            
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}