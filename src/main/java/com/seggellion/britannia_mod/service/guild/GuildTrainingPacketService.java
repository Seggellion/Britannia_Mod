package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.GuildTrainingRequestC2SPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Turns a client training request into a purchase, and refuses everything else.
 *
 * <p>Guildmaster milestone 6. The counterpart of {@code BankingTransferPacketService}: the packet
 * carries a network entity id and a skill slug, and this resolves the first into a live entity
 * before handing both to {@link GuildTrainingService}, which revalidates again.
 *
 * <p>Every rejection here is silent. A packet naming an entity that is not a Guildmaster, is out
 * of range, or does not exist is not a player-facing error — it is either a stale click or a
 * crafted packet, and neither deserves a reply that confirms what the server found.
 */
public final class GuildTrainingPacketService {
    private GuildTrainingPacketService() {
    }

    public static void handleTrainingRequest(ServerPlayer player, GuildTrainingRequestC2SPayload payload) {
        if (player == null || payload == null) return;

        String skillSlug = payload.skillSlug();
        if (skillSlug == null || skillSlug.isBlank()) return;

        // Resolved from the player's own level, so a packet cannot reach an entity in a dimension
        // the player is not in.
        Entity entity = player.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof ServiceNpcEntity guildmaster)) return;

        // purchase() re-resolves distance, liveness, world identity and live guild.train capability
        // through GuildmasterProxyService.resolve, and checks the slug against the published
        // taught-skill set before pricing anything. Nothing on this path trusts the packet.
        GuildTrainingService.purchase(player, guildmaster, skillSlug);
    }
}
