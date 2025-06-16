package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.magic.Spell;
import com.seggellion.britannia_mod.magic.SpellRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

public class PlayerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        handlePlayerPosition(event.getEntity());

    }

    private void handlePlayerPosition(Player player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            double x = serverPlayer.getX(), z = serverPlayer.getZ();
            if (x <= 0) teleportPlayer(serverPlayer, 14999, serverPlayer.getY(), z);
            else if (x >= 15000) teleportPlayer(serverPlayer, 1, serverPlayer.getY(), z);
            if (z <= 0) teleportPlayer(serverPlayer, x, serverPlayer.getY(), 9998);
            else if (z >= 9999) teleportPlayer(serverPlayer, x, serverPlayer.getY(), 1);
        }
    }

    private void teleportPlayer(ServerPlayer player, double x, double y, double z) {
        LOGGER.info("Teleporting player {} to coordinates: {}, {}, {}", player.getName().getString(), x, y, z);
        player.teleportTo(x, y, z);
    }

    // Detect when the player picks up or interacts with the gold coin
@SubscribeEvent
public void onItemPickup(ItemEntityPickupEvent.Pre event) { // Changed to Pre
    ItemEntity itemEntity = event.getItemEntity();
    if (itemEntity == null || itemEntity.getItem().isEmpty()) {
        LOGGER.warn("ItemEntity is null or empty (minecraft:air), skipping sound.");
        return;
    }
    // Check if the picked up item is a gold coin
    if (itemEntity.getItem().getItem() == ItemRegistry.GOLD_COIN.get()) {
        LOGGER.info("Gold coin detected! Proceeding to play sound.");

        Player player = event.getPlayer();
        Level level = player.level(); // Access the player's world
        
        // Convert player's Vec3 position to a BlockPos by casting to int
        BlockPos playerPos = new BlockPos((int) player.getX(), (int) player.getY(), (int) player.getZ());
        
        // Play the gold coin pickup sound at the player's position
        level.playSound(null, playerPos, ModSounds.GOLD_COIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        LOGGER.info("Gold coin sound played at player position.");
    } else {
        LOGGER.info("Picked up item is not a gold coin.");
    }
}


    // Cancel physical attack with spell items
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        Spell spell = SpellRegistry.getSpell(itemStack);

        // Cancel physical attack if using a spell item
        if (spell != null) {
            LOGGER.info("Preventing physical damage from spell item.");
            event.setCanceled(true);
        }
    }

    // Handle right-click to apply spell effect to the caster
    @SubscribeEvent
    public void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack itemStack = player.getItemInHand(event.getHand());
        Spell spell = SpellRegistry.getSpell(itemStack);

        if (spell != null && player instanceof ServerPlayer serverPlayer) {
            LOGGER.info("Casting spell on self.");
            if (!spell.castSelf(serverPlayer)) {
                player.sendSystemMessage(Component.literal("Failed to cast spell on self!"));
            }
            event.setCanceled(true); // Prevent default right-click action if spell is cast
        }
    }
   

  
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
    
        if (player instanceof ServerPlayer serverPlayer && !level.isClientSide()) {
            // Check if player is in Adventure mode
            if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

                // Check if holding the two-handed axe and the block is a log
                if (heldItem.getItem() == ItemRegistry.TWO_HANDED_AXE.get() && state.is(BlockTags.LOGS)) {
                    // Break the block manually
                    level.destroyBlock(pos, true, player);
                    event.setCanceled(true);
                }
            }
        }
    }

    

}
