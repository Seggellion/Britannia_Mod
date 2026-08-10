package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the training screen needs, computed on the server. Guildmaster milestone 6.
 *
 * <h2>Why the server does the arithmetic</h2>
 * The client could not do it even if it were trusted to. It has no skill definitions — {@code
 * ClientSkillTable} holds the viewing player's values keyed by slug with no names and no caps —
 * and no view of the guild's taught-skill set, which lives in the server-only registry cache. So
 * every figure here is derived server-side and sent ready to draw.
 *
 * <p>None of it is authoritative. It is what to <em>display</em>: the screen sends back a slug and
 * nothing else, and the whole quote is recomputed from scratch before a coin moves. A stale price
 * on screen costs the player nothing — they are charged what the server derives at commit time,
 * which is why the row can afford to be a snapshot.
 */
public record GuildTrainingOpenS2CPayload(
        int entityId,
        String guildDisplayName,
        String npcName,
        List<Offer> offers
) implements CustomPacketPayload {
    public static final int MAX_LABEL_BYTES = 128;
    public static final int MAX_SLUG_BYTES = 64;
    /** RunUO's largest guild teaches eleven; bounded well above that and far below abuse. */
    public static final int MAX_OFFERS = 64;

    /**
     * One trainable skill as the screen will draw it.
     *
     * @param currentTenths  where the player stands now
     * @param capTenths      the effective ceiling: the stricter of 40.0 and the skill's own maximum
     * @param affordableGold what this player can actually buy right now, in gold — already the
     *                       minimum of their purse and the remaining headroom, so the screen never
     *                       has to decide affordability and never has to know the coin ratio
     */
    public record Offer(String slug, String label, int currentTenths, int capTenths, int affordableGold) {
        public boolean atCap() {
            return currentTenths >= capTenths;
        }

        /** One gold per tenth, so the price of the whole offer is the tenths it grants. */
        public int costGold() {
            return affordableGold;
        }

        public boolean purchasable() {
            return !atCap() && affordableGold > 0;
        }
    }

    public GuildTrainingOpenS2CPayload {
        offers = List.copyOf(offers);
    }

    public static final Type<GuildTrainingOpenS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "guild_training_open")
    );

    public static final StreamCodec<FriendlyByteBuf, GuildTrainingOpenS2CPayload> STREAM_CODEC =
            StreamCodec.of(GuildTrainingOpenS2CPayload::encode, GuildTrainingOpenS2CPayload::decode);

    private static void encode(FriendlyByteBuf buffer, GuildTrainingOpenS2CPayload payload) {
        buffer.writeVarInt(payload.entityId);
        ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.guildDisplayName, MAX_LABEL_BYTES);
        ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.npcName, MAX_LABEL_BYTES);
        buffer.writeVarInt(payload.offers.size());
        for (Offer offer : payload.offers) {
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, offer.slug(), MAX_SLUG_BYTES);
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, offer.label(), MAX_LABEL_BYTES);
            buffer.writeVarInt(offer.currentTenths());
            buffer.writeVarInt(offer.capTenths());
            buffer.writeVarInt(offer.affordableGold());
        }
    }

    private static GuildTrainingOpenS2CPayload decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        String guild = ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_LABEL_BYTES);
        String npc = ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_LABEL_BYTES);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_OFFERS) {
            throw new IllegalArgumentException("Invalid guild training offer count " + count);
        }
        List<Offer> offers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            offers.add(new Offer(
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_SLUG_BYTES),
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_LABEL_BYTES),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            ));
        }
        return new GuildTrainingOpenS2CPayload(entityId, guild, npc, offers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
