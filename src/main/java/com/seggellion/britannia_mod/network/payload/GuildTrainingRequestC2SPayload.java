package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "Train me this skill." Guildmaster milestone 6.
 *
 * <h2>Intent only — deliberately two fields</h2>
 * The client names <em>which</em> Guildmaster and <em>which</em> skill, and nothing else. It does
 * not send a price, an amount, its current skill value, its gold, or the value it expects to end
 * up with. Every one of those is derived server-side:
 *
 * <ul>
 *   <li>the guild, and whether it teaches this skill, from the live registry via
 *       {@code GuildmasterCapability}</li>
 *   <li>the current value from {@code SkillManager}</li>
 *   <li>the gold from a real inventory count</li>
 *   <li>the price from {@code GuildTrainingQuote}, then re-derived again by Rails</li>
 * </ul>
 *
 * <p>A crafted packet can therefore ask to be trained, and that is all it can ask. The worst a
 * forged {@code skillSlug} achieves is a refusal, because the slug is checked against the guild's
 * published taught-skill set before it is priced.
 *
 * <p>{@code entityId} is the network id, resolved back to a live entity and revalidated for
 * distance, liveness and capability by {@code GuildmasterProxyService.resolve} — the same check
 * banking makes. A stale or hostile id resolves to nothing and the request is dropped silently.
 */
public record GuildTrainingRequestC2SPayload(int entityId, String skillSlug) implements CustomPacketPayload {
    /** Comfortably above any real slug; bounded so a hostile packet cannot allocate freely. */
    public static final int MAX_SLUG_BYTES = 64;

    public static final Type<GuildTrainingRequestC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "guild_training_request")
    );

    public static final StreamCodec<FriendlyByteBuf, GuildTrainingRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of(GuildTrainingRequestC2SPayload::encode, GuildTrainingRequestC2SPayload::decode);

    private static void encode(FriendlyByteBuf buffer, GuildTrainingRequestC2SPayload payload) {
        buffer.writeVarInt(payload.entityId);
        ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.skillSlug, MAX_SLUG_BYTES);
    }

    private static GuildTrainingRequestC2SPayload decode(FriendlyByteBuf buffer) {
        return new GuildTrainingRequestC2SPayload(
                buffer.readVarInt(),
                ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_SLUG_BYTES)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
