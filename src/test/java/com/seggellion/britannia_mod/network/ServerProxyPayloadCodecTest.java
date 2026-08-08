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
            "minecraft:bread", "Bread", 12, "copper", "minecraft:bread");
        var payload = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7, List.of(product));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ClientboundOpenNpcScreenPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void oversizedCatalogAndNegativePricesAreRejected() {
        var product = new ClientboundOpenNpcScreenPayload.ProductData(
            "minecraft:bread", "Bread", 1, "copper", "");
        var tooMany = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7,
            java.util.Collections.nCopies(ClientboundOpenNpcScreenPayload.MAX_PRODUCTS + 1, product));
        assertThrows(IllegalArgumentException.class, () -> ClientboundOpenNpcScreenPayload.STREAM_CODEC.encode(
            new FriendlyByteBuf(Unpooled.buffer()), tooMany));

        var invalid = new ClientboundOpenNpcScreenPayload(
            NpcType.MERCHANT, "Baker", "Britain", 7,
            List.of(new ClientboundOpenNpcScreenPayload.ProductData(
                "minecraft:bread", "Bread", -1, "copper", "")));
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
