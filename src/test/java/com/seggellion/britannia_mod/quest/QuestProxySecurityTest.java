package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestProxySecurityTest {
    private static final String REQUEST_UUID = "8d1f2c3a-4b5e-4f60-9a71-2c3d4e5f6a7b";

    @Test
    void boundedQuestIntentRoundTripsWithoutCredentialsOrAuthoritativeState() {
        QuestActionC2SPayload payload = new QuestActionC2SPayload(
            17L, QuestActionC2SPayload.Action.CHOOSE, 42L, "accept", -1, UUID.randomUUID(), REQUEST_UUID);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        QuestActionC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, QuestActionC2SPayload.STREAM_CODEC.decode(buffer));

        for (var component : QuestActionC2SPayload.class.getRecordComponents()) {
            assertFalse(component.getName().matches("(?i).*(secret|token|credential|authorization|reward|price|balance).*"));
        }
    }

    @Test
    void rejectsMalformedAndUnboundedQuestIntents() {
        UUID npc = UUID.randomUUID();
        assertTrue(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "", 10, npc, REQUEST_UUID)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "client_npc_name", 10, npc, REQUEST_UUID)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.CHOOSE, 2L, "x".repeat(129), -1, npc, REQUEST_UUID)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.ABANDON, 2L, "unexpected", -1, new UUID(0L, 0L), REQUEST_UUID)));
    }

    /**
     * Milestone 2: the correlation id is logged and forwarded to Rails, so an unbounded or
     * control-laden value would be a log-injection vector rather than a trace token.
     */
    @Test
    void rejectsMalformedCorrelationIds() {
        assertTrue(QuestProxyService.validRequestUuid(REQUEST_UUID));
        assertFalse(QuestProxyService.validRequestUuid(null));
        assertFalse(QuestProxyService.validRequestUuid(""));
        assertFalse(QuestProxyService.validRequestUuid("not-a-uuid"));
        assertFalse(QuestProxyService.validRequestUuid(REQUEST_UUID.replace('-', ' ')));
        assertFalse(QuestProxyService.validRequestUuid(REQUEST_UUID + "extra"));
        assertFalse(QuestProxyService.validRequestUuid("8d1f2c3a4b5e4f609a712c3d4e5f6a7b"));
        // A newline in a value that is written straight into a log line is log injection.
        assertFalse(QuestProxyService.validRequestUuid(
            REQUEST_UUID.substring(0, 30) + "\n" + REQUEST_UUID.substring(31)));

        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.CHOOSE, 2L, "accept", -1, UUID.randomUUID(), "not-a-uuid")));
    }

    @Test
    void correlationIdSurvivesTheWireUnchanged() {
        QuestActionC2SPayload payload = new QuestActionC2SPayload(
            3L, QuestActionC2SPayload.Action.TRIGGER, 9L, "arrived", -1, new UUID(0L, 0L), REQUEST_UUID);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        QuestActionC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(REQUEST_UUID, QuestActionC2SPayload.STREAM_CODEC.decode(buffer).requestUuid());
    }

    @Test
    void rewardValidatorRejectsClientScaleCountsAndMalformedIds() {
        QuestModels.ItemData valid = new QuestModels.ItemData();
        valid.id = "britannia_mod:quest_item";
        valid.count = 1;
        assertTrue(QuestRewardService.valid(valid));

        valid.count = QuestRewardService.MAX_ITEM_COUNT + 1;
        assertFalse(QuestRewardService.valid(valid));
        valid.count = 1;
        valid.id = "x".repeat(129);
        assertFalse(QuestRewardService.valid(valid));
    }
}
