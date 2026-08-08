package com.seggellion.britannia_mod.quest;

import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestProxySecurityTest {
    @Test
    void boundedQuestIntentRoundTripsWithoutCredentialsOrAuthoritativeState() {
        QuestActionC2SPayload payload = new QuestActionC2SPayload(
            17L, QuestActionC2SPayload.Action.CHOOSE, 42L, "accept", -1, UUID.randomUUID());
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
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "", 10, npc)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "client_npc_name", 10, npc)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.CHOOSE, 2L, "x".repeat(129), -1, npc)));
        assertFalse(QuestProxyService.isValidShape(new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.ABANDON, 2L, "unexpected", -1, new UUID(0L, 0L))));
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
