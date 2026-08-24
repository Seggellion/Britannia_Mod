package com.seggellion.britannia_mod.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.seggellion.britannia_mod.network.payload.housing.S2CHouseBuildRightsPayload;
import net.neoforged.neoforge.network.PacketDistributor;
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

    /**
     * A lease belongs to a session, and ends with it.
     *
     * <p>Without this the entry stays in the set for the lifetime of the server, which is two
     * problems rather than one. It grows without bound on a shard people log in and out of; and a
     * returning player would still be holding a lease earned by where they were standing when they
     * left, which the next tick then has to take back. Clearing here makes the invariant simple
     * enough to state: this set holds online players who are standing in their own house, and
     * nobody else.
     *
     * <p>Nothing is sent to the client, because there is no longer a client to send to. The next
     * session starts from {@code false} — {@code ClientHouseBuildRights} clears itself on
     * {@code LoggingIn} — and the first tick grants it again if the player is still at home.
     */
    @SubscribeEvent
    public void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseLease(player.getUUID());
        }
    }

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
        boolean isNew = lentBuildRights.add(player.getUUID());
        if (isNew) {
            tellClient(player, true);
        }
        if (player.getAbilities().mayBuild) return;

        player.getAbilities().mayBuild = true;
        player.onUpdateAbilities();
    }

    private static void revoke(ServerPlayer player) {
        if (!lentBuildRights.remove(player.getUUID())) return;
        tellClient(player, false);
        if (!player.getAbilities().mayBuild) return;

        // Only ever handing back what this class lent. A player whose current game mode grants
        // mayBuild in its own right keeps it.
        if (player.gameMode.getGameModeForPlayer().isBlockPlacingRestricted()) {
            player.getAbilities().mayBuild = false;
            player.onUpdateAbilities();
        }
    }

    /**
     * Tells the player's own client what it has been lent.
     *
     * <p>{@code onUpdateAbilities()} above is not enough and never was:
     * {@code ClientboundPlayerAbilitiesPacket} carries {@code invulnerable}, {@code flying},
     * {@code mayfly} and {@code instabuild}, and not {@code mayBuild}. The client's copy of that
     * flag is written only by {@code GameType.updatePlayerAbilities} on a game-mode change, which
     * for Adventure writes {@code false} once and never revisits it.
     *
     * <p>So the owner's own client kept refusing their break in
     * {@code MultiPlayerGameMode.startDestroyBlock}, before any packet was sent. Placing was
     * unaffected because the client does not gate {@code useItemOn} at all, which is exactly the
     * shape of the report: blocks could be placed inside the house and never broken.
     *
     * <p>Sent only on a transition, not every tick -- this runs twenty times a second for every
     * player on the shard.
     */
    private static void tellClient(ServerPlayer player, boolean granted) {
        // Only where there is a real client that speaks this channel. Three things reaching this
        // method are not one, and each fails differently if it is not screened out:
        //
        //   a fake player            no listener at all
        //   a GameTest mock player   a listener whose Connection has no netty channel, which
        //                            hasChannel() itself dereferences
        //   a vanilla client         a real channel that never negotiated this payload, which
        //                            throws "may not be sent to the client"
        //
        // The last one is why this cannot simply be a null check: it took an entire GameTest batch
        // down when it was.
        if (player.connection == null) {
            return;
        }
        net.minecraft.network.Connection raw = player.connection.getConnection();
        if (raw == null || raw.channel() == null) {
            return;
        }
        if (!player.connection.hasChannel(S2CHouseBuildRightsPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CHouseBuildRightsPayload(granted));
    }

    /**
     * Why there is no login hook here.
     *
     * <p>There was one, re-stating the lease to a freshly connected client, and it was redundant the
     * moment {@link #onLogout} started releasing the lease: a returning player is never in
     * {@code lentBuildRights}, {@code ClientHouseBuildRights} clears itself on {@code LoggingIn}, and
     * so the first {@link #applyTo} tick after login sees a brand-new grant and tells the client
     * about it. Both sides start at false and agree within one tick.
     *
     * <p>It was also the one piece of this system that ran for every player join in the game, and
     * touching a connection that is still being set up inside {@code placeNewPlayer} is not
     * something a housing rule needs to do. Leaving the grant to the tick loop keeps this handler's
     * entire contract "look at where players are standing", which is all it was ever for.
     */


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

    /**
     * Drops a lease without touching the player.
     *
     * <p>Separate from {@link #revoke} because that one also hands the ability back and tells the
     * client, neither of which is possible or meaningful for a player who has gone.
     */
    static boolean releaseLease(java.util.UUID playerId) {
        return lentBuildRights.remove(playerId);
    }

    /** Whether this player currently holds a lease. Diagnostics and tests. */
    public static boolean holdsLease(java.util.UUID playerId) {
        return lentBuildRights.contains(playerId);
    }

    /** Test seam: forget who has been lent what, without touching any player. */
    public static void forgetLentRights() {
        lentBuildRights.clear();
    }
}
