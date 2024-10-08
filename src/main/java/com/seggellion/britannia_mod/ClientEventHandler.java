package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.SpellCastPayload;
import com.seggellion.britannia_mod.magic.Spell;
import com.seggellion.britannia_mod.magic.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.function.Predicate;

public class ClientEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean wasAttackPressed = false;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        boolean isAttackPressed = mc.options.keyAttack.isDown();

        if (isAttackPressed && !wasAttackPressed) {
            handleLeftClick(mc);
        }

        wasAttackPressed = isAttackPressed;
    }

    private void handleLeftClick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack itemStack = player.getMainHandItem();
        Spell spell = SpellRegistry.getSpell(itemStack);

        if (spell != null) {
            // Perform an entity ray trace
            double reachDistance = 20.0D; // Set the reach distance to 20 blocks
            Vec3 eyePosition = player.getEyePosition(1.0F);
            Vec3 lookVector = player.getLookAngle();
            Vec3 endVec = eyePosition.add(lookVector.scale(reachDistance));

            AABB boundingBox = player.getBoundingBox().expandTowards(lookVector.scale(reachDistance)).inflate(1.0D, 1.0D, 1.0D);
            Predicate<Entity> predicate = entity -> !entity.isSpectator() && entity.isPickable() && entity != player;

            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(mc.level, player, eyePosition, endVec, boundingBox, predicate);

            if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity target) {
                // Log the target's name and distance
                double distanceToTarget = player.distanceTo(target);
                LOGGER.info("Client: Target found: {}, Distance: {}", target.getName().getString(), distanceToTarget);

                // Create and send the payload to the server
                SpellCastPayload payload = new SpellCastPayload(target.getId());
                NetworkHandler.sendToServer(payload);
            } else if (entityHitResult == null) {
                LOGGER.info("Client: No target hit, entityHitResult is null.");
                player.sendSystemMessage(Component.literal("No target in crosshairs, spell fizzles!"));
            } else {
                LOGGER.info("Client: No valid target found for spell.");
                player.sendSystemMessage(Component.literal("No valid target for spell!"));
            }
            // Cancel default attack action to prevent physical damage
            mc.gameMode.stopDestroyBlock();
        } else {
            LOGGER.info("Client: No spell detected on item.");
        }
    }
}
