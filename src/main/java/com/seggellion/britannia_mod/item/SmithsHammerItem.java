package com.seggellion.britannia_mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class SmithsHammerItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    public SmithsHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        LOGGER.info("USED HAMMER!");
        // 1. Check if the block clicked is an Anvil
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.is(Blocks.ANVIL)) {
            return InteractionResult.PASS; // Not an anvil, do normal item behavior
        }

        // 2. Prevent the default vanilla Anvil UI from opening
        // We return SUCCESS on the client to visually swing the arm and stop further processing
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS; 
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack offHandItem = serverPlayer.getOffhandItem();

        // 3. Crosscheck against the metal material
        // We use NeoForge's built-in tag for ingots (c:ingots). 
        // You can replace this with a custom tag like ModTags.Items.BRITANNIA_METALS if you have specific materials.
        if (offHandItem.is(Tags.Items.INGOTS)) {
                    LOGGER.info("USED HAMMER!");
            // Extract the registry name of the ingot to tell the UI what material we are using
            // String ingotId = BuiltInRegistries.ITEM.getKey(offHandItem.getItem()).toString();
            
            // TODO: Send payload to client to open the Britannia Blacksmithy Screen
            // NetworkHandler.sendToPlayer(serverPlayer, new OpenBlacksmithyScreenS2CPayload(ingotId));

            return InteractionResult.SUCCESS;
        } else {
            // Player clicked an anvil with the hammer, but has no metal to work with
            serverPlayer.displayClientMessage(
                Component.literal("You must hold metal ingots in your offhand to smith here.")
                    .withStyle(ChatFormatting.RED), 
                true // true sends it to the action bar above the hotbar instead of chat
            );
            return InteractionResult.CONSUME; 
        }
    }
}