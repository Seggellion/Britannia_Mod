package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestEntryParserCompatibilityTest {
    @Test
    void legacyCurrentQuestsAliasRemainsParseableWithoutServiceRegistry() {
        JsonObject root = JsonParser.parseString("""
                {
                  "current_quests": [
                    {
                      "player_quest_state_id": "state-17",
                      "quest_definition_id": "42",
                      "slug": "escort_to_britain",
                      "npc_name": "Iolo:quest-api-id",
                      "quest_title": "Safe Passage",
                      "summary": "Escort the traveller.",
                      "started_at": "2026-07-14T10:00:00Z",
                      "state": "accepted"
                    }
                  ]
                }
                """).getAsJsonObject();

        List<ClientQuestEntry> quests = QuestEntryParser.parseAcceptedQuests(root);

        assertEquals(1, quests.size());
        ClientQuestEntry quest = quests.getFirst();
        assertEquals("state-17", quest.questStateId());
        assertEquals("42", quest.questId());
        assertEquals("escort_to_britain", quest.questKey());
        assertEquals("Iolo", quest.questGiverName());
        assertEquals("Safe Passage", quest.name());
        assertEquals("accepted", quest.status());

        ServiceNpcRegistryParser.ParseResult serviceRegistry =
                ServiceNpcRegistryParser.parseBootstrapRoot(root);
        assertEquals(ServiceNpcRegistryParser.ParseStatus.MISSING, serviceRegistry.status());
        assertTrue(serviceRegistry.snapshot().isEmpty());
        assertEquals(1, QuestEntryParser.parseAcceptedQuests(root).size());
    }

    @Test
    void modernAcceptedQuestsRemainParseableWithAnUnknownAdditiveRootMember() {
        JsonObject root = JsonParser.parseString("""
                {
                  "accepted_quests": [
                    {
                      "quest_state_id": "state-18",
                      "quest_id": "43",
                      "quest_key": "recover_relic",
                      "quest_giver_name": "Shamino",
                      "name": "Recover the Relic",
                      "status": "accepted"
                    }
                  ],
                  "future_additive_member": {"ignored": true}
                }
                """).getAsJsonObject();

        List<ClientQuestEntry> quests = QuestEntryParser.parseAcceptedQuests(root);

        assertEquals(1, quests.size());
        assertEquals("state-18", quests.getFirst().questStateId());
        assertEquals("recover_relic", quests.getFirst().questKey());
    }
}
