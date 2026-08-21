package com.seggellion.britannia_mod.deposit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The two ways a player can reach a deposit, both answered by the same rule.
 *
 * <h2>Left click, in adventure</h2>
 * The world is held in adventure mode, so an ordinary player cannot break anything and a deposit
 * would be inert. The established answer here is the left click: {@code WildResourceInteractionHandler}
 * harvests an oyster this way and {@code FlowerInteractionHandler} cuts back a flower this way,
 * both by cancelling the event and calling a service. This does the same.
 *
 * <p>Note what is <em>not</em> done: nothing here grants {@code mayBuild}, changes a game mode, or
 * relaxes adventure for {@code minecraft:clay}. Adventure is left exactly as strict as it was, and
 * the deposit is an authorized exception inside it rather than a hole in it.
 *
 * <p>"Anybody else" is not "any ServerPlayer". A fake player is one, so the actor question is
 * asked inside {@link ManagedDepositExtraction} rather than here (milestone 6): the break is
 * cancelled first and refused second, which leaves the bed standing rather than letting automation
 * destroy what it is not allowed to earn from.
 *
 * <h2>Break, anywhere</h2>
 * A break of a deposit is taken over rather than allowed. An operator in creative may remove a
 * badly placed bed and is left alone; anybody else is routed through the same extraction, so a
 * deposit reached from survival obeys the same tool rule and produces the same single accounted
 * yield instead of the block's vanilla drop. The block has no loot table, so a cancelled break
 * cannot drop anything either way — this is the rule stated rather than inherited.
 *
 * <p>{@link EventPriority#HIGH} for the same reason the Mining gate uses it: it must land before
 * {@code CustomBlockBreakHandler}, which mutates the world inside its NORMAL-priority listener.
 */
public final class ManagedDepositInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            return;
        }
        ManagedDepositExtraction.Result result = ManagedDepositExtraction.extract(
                level, event.getPos(), player, player.getMainHandItem());
        if (result.concernsADeposit()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || ManagedDeposits.resolve(event.getState()).isEmpty()) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        event.setCanceled(true);
        ManagedDepositExtraction.extract(level, event.getPos(), player, player.getMainHandItem());
    }
}
