package com.seggellion.britannia_mod.quest.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The frozen M0 fixtures, as bytes and as parsed objects, for the M3 contract tests. */
final class QuestContractFixtures {
    static final String RESOURCE_ROOT = "quest_contract/v1/";
    static final String DELIVERY_UUID = "6f1d0c8e-3c2f-4d0a-9a9b-2b0f6f5a8e01";
    static final String PLAYER_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    static final String REQUEST_UUID = "8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b";

    private QuestContractFixtures() {}

    static byte[] bytes(String name) {
        String path = RESOURCE_ROOT + name;
        try (InputStream in = QuestContractFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing checked-in fixture: " + path);
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** LF-normalised text, the form the mirrored manifest digests. */
    static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static JsonObject json(String name) {
        return JsonParser.parseString(text(name)).getAsJsonObject();
    }
}
