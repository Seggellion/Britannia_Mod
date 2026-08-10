package com.seggellion.britannia_mod.service.guild;

import java.util.UUID;

/**
 * One training purchase, as sent to {@code POST /api/guild_training}.
 *
 * <h2>Intent only</h2>
 * The server re-derives everything it can: the city comes from the World NPC, the taught-skill
 * check from that NPC's service type, and the resulting value is clamped to the skill cap. The
 * client cannot name a city, teach itself an untaught skill, or push a value past the cap.
 *
 * <p>{@code goldPaid} is necessarily trusted — coins are Minecraft inventory items and Rails has
 * no view of them — but it is not trusted blindly: Rails verifies the one-gold-per-tenth identity
 * against {@code purchasedTenths}, so the only figure the client alone can measure is still
 * checkable against the one it cannot forge.
 *
 * <h2>idempotencyKey</h2>
 * Generated once per purchase attempt and reused across any retry of <em>that same</em> attempt,
 * so a retry after a timeout cannot charge twice. A new attempt gets a new key. This is what the
 * unique index on {@code (idempotency_key, shard_id)} enforces.
 */
public record GuildTrainingRequest(
        UUID worldNpcPublicId,
        UUID playerUuid,
        String playerName,
        String skillSlug,
        int purchasedTenths,
        int goldPaid,
        UUID idempotencyKey
) {
    public static GuildTrainingRequest of(
            UUID worldNpcPublicId, UUID playerUuid, String playerName, GuildTrainingQuote quote
    ) {
        return new GuildTrainingRequest(
                worldNpcPublicId,
                playerUuid,
                playerName,
                quote.skillSlug(),
                quote.purchasedTenths(),
                // One gold per tenth. Sent explicitly rather than implied so Rails can verify the
                // identity rather than assume it.
                quote.costGold(),
                UUID.randomUUID()
        );
    }
}
