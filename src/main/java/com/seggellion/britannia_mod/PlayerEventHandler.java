package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.seggellion.britannia_mod.magic.ManaHandler;
import com.seggellion.britannia_mod.magic.NightSightSpell;

public class PlayerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final NightSightSpell NIGHT_SIGHT_SPELL = new NightSightSpell();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        handlePlayerPosition(event.getEntity());  // Handle player position
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

    // Handle right-clicking with the spell item
    @SubscribeEvent
    public void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack itemInHand = player.getMainHandItem();  // Get item in main hand

        // Log the event details for clarity
        LOGGER.info("Right Click Event: {}", event);
        LOGGER.info("Player: {}", player);
        LOGGER.info("itemInHand: {}", itemInHand);

        // Check if the item is the spell item
        if (NIGHT_SIGHT_SPELL.isSpellItem(itemInHand)) {
            LOGGER.info("Item is in hand!");

            if (!player.level().isClientSide()) {
                // We are on the server side, so we can cast the spell
                LOGGER.info("Server-side: Attempting to cast Night Sight...");

                // Ensure that the player is an instance of ServerPlayer before casting
                if (player instanceof ServerPlayer serverPlayer) {
                    LOGGER.info("Server-side: Player is a ServerPlayer.");

                    // Cast the spell on the player (self-cast)
                    if (NIGHT_SIGHT_SPELL.castSelf(serverPlayer)) {
                        LOGGER.info("Player {} successfully cast Night Sight on self.", player.getName().getString());
                    } else {
                        LOGGER.info("Player {} failed to cast Night Sight on self (mana or other issue).", player.getName().getString());
                    }
                } else {
                    LOGGER.info("Error: Player is not a ServerPlayer on the server side.");
                }
            } else {
                // We are on the client side, so we log and send a packet to the server if needed
                LOGGER.info("Client-side: Casting spell on self (should send a packet to server).");

                // You can implement this if necessary
                sendCastSpellPacket(player);
            }
        }
    }

    // Handle left-clicking to cast spell on another player
    @SubscribeEvent
    public void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            // Server-side: Cast spell on target player
            Player target = findPlayerInCrosshairs(player, 20);
            if (target != null && NIGHT_SIGHT_SPELL.castTarget((ServerPlayer) player, target)) {
                LOGGER.info("Player {} cast Night Sight on {}", player.getName().getString(), target.getName().getString());
            } else {
                player.sendSystemMessage(Component.literal("No target in crosshairs or spell fizzled!"));
            }
        } else {
            LOGGER.info("Client: Left-click detected, sending packet to server");
            // Client-side: Send packet to server to cast spell on target player
            sendCastSpellPacket(player);
        }
    }

    private Player findPlayerInCrosshairs(Player caster, int range) {
        Vec3 eyePosition = caster.getEyePosition(1.0F);
        Vec3 lookVector = caster.getViewVector(1.0F);
        Vec3 endVector = eyePosition.add(lookVector.scale(range));

        HitResult hitResult = caster.level().clip(new ClipContext(eyePosition, endVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            if (entityHit.getEntity() instanceof Player target) {
                return target;
            }
        }
        return null;
    }

    // Placeholder method to simulate sending a packet from client to server
    private void sendCastSpellPacket(Player player) {
        LOGGER.info("Sending spell cast packet from client to server for player: {}", player.getName().getString());
        // TODO: Implement actual network code to send a packet to the server
    }
}
