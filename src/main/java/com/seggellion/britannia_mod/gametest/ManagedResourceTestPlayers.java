package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * A named, ordinary, survival player for the managed-resource tests.
 *
 * <h2>Why this exists</h2>
 * The extraction tests needed a player with a name of their own — a house owner has to be
 * distinguishable from a stranger — and the only convenient way to fabricate one was
 * {@code FakePlayerFactory.get}. So every clay, silica and sandstone extraction test was quietly
 * driving the code with a NeoForge fake player: the exact actor milestone 6 has to refuse. The
 * tests were not wrong about extraction, but they were asserting it about the wrong kind of
 * subject, and they were the reason nobody noticed automation could work a deposit bed.
 *
 * <p>The other trap is the game mode. {@code GameTestServer} builds its level with
 * {@link GameType#CREATIVE}, so a player that does not say otherwise is genuinely in creative —
 * which is why a creative operator minting purity ore also went unseen. Both defaults are opted out
 * of here, once, so no test has to remember either.
 *
 * <p>Construction mirrors {@code GameTestHelper.makeMockServerPlayerInLevel} because that is the
 * arrangement known to work in this harness, with two differences: the profile is named by the
 * caller, and nothing overrides {@code isCreative()}, so the player reports what its game mode
 * actually is.
 */
public final class ManagedResourceTestPlayers {

    private ManagedResourceTestPlayers() {
    }

    /** A real, named player in survival: the subject an extraction rule is written about. */
    public static ServerPlayer survival(ServerLevel level, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
