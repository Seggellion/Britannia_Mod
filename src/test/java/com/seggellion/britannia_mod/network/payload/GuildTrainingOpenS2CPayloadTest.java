package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload.Offer;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildTrainingOpenS2CPayloadTest {
    private static GuildTrainingOpenS2CPayload sample() {
        return new GuildTrainingOpenS2CPayload(77, "Warrior Guildmaster", "Marcus", List.of(
                new Offer("swordsmanship", "Swordsmanship", 127, 400, 273),
                new Offer("parrying", "Parrying", 400, 400, 0)
        ));
    }

    @Test
    void roundTripsEveryOffer() {
        GuildTrainingOpenS2CPayload payload = sample();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        GuildTrainingOpenS2CPayload.STREAM_CODEC.encode(buffer, payload);

        assertEquals(payload, GuildTrainingOpenS2CPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void anOfferAtTheCeilingIsNotPurchasable() {
        Offer atCap = sample().offers().get(1);

        assertTrue(atCap.atCap());
        assertFalse(atCap.purchasable());
        assertEquals(0, atCap.costGold());
    }

    @Test
    void thePriceIsTheTenthsItGrants() {
        // The identity the UO rule rests on, carried through to what the screen shows.
        Offer offer = sample().offers().get(0);

        assertTrue(offer.purchasable());
        assertEquals(273, offer.costGold());
        assertEquals(400, offer.currentTenths() + offer.affordableGold(),
                "273 gold takes 12.7 exactly to the 40.0 ceiling");
    }

    @Test
    void anOfferWithHeadroomButNoGoldIsNotPurchasable() {
        Offer broke = new Offer("mining", "Mining", 0, 400, 0);

        assertFalse(broke.purchasable());
        assertFalse(broke.atCap(), "not at the cap - simply unaffordable");
    }

    @Test
    void refusesAnOfferCountBeyondTheBound() {
        // The decode bound is the one that matters: encode is ours, decode is whatever arrives.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeUtf("Guild");
        buffer.writeUtf("Npc");
        buffer.writeVarInt(GuildTrainingOpenS2CPayload.MAX_OFFERS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> GuildTrainingOpenS2CPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void offersAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> sample().offers().clear());
    }
}
