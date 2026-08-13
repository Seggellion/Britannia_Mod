package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.network.payload.grabby.C2SConfirmGrabbyDestructionPayload;
import com.seggellion.britannia_mod.network.payload.grabby.S2COpenGrabbyDestructionPromptPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two Grabby payloads survive the wire.
 *
 * <p>Cheap to check and worth checking: a codec that writes three fields and reads two is a bug that
 * only ever shows up as a mysterious disconnect, and the confirmation payload is the one place a
 * client's bytes reach a destructive server action.
 */
class GrabbyPayloadCodecTest {
    private static final UUID SESSION = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void theConfirmationPayloadRoundTrips() {
        FriendlyByteBuf buffer = buffer();
        C2SConfirmGrabbyDestructionPayload.STREAM_CODEC.encode(
                buffer, new C2SConfirmGrabbyDestructionPayload(SESSION));

        C2SConfirmGrabbyDestructionPayload decoded =
                C2SConfirmGrabbyDestructionPayload.STREAM_CODEC.decode(buffer);

        assertEquals(SESSION, decoded.sessionId());
        assertEquals(0, buffer.readableBytes(), "the codec must consume exactly what it wrote");
    }

    @Test
    void theConfirmationPayloadCarriesNothingButTheSessionId() {
        // Its whole security property: no position, no block, no target identity to forge.
        assertEquals(1, C2SConfirmGrabbyDestructionPayload.class.getRecordComponents().length);
        assertEquals("sessionId",
                C2SConfirmGrabbyDestructionPayload.class.getRecordComponents()[0].getName());
    }

    @Test
    void thePromptPayloadRoundTrips() {
        FriendlyByteBuf buffer = buffer();
        S2COpenGrabbyDestructionPromptPayload.STREAM_CODEC.encode(
                buffer, new S2COpenGrabbyDestructionPromptPayload(SESSION, "Wooden Chair", 4));

        S2COpenGrabbyDestructionPromptPayload decoded =
                S2COpenGrabbyDestructionPromptPayload.STREAM_CODEC.decode(buffer);

        assertEquals(SESSION, decoded.sessionId());
        assertEquals("Wooden Chair", decoded.objectName());
        assertEquals(4, decoded.occupiedSlots());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void aPromptForSomethingWithNoContentsRoundTrips() {
        FriendlyByteBuf buffer = buffer();
        S2COpenGrabbyDestructionPromptPayload.STREAM_CODEC.encode(
                buffer, new S2COpenGrabbyDestructionPromptPayload(SESSION, "Wine Bottle", 0));

        assertEquals(0, S2COpenGrabbyDestructionPromptPayload.STREAM_CODEC.decode(buffer).occupiedSlots());
    }

    @Test
    void theTwoPayloadTypesHaveDistinctIdentifiers() {
        assertTrue(C2SConfirmGrabbyDestructionPayload.TYPE_ID.getPath().length() > 0);
        assertEquals("britannia_mod", C2SConfirmGrabbyDestructionPayload.TYPE_ID.getNamespace());
        assertEquals("britannia_mod", S2COpenGrabbyDestructionPromptPayload.TYPE_ID.getNamespace());
        assertTrue(!C2SConfirmGrabbyDestructionPayload.TYPE_ID.equals(
                        S2COpenGrabbyDestructionPromptPayload.TYPE_ID),
                "two payloads sharing an id would silently shadow each other");
    }

    @Test
    void aMissingSessionIdIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new C2SConfirmGrabbyDestructionPayload(null));
        assertThrows(NullPointerException.class,
                () -> new S2COpenGrabbyDestructionPromptPayload(null, "x", 0));
    }

    @Test
    void aNegativeSlotCountIsNormalisedRatherThanTrusted() {
        assertEquals(0, new S2COpenGrabbyDestructionPromptPayload(SESSION, "x", -5).occupiedSlots());
    }

    @Test
    void aMissingObjectNameBecomesEmptyRatherThanNull() {
        assertEquals("", new S2COpenGrabbyDestructionPromptPayload(SESSION, null, 0).objectName());
    }
}
