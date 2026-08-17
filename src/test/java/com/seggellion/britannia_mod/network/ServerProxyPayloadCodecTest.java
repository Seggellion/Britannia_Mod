package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.payload.QuestActionResultS2CPayload;
import com.seggellion.britannia_mod.npc.NpcType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerProxyPayloadCodecTest {
    @Test
    void serverResolvedCatalogRoundTripsAsBoundedProductData() {
        var product = new ClientboundOpenNpcScreenPayload.ProductData(
            "minecraft:bread", "Bread", 12, "copper", "minecraft:bread", 0L, 0);
        var payload = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7, List.of(product), List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ClientboundOpenNpcScreenPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void buybackNoticesRoundTripAlongsideProducts() {
        var payload = new ClientboundOpenNpcScreenPayload(
            NpcType.TRADER, "Fish Trader", "Trinsic", 9,
            List.of(new ClientboundOpenNpcScreenPayload.ProductData(
                "britannia_mod:cod", "cod", 7, "copper", "", 442L, 1)),
            List.of(new ClientboundOpenNpcScreenPayload.Notice(
                        ClientboundOpenNpcScreenPayload.Notice.COMMODITY_STOCK_CAP_EXCEEDED, "lava fish"),
                    new ClientboundOpenNpcScreenPayload.Notice(
                        ClientboundOpenNpcScreenPayload.Notice.TREASURY_INSUFFICIENT, "")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ClientboundOpenNpcScreenPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void oversizedNoticeListIsRejected() {
        var notice = new ClientboundOpenNpcScreenPayload.Notice(
            ClientboundOpenNpcScreenPayload.Notice.TREASURY_INSUFFICIENT, "");
        var tooMany = new ClientboundOpenNpcScreenPayload(
            NpcType.TRADER, "Fish Trader", "Trinsic", 9, List.of(),
            java.util.Collections.nCopies(ClientboundOpenNpcScreenPayload.MAX_NOTICES + 1, notice));
        assertThrows(IllegalArgumentException.class, () -> ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(
            new FriendlyByteBuf(Unpooled.buffer()), tooMany));

        assertThrows(IllegalArgumentException.class,
            () -> new ClientboundOpenNpcScreenPayload.Notice("", "cod"));
    }

    @Test
    void oversizedCatalogAndNegativePricesAreRejected() {
        var product = new ClientboundOpenNpcScreenPayload.ProductData(
            "minecraft:bread", "Bread", 1, "copper", "", 0L, 0);
        var tooMany = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7,
            java.util.Collections.nCopies(ClientboundOpenNpcScreenPayload.MAX_PRODUCTS + 1, product), List.of());
        assertThrows(IllegalArgumentException.class, () -> ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(
            new FriendlyByteBuf(Unpooled.buffer()), tooMany));

        var invalid = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7,
            List.of(new ClientboundOpenNpcScreenPayload.ProductData(
                "minecraft:bread", "Bread", -1, "copper", "", 0L, 0)), List.of());
        assertThrows(IllegalArgumentException.class, () -> ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(
            new FriendlyByteBuf(Unpooled.buffer()), invalid));
    }

    @Test
    void clientboundProxyPayloadsHaveNoCredentialFields() {
        assertNoCredentialComponents(ClientboundOpenNpcScreenPayload.class);
        assertNoCredentialComponents(QuestActionResultS2CPayload.class);
    }

    private static void assertNoCredentialComponents(Class<?> payloadType) {
        for (var component : payloadType.getRecordComponents()) {
            assertFalse(component.getName().matches("(?i).*(secret|token|credential|authorization).*"),
                () -> payloadType.getName() + " exposes credential-like component " + component.getName());
        }
    }
}
