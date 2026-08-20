package com.seggellion.britannia_mod.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the world in adventure mode, and lends a house owner the one ability they need to build
 * in their own house.
 *
 * <h2>Why an ability and not a game mode</h2>
 * This used to put an owner into survival while they stood inside their house and back into
 * adventure when they left. The house therefore granted its rights by changing what the player
 * <em>was</em>, which meant the game mode had to be tracked, restored, and got wrong across a
 * logout.
 *
 * <p>The owner now stays in adventure the whole time. Adventure gates block editing on exactly
 * one flag -- {@code Abilities.mayBuild}, read by {@code Player.blockActionRestricted} for
 * breaking and by {@code CommonHooks.onPlaceItemIntoWorld} for placing -- and that flag is
 * independent of everything creative mode is made of. Lending it changes nothing else: no
 * creative inventory ({@code instabuild} is untouched), no flight ({@code mayfly} and
 * {@code flying} are untouched), no invulnerability, and no change to what the player's game
 * mode reports. Blocks still cost an item to place, still drop when broken, still take time and
 * durability.
 *
 * <p>What may then be done with the ability is not decided here -- {@link HouseBuildRights} and
 * {@link StructureProtectionHandler} own that, and they refuse the perimeter foundation and the
 * lot block even to an owner. This class only decides who is lent the ability at all.
 */
public class SurvivalZoneHandler {

    /**
     * Players currently holding the housing exemption.
     *
     * <p>Tracked so the flag is only ever taken back from someone this handler gave it to. A
     * player who can build for some other reason -- an operator in creative, a future permission
     * of some kind -- is left entirely alone.
     */
    private static final Set<UUID> lentBuildRights = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyTo(player);
        }
    }

    /**
     * One player, one tick of this rule.
     *
     * <p>Separate from the loop so the rule can be exercised against a player the loop cannot
     * reach, and so that what it does to a single player is readable on its own.
     */
    public static void applyTo(ServerPlayer player) {
        {
            GameType currentMode = player.gameMode.getGameModeForPlayer();
            if (currentMode == GameType.CREATIVE || currentMode == GameType.SPECTATOR) {
                // An operator. Not ours to manage, and nothing to take back from them.
                revoke(player);
                return;
            }

            // The existing escape hatch: a mod tool grants ordinary survival rights for the work
            // it is for, anywhere. Unchanged.
            boolean isHoldingTool = (player.getMainHandItem().getItem() instanceof QualityToolItem)
                    || (player.getMainHandItem().getItem() instanceof TwoHandedAxeItem);

            if (currentMode != GameType.ADVENTURE && !isHoldingTool) {
                player.setGameMode(GameType.ADVENTURE);
            }

            if (HouseBuildRights.ownsHouseAt(player.level(), player.blockPosition(), player.getUUID())) {
                grant(player);
            } else {
                revoke(player);
            }
        }
    }

    private static void grant(ServerPlayer player) {
        lentBuildRights.add(player.getUUID());
        if (player.getAbilities().mayBuild) return;

        player.getAbilities().mayBuild = true;
        player.onUpdateAbilities();
    }

    private static void revoke(ServerPlayer player) {
        if (!lentBuildRights.remove(player.getUUID())) return;
        if (!player.getAbilities().mayBuild) return;

        // Only ever handing back what this class lent. A player whose current game mode grants
        // mayBuild in its own right keeps it.
        if (player.gameMode.getGameModeForPlayer().isBlockPlacingRestricted()) {
            player.getAbilities().mayBuild = false;
            player.onUpdateAbilities();
        }
    }

    /**
     * Whether this player's ability to build is one this handler lent them.
     *
     * <p>Asked by the protection rules, because a lent right is only good inside the house that
     * earned it. Reach is about five blocks, so an owner standing in their doorway can aim at
     * ground that is not theirs, and the ability does not know where they are pointing.
     */
    public static boolean hasLentBuildRights(net.minecraft.world.entity.player.Player player) {
        return lentBuildRights.contains(player.getUUID());
    }

    /** Test seam: forget who has been lent what, without touching any player. */
    public static void forgetLentRights() {
        lentBuildRights.clear();
    }
}
