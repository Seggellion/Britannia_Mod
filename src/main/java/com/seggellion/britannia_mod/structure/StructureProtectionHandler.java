package com.seggellion.britannia_mod.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

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
 *
 * <h2>What a held item does here: nothing</h2>
 * This used to begin by returning outright for anyone holding a {@code QualityToolItem} or a
 * {@code TwoHandedAxeItem}, which meant a stranger with a project pickaxe walked through every
 * rule below -- ownership, the perimeter foundation, the lot block, all of it. The exemption was
 * added in {@code 5b583c69}, older than {@link HouseBuildRights} itself, back when
 * {@code CityGameModeHandler} put a tool holder into survival so they could mine at all; without
 * it those force-switched miners tripped the survival refusal that used to live in the
 * OUTSIDE_ANY_HOUSE branch. Both halves of that arrangement are gone -- miners stay in adventure
 * and dig through {@code can_break} -- so the exemption protected nothing and cost house
 * ownership its authority over anybody carrying the right item.
 *
 * <p>The rule now is location and ownership, full stop. A tool decides which blocks may enter
 * the destruction lifecycle ({@code ExtractionToolPredicates}) and the resource systems decide
 * whether an attempt earns anything ({@code MiningBreakGate}, {@code ManagedDepositExtraction},
 * {@code WoodChopEventHandler}); neither of those is a licence to work somebody else's house, and
 * this class no longer reads the player's hands to find out.
 *
 * <p><b>This class is not the only house authority, and cannot be.</b> Every managed resource
 * path cancels the break and mutates the world from its own listener, and all of them run before
 * this one -- {@code MiningGateHandler} at HIGH, {@code CustomBlockBreakHandler} and
 * {@code WoodChopEventHandler} earlier in registration order at NORMAL. A cancelled event never
 * reaches this listener, so for a managed resource the refusal here would arrive after the block
 * had already been taken. Each of those paths therefore asks {@link HouseBuildRights} itself, the
 * same authority this class uses; there is one copy of the region logic and several callers, not
 * several copies.
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

        HouseBuildRights.Decision decision =
                HouseBuildRights.evaluateBreak(event.getLevel() instanceof Level level
                        ? level : player.level(), event.getPos(), player);

        switch (decision) {
            case OUTSIDE_ANY_HOUSE -> {
                // No house is involved, so the house rules have no opinion about the block. Two
                // world rules still live here, and neither reads the player's hands.
                //
                // The survival refusal is the older one, and it is not dead code: every managed
                // resource path -- the Mining flow, the deposit service, the wood handler -- takes
                // its break over and cancels the event before this listener is ever reached, so
                // what actually arrives here in survival is somebody breaking ordinary world
                // scenery, which is refused now as it always was.
                if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                        && !holdsAnExtractionTool(player)
                        && !(event.getLevel() instanceof ServerLevel placedIn
                                && com.seggellion.britannia_mod.mining.MiningProvenance
                                        .isPlayerPlaced(placedIn, event.getPos()))) {
                    // Two exceptions, and note what they are exceptions to: this is the world
                    // rule for open ground, never the house rule. An extraction tool means the
                    // player is doing resource work rather than vandalising scenery, and a block
                    // they put there themselves is their own construction -- provenance exists to
                    // say so, and without it the rule would forbid picking up the wall you just
                    // built.
                    refuse(event, player, "You can't break blocks outside your house.");
                } else if (SurvivalZoneHandler.hasLentBuildRights(player)) {
                    // The reach problem, and a statement about the house rather than the block:
                    // the ability was lent for the inside of a building and does not know which
                    // way its holder is pointing.
                    refuse(event, player, "You can only build inside your own house.");
                }
            }
            case ALLOWED -> {
                // The ability lent by SurvivalZoneHandler is what permits this; un-cancelling here
                // would not, because destroyBlock re-checks the restriction after this event
                // regardless. So nothing is done -- except for two rules that only make sense here,
                // in the pipeline where a real hand swings at a real block.
                if (HouseBuildRights.bareHanded(player)) {
                    // Nothing in a house comes apart bare-handed. Refused before the block is
                    // removed rather than restored afterwards, so a block entity and a container's
                    // contents are never briefly destroyed on the way.
                    refuse(event, player, HouseBuildRights.Decision.DENIED_BARE_HANDED.message());
                } else if (event.getLevel() instanceof ServerLevel serverLevel
                        && HouseObjectRemoval.isStatefulHouseObject(serverLevel, event.getPos())
                        && HouseObjectRemoval.take(serverLevel, player, event.getPos())) {
                    // The one class of block whose ordinary break would lose what it was carrying.
                    event.setCanceled(true);
                }
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

    /**
     * Whether the player is carrying one of the project's resource-extraction tools.
     *
     * <p>Read in exactly one place: the OUTSIDE_ANY_HOUSE world rule above. This is emphatically
     * <em>not</em> the old privilege bypass, which sat at the top of the method and let a tool
     * skip every house question below it. The distinction is the whole point of this class now:
     *
     * <pre>
     *   inside a house    ownership decides, and no held item changes the answer
     *   open ground       the tool distinguishes resource work from vandalism
     * </pre>
     *
     * <p>It matters only for survival, and production has no survival players — the zone rule
     * holds everyone in adventure — so in practice this speaks for operators and for the test
     * harness. It is kept because the world rule it modulates is real: a survival player with
     * empty hands still cannot pull the countryside apart, which is what
     * {@code StructureRegionRehydrationGameTests} pins.
     */
    private static boolean holdsAnExtractionTool(ServerPlayer player) {
        net.minecraft.world.item.Item held = player.getMainHandItem().getItem();
        return held instanceof com.seggellion.britannia_mod.item.QualityToolItem
                || held instanceof com.seggellion.britannia_mod.item.QualityShovelItem
                || held instanceof com.seggellion.britannia_mod.item.TwoHandedAxeItem;
    }

    private static void refuse(BlockEvent.BreakEvent event, ServerPlayer player, String message) {
        event.setCanceled(true);
        tell(player, message);
    }

    private static void tell(Player player, String message) {
        if (message == null) return;
        player.sendSystemMessage(Component.literal(message));
    }
}
