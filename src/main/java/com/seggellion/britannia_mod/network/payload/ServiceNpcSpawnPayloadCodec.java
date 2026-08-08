package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class ServiceNpcSpawnPayloadCodec {
    static final int MAX_C2S_BYTES = 256;
    static final int MAX_STATE_BYTES = 1_048_576;
    static final int MAX_TYPE_KEY_BYTES = 64;
    static final int MAX_LABEL_BYTES = 128;
    static final int MAX_ERROR_BYTES = 128;
    static final int MAX_CITY_OPTIONS = 4_096;
    static final int MAX_TYPE_OPTIONS = 1_024;

    private ServiceNpcSpawnPayloadCodec() {
    }

    static void requireReadableLimit(FriendlyByteBuf buffer, int limit) {
        if (buffer.readableBytes() > limit) {
            throw new IllegalArgumentException("Service NPC spawn payload exceeds " + limit + " bytes");
        }
    }

    static void writeUtf(FriendlyByteBuf buffer, String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) throw new IllegalArgumentException("String exceeds " + maxBytes + " bytes");
        buffer.writeVarInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    static String readUtf(FriendlyByteBuf buffer, int maxBytes) {
        int length = buffer.readVarInt();
        if (length < 0 || length > maxBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid bounded UTF-8 length " + length);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Malformed UTF-8", exception);
        }
    }
}
