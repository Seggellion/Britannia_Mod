package com.seggellion.britannia_mod.quest.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The frozen M0 fixtures, as bytes and as parsed objects, for the M5 contract tests. */
final class QuestActionContractFixtures {
    static final String RESOURCE_ROOT = "quest_contract/v1/";

    static final String REQUEST_CROP_HARVEST = "action_event_request_crop_harvest.json";
    static final String RESPONSE_APPLIED = "action_event_response_applied.json";
    static final String RESPONSE_DUPLICATE = "action_event_response_duplicate.json";
    static final String RESPONSE_IRRELEVANT = "action_event_response_irrelevant.json";
    static final String RESPONSE_STALE = "action_event_response_stale.json";
    static final String RESPONSE_REJECTED = "action_event_response_rejected.json";
    static final String JOURNAL_ENTRY_STAGE5 = "journal_entry_stage5.json";

    static final String EVENT_UUID = "3d2a0f8b-6b6c-4d2c-9d5a-0e7b8f1c2d3e";
    static final String REQUEST_UUID = "8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b";
    static final String PLAYER_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    static final String CROP_CYCLE_UUID = "b1b3d4a0-1f7e-4c0d-8f4c-6a2e9c1f0a11";
    static final String PLOT_KEY = "minecraft:overworld:1203:64:-488";

    private QuestActionContractFixtures() {}

    static byte[] bytes(String name) {
        String path = RESOURCE_ROOT + name;
        try (InputStream in = QuestActionContractFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing checked-in fixture: " + path);
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** LF-normalised text, the form the mirrored manifest digests and the encoder emits. */
    static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static byte[] normalisedBytes(String name) {
        return text(name).getBytes(StandardCharsets.UTF_8);
    }

    static JsonObject json(String name) {
        return JsonParser.parseString(text(name)).getAsJsonObject();
    }
}
