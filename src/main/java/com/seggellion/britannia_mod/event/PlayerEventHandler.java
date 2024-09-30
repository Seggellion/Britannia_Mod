package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.magic.HealSpell;
import com.seggellion.britannia_mod.magic.MagicArrowSpell;
import com.seggellion.britannia_mod.magic.NightSightSpell;
import com.seggellion.britannia_mod.magic.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public class PlayerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final NightSightSpell NIGHT_SIGHT_SPELL = new NightSightSpell();
    private static final HealSpell HEAL_SPELL = new HealSpell();
    private static final MagicArrowSpell MAGIC_ARROW_SPELL = new MagicArrowSpell();

    @SubscribeEvent
    public void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        handleSpellCasting(event.getEntity(), event.getEntity().getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        handleSpellCasting(event.getEntity(), event.getEntity().getMainHandItem(), InteractionHand.OFF_HAND);
    }

    private void handleSpellCasting(Player player, ItemStack itemStack, InteractionHand hand) {
        Spell spell = getSpellFromItem(itemStack);

        if (spell != null) {
            if (hand == InteractionHand.MAIN_HAND) {
                // Right-click -> Self cast
                if (player instanceof ServerPlayer serverPlayer) {
                    if (!spell.castSelf(serverPlayer)) {
                        player.sendSystemMessage(Component.literal("Failed to cast spell!"));
                    }
                }
            } else if (hand == InteractionHand.OFF_HAND) {
                // Left-click -> Target cast
                Player target = findPlayerInCrosshairs(player, 20);
                if (target != null && player instanceof ServerPlayer serverPlayer) {
                    if (!spell.castTarget(serverPlayer, target)) {
                        player.sendSystemMessage(Component.literal("Failed to cast spell on target!"));
                    }
                }
            }
        }
    }

    private Spell getSpellFromItem(ItemStack itemStack) {
        if (NIGHT_SIGHT_SPELL.isSpellItem(itemStack)) {
            return NIGHT_SIGHT_SPELL;
        } else if (HEAL_SPELL.isSpellItem(itemStack)) {
            return HEAL_SPELL;
        } else if (MAGIC_ARROW_SPELL.isSpellItem(itemStack)) {
            return MAGIC_ARROW_SPELL;
        }
        return null;
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
