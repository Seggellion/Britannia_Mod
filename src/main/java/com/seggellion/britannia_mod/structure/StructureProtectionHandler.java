package com.seggellion.britannia_mod.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import net.minecraft.world.item.ItemStack;

/**
 * What a player may do to the blocks of a house.
 *
 * <p>The right to build at all is lent by {@link SurvivalZoneHandler} as a single ability flag;
 * this is the rule about what that right covers, and it is the only place a house says no.
 *
 * <pre>
 *   outside any house        the world's own rules, untouched
 *   somebody else's house    refused
 *   your own house           allowed, except:
 *       the perimeter foundation   the permanent outline of the building
 *       the lot block              the house's ownership record
 * </pre>
 *
 * <p>Both exceptions are decided by {@link HouseBuildRights}, which reads block tags rather than
 * naming block ids -- and the tags were built from what each block actually does in the authored
 * structures, because the names do not separate the perimeter from the floor. The interior floor
 * slab is deliberately <em>not</em> protected: cutting down through it is how an owner starts a
 * basement.
 *
 * <p>The playbook asked for the creative early-return below to be inverted, on the reasoning that
 * an owner inside their own house would be in creative and would still need the foundation test.
 * That model was not adopted -- owners stay in adventure and are lent {@code mayBuild} -- so the
 * only players in creative are operators, and the early return means what it always meant.
 */
public class StructureProtectionHandler {

    public static void register() {
        NeoForge.EVENT_BUS.register(new StructureProtectionHandler());
    }

    /* ------------------------------------------------------------------ */
    /*  Breaking                                                           */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        ItemStack heldItem = player.getMainHandItem();
        boolean isAllowedTool = (heldItem.getItem() instanceof QualityToolItem)
                || (heldItem.getItem() instanceof TwoHandedAxeItem);
        if (isAllowedTool) return;

        HouseBuildRights.Decision decision =
                HouseBuildRights.evaluateBreak(event.getLevel() instanceof Level level
                        ? level : player.level(), event.getPos(), player);

        switch (decision) {
            case OUTSIDE_ANY_HOUSE -> {
                // No house is involved, so the house rules have no opinion. The world's own
                // protection still applies: adventure mode refuses this on its own, and a survival
                // player without a mod tool is refused here as they always were.
                if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                    refuse(event, player, "You can't break blocks outside your house.");
                } else if (SurvivalZoneHandler.hasLentBuildRights(player)) {
                    // Standing in their own house and reaching past its edge. The ability was lent
                    // for the house, and it does not know which way the player is pointing.
                    refuse(event, player, "You can only build inside your own house.");
                }
            }
            case ALLOWED -> {
                // Nothing to do. The ability lent by SurvivalZoneHandler is what permits it;
                // un-cancelling here would not, because destroyBlock re-checks the restriction
                // after this event regardless.
            }
            default -> refuse(event, player, decision.message());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Placing                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Nobody remodels a house that is not theirs.
     *
     * <p>Adventure mode already stops most of this on its own, and an owner is the only player
     * lent the ability that gets past it -- but "most" is not a rule. An operator dropping to
     * survival, a mod tool, or any future path to {@code mayBuild} would otherwise let a stranger
     * build inside somebody's living room.
     *
     * <p>High priority so the refusal lands before anything downstream acts on the placement.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        Level level = event.getLevel() instanceof Level world ? world : player.level();
        HouseBuildRights.Decision decision =
                HouseBuildRights.evaluatePlace(level, event.getPos(), player);

        if (decision == HouseBuildRights.Decision.DENIED_NOT_OWNER) {
            event.setCanceled(true);
            tell(player, decision.message());
        } else if (decision == HouseBuildRights.Decision.OUTSIDE_ANY_HOUSE
                && SurvivalZoneHandler.hasLentBuildRights(player)) {
            // Same reach problem as breaking: the house lent the ability, not the ground outside it.
            event.setCanceled(true);
            tell(player, "You can only build inside your own house.");
        }
    }

    /* ------------------------------------------------------------------ */

    private static void refuse(BlockEvent.BreakEvent event, ServerPlayer player, String message) {
        event.setCanceled(true);
        tell(player, message);
    }

    private static void tell(Player player, String message) {
        if (message == null) return;
        player.sendSystemMessage(Component.literal(message));
    }
}
