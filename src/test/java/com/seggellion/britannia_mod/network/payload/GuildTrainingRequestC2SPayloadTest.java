package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guildmaster milestone 6. The payload is two fields on purpose, and the test that matters is the
 * bound: everything else about a training purchase is derived server-side, so this codec is the
 * only place a hostile packet gets to choose a size.
 */
class GuildTrainingRequestC2SPayloadTest {
    @Test
    void roundTripsIntent() {
        GuildTrainingRequestC2SPayload payload = new GuildTrainingRequestC2SPayload(4231, "swordsmanship");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        GuildTrainingRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);

        assertEquals(payload, GuildTrainingRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void roundTripsAHyphenatedSlug() {
        // Rails friendly_id produces these for multi-word skills; the codec must not mangle them.
        GuildTrainingRequestC2SPayload payload = new GuildTrainingRequestC2SPayload(1, "animal-lore");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        GuildTrainingRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);

        assertEquals("animal-lore", GuildTrainingRequestC2SPayload.STREAM_CODEC.decode(buffer).skillSlug());
    }

    @Test
    void refusesToEncodeAnOversizedSlug() {
        GuildTrainingRequestC2SPayload payload =
                new GuildTrainingRequestC2SPayload(1, "a".repeat(GuildTrainingRequestC2SPayload.MAX_SLUG_BYTES + 1));

        assertThrows(IllegalArgumentException.class, () ->
                GuildTrainingRequestC2SPayload.STREAM_CODEC.encode(new FriendlyByteBuf(Unpooled.buffer()), payload));
    }

    @Test
    void refusesToDecodeAnOversizedSlugOffTheWire() {
        // The one that actually matters: encode is ours, decode is whatever arrives.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("a".repeat(GuildTrainingRequestC2SPayload.MAX_SLUG_BYTES + 32));

        assertThrows(RuntimeException.class,
                () -> GuildTrainingRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }
}
