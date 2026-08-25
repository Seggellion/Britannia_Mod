package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The MerchantSpawnScreen saves its selection through this payload; the farmer key must survive
 * the wire byte-for-byte like every other merchant key (the codec is length-prefixed UTF-8 with
 * no vocabulary of its own -- normalization happens in the block entity, not here).
 */
class MerchantSpawnConfigPayloadFarmerTest {

    @Test
    void theFarmerSelectionRoundTripsThroughTheWire() {
        MerchantSpawnConfigC2SPayload sent =
                new MerchantSpawnConfigC2SPayload(new BlockPos(7, 64, -3), "farmer", "Britain", 2);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MerchantSpawnConfigC2SPayload.STREAM_CODEC.encode(buf, sent);
            MerchantSpawnConfigC2SPayload received = MerchantSpawnConfigC2SPayload.STREAM_CODEC.decode(buf);
            assertEquals(sent, received);
            assertEquals("farmer", received.merchantType());
        } finally {
            buf.release();
        }
    }

    @Test
    void theExistingMerchantSelectionsStillRoundTrip() {
        for (String key : new String[] { "baker", "tavernkeeper", "costermonger" }) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MerchantSpawnConfigC2SPayload sent =
                        new MerchantSpawnConfigC2SPayload(BlockPos.ZERO, key, "Trinsic", 0);
                MerchantSpawnConfigC2SPayload.STREAM_CODEC.encode(buf, sent);
                assertEquals(sent, MerchantSpawnConfigC2SPayload.STREAM_CODEC.decode(buf));
            } finally {
                buf.release();
            }
        }
    }
}
