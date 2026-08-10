package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guildmaster milestone 1: the {@code guild.train} service key and the optional
 * {@code taught_skill_slugs} member.
 *
 * <p>Kept separate from {@code ServiceNpcRegistryParserTest} rather than folded into it — that
 * class is the banking-era contract and its continued passing, untouched, is itself part of this
 * milestone's evidence that no existing Service NPC behavior changed.
 */
class ServiceNpcRegistryParserGuildmasterTest {
    @AfterEach
    void clearCache() {
        ServiceNpcRegistryCache.clear();
    }

    // ---------- Accepting a Guildmaster ----------

    @Test
    void parsesAGuildmasterTeachingSeveralSkillsInPublishedOrder() {
        ServiceNpcRegistryParser.ParseResult result =
                parse(taughtSkills("\"arms-lore\", \"fencing\", \"macing\", \"parry\", \"swords\", \"tactics\""));

        assertAccepted(result);
        ServiceNpcTypeDefinition guild = result.snapshot().serviceNpcTypes().get("warrior_guildmaster");
        assertNotNull(guild);
        assertEquals("Warrior Guildmaster", guild.displayName());
        assertEquals("warrior_guildmaster", guild.professionKey());
        assertEquals(List.of("guild.train"), guild.allowedServiceKeys());
        assertEquals(
                List.of("arms-lore", "fencing", "macing", "parry", "swords", "tactics"),
                guild.taughtSkillSlugs()
        );
    }

    @Test
    void taughtSkillSlugsAreImmutable() {
        ServiceNpcTypeDefinition guild = acceptedGuildmaster(taughtSkills("\"swords\""));

        assertThrows(UnsupportedOperationException.class, () -> guild.taughtSkillSlugs().add("mining"));
    }

    @Test
    void acceptsAHyphenatedSlugAsRailsFriendlyIdActuallyProducesIt() {
        // "Animal Taming" parameterizes to animal-taming. The Service NPC DEFINITION_KEY pattern
        // rejects hyphens, so a slug validated with that pattern would make every multi-word
        // skill unpublishable.
        ServiceNpcTypeDefinition guild = acceptedGuildmaster(taughtSkills("\"animal-taming\", \"magic-resist\""));

        assertEquals(List.of("animal-taming", "magic-resist"), guild.taughtSkillSlugs());
    }

    @Test
    void acceptsAnUnderscoredSlugBecauseSlugIsAPlainAdminEditableColumn() {
        ServiceNpcTypeDefinition guild = acceptedGuildmaster(taughtSkills("\"animal_taming\""));

        assertEquals(List.of("animal_taming"), guild.taughtSkillSlugs());
    }

    // ---------- Backward compatibility: this must never break bankers ----------

    @Test
    void aBankTellerInTheSameRegistryIsUnaffectedByAGuildmaster() {
        ServiceNpcRegistryParser.ParseResult result =
                parse(taughtSkills("\"swords\""));

        assertAccepted(result);
        ServiceNpcTypeDefinition teller = result.snapshot().serviceNpcTypes().get("bank_teller");
        assertNotNull(teller, "publishing a Guildmaster must not drop the bank teller");
        assertEquals(List.of("bank.open", "bank.create_check"), teller.allowedServiceKeys());
        assertEquals(List.of(), teller.taughtSkillSlugs());
        assertTrue(teller.active());
        assertTrue(teller.spawnable());
    }

    @Test
    void anAbsentTaughtSkillSlugsMemberParsesAsEmptyRatherThanRejecting() {
        // An older Rails build that predates this milestone omits the member entirely. Rejecting
        // it would fall the whole registry back to the empty snapshot and take bank tellers with
        // it, so absence must stay legal forever.
        ServiceNpcRegistryParser.ParseResult result = parse("", "bank.open");

        assertAccepted(result);
        assertEquals(List.of(), result.snapshot().serviceNpcTypes().get("warrior_guildmaster").taughtSkillSlugs());
    }

    @Test
    void anExplicitlyNullTaughtSkillSlugsMemberParsesAsEmpty() {
        ServiceNpcRegistryParser.ParseResult result =
                parse("\"taught_skill_slugs\": null,", "bank.open");

        assertAccepted(result);
        assertEquals(List.of(), result.snapshot().serviceNpcTypes().get("warrior_guildmaster").taughtSkillSlugs());
    }

    // ---------- Rejecting incoherent Guildmaster data ----------

    @Test
    void rejectsAGuildmasterThatAllowsTrainingButTeachesNothing() {
        assertRejected(parse(taughtSkills("")), "teaches no skills");
    }

    @Test
    void rejectsAGuildmasterThatAllowsTrainingWithNoTaughtSkillsMemberAtAll() {
        assertRejected(parse(""), "teaches no skills");
    }

    @Test
    void rejectsTaughtSkillsOnATypeThatDoesNotAllowTraining() {
        assertRejected(
                parse("\"taught_skill_slugs\": [\"swords\"],", "bank.open"),
                "without allowing guild.train"
        );
    }

    @Test
    void rejectsADuplicateTaughtSkill() {
        assertRejected(parse(taughtSkills("\"swords\", \"swords\"")), "duplicate taught skill");
    }

    @Test
    void rejectsAnUppercaseSlug() {
        assertRejected(parse(taughtSkills("\"Swords\"")), "invalid taught skill slug");
    }

    @Test
    void rejectsASlugWithASpace() {
        assertRejected(parse(taughtSkills("\"animal taming\"")), "invalid taught skill slug");
    }

    @Test
    void rejectsASlugWithALeadingSeparator() {
        assertRejected(parse(taughtSkills("\"-swords\"")), "invalid taught skill slug");
    }

    @Test
    void rejectsABlankSlug() {
        assertRejected(parse(taughtSkills("\"\"")), "must be a non-empty string");
    }

    @Test
    void rejectsANonStringSlug() {
        // The shared requiredString helper reports a JSON number as "non-empty string" rather
        // than "string" (a number is still a JsonPrimitive). Asserting its real wording rather
        // than reshaping a helper the whole parser depends on.
        assertRejected(parse(taughtSkills("17")), "must be a non-empty string");
    }

    @Test
    void rejectsATaughtSkillSlugsMemberThatIsNotAnArray() {
        assertRejected(
                parse("\"taught_skill_slugs\": \"swords\","),
                "taught_skill_slugs must be an array"
        );
    }

    @Test
    void rejectsAnUnboundedTaughtSkillList() {
        StringBuilder slugs = new StringBuilder();
        for (int index = 0; index <= 64; index++) {
            if (index > 0) slugs.append(", ");
            slugs.append("\"skill-").append(index).append('"');
        }
        assertRejected(parse(taughtSkills(slugs.toString())), "too many skills");
    }

    @Test
    void stillRejectsAServiceKeyThatIsNotInTheSupportedSet() {
        // guild.train became supported; guild.join did not. The closed set is still closed.
        assertRejected(
                parse(taughtSkills("\"swords\""), "guild.join"),
                "unsupported service action guild.join"
        );
    }

    // ---------- minimum_city_supplies (Milestone 7: shared-payload compatibility) ----------

    @Test
    void parsesTheEconomicGate() {
        ServiceNpcRegistryParser.ParseResult result = parse(
                "\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"food\": 200, \"gold\": 1},");

        assertAccepted(result);
        ServiceNpcTypeDefinition guild = result.snapshot().serviceNpcTypes().get("warrior_guildmaster");
        assertEquals(200.0, guild.minimumCitySupplies().get("food"));
        assertEquals(1.0, guild.minimumCitySupplies().get("gold"));
    }

    @Test
    void anAbsentEconomicGateParsesAsUngated() {
        // The compatibility case that matters: a Rails build predating the gate omits the member
        // entirely. Requiring it would reject the whole registry and take bank tellers down too.
        ServiceNpcRegistryParser.ParseResult result = parse(taughtSkills("\"swords\""));

        assertAccepted(result);
        assertTrue(result.snapshot().serviceNpcTypes()
                .get("warrior_guildmaster").minimumCitySupplies().isEmpty());
    }

    @Test
    void anExplicitlyNullEconomicGateParsesAsUngated() {
        ServiceNpcRegistryParser.ParseResult result = parse(
                "\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": null,");

        assertAccepted(result);
        assertTrue(result.snapshot().serviceNpcTypes()
                .get("warrior_guildmaster").minimumCitySupplies().isEmpty());
    }

    @Test
    void aBankTellerIsUngatedEvenBesideAGatedGuildmaster() {
        ServiceNpcRegistryParser.ParseResult result = parse(
                "\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"food\": 200},");

        assertAccepted(result);
        assertTrue(result.snapshot().serviceNpcTypes().get("bank_teller").minimumCitySupplies().isEmpty(),
                "publishing an economic gate must never gate the banker");
    }

    @Test
    void theEconomicGateIsImmutable() {
        ServiceNpcRegistryParser.ParseResult result = parse(
                "\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"food\": 200},");

        assertAccepted(result);
        assertThrows(UnsupportedOperationException.class, () -> result.snapshot()
                .serviceNpcTypes().get("warrior_guildmaster").minimumCitySupplies().put("gold", 1.0));
    }

    @Test
    void rejectsANonObjectEconomicGate() {
        assertRejected(parse("\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": 200,"),
                "minimum_city_supplies must be an object");
    }

    @Test
    void rejectsANonNumericMinimum() {
        assertRejected(
                parse("\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"food\": \"lots\"},"),
                "must be a number");
    }

    @Test
    void rejectsANegativeMinimum() {
        assertRejected(
                parse("\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"food\": -1},"),
                "non-negative finite number");
    }

    @Test
    void rejectsAMalformedSupplyKey() {
        assertRejected(
                parse("\"taught_skill_slugs\": [\"swords\"], \"minimum_city_supplies\": {\"Food Supply\": 200},"),
                "invalid supply key");
    }

    // ---------- Fixture ----------

    private static ServiceNpcTypeDefinition acceptedGuildmaster(String taughtSkillsMember) {
        ServiceNpcRegistryParser.ParseResult result = parse(taughtSkillsMember);
        assertAccepted(result);
        return result.snapshot().serviceNpcTypes().get("warrior_guildmaster");
    }

    private static String taughtSkills(String slugList) {
        return "\"taught_skill_slugs\": [" + slugList + "],";
    }

    /** The three keys the fixture always declares, so a reused key is never declared twice. */
    private static final List<String> BASE_ACTION_KEYS =
            List.of("bank.open", "bank.create_check", "guild.train");

    private static ServiceNpcRegistryParser.ParseResult parse(String taughtSkillsMember) {
        return parse(taughtSkillsMember, "guild.train");
    }

    /**
     * A registry carrying both the seeded {@code bank_teller} and a {@code warrior_guildmaster},
     * so every case here also proves the banker survives alongside it.
     *
     * @param taughtSkillsMember raw JSON for the guildmaster's taught-skill member, including its
     *                           trailing comma, or {@code ""} to omit the member entirely
     * @param guildServiceKey    the service the guildmaster's dialogue invokes and allows. Only
     *                           appended to {@code service_actions} when it is not already one of
     *                           {@link #BASE_ACTION_KEYS} — declaring it twice would trip the
     *                           parser's duplicate-action check and mask the case under test.
     */
    private static ServiceNpcRegistryParser.ParseResult parse(
            String taughtSkillsMember, String guildServiceKey
    ) {
        String extraAction = BASE_ACTION_KEYS.contains(guildServiceKey) ? ""
                : ",{ \"key\": \"" + guildServiceKey
                        + "\", \"display_name\": \"Extra\", \"description\": \"Extra.\" }";
        String json = """
                {
                  "service_npc_registry": {
                    "schema_version": 1,
                    "revision": 501424005180508762,
                    "service_actions": [
                      { "key": "bank.open", "display_name": "Open bank account", "description": "Open it." },
                      { "key": "bank.create_check", "display_name": "Create bank check", "description": "Begin it." },
                      { "key": "guild.train", "display_name": "Train a skill", "description": "Teach it." }
                      __EXTRA_ACTION__
                    ],
                    "service_npc_types": [
                      {
                        "key": "bank_teller",
                        "display_name": "Bank Teller",
                        "profession_key": "banker",
                        "minecraft_entity_type_key": "britannia_mod:service_npc",
                        "default_dialogue_key": "bank_teller_default",
                        "allowed_service_keys": ["bank.open", "bank.create_check"],
                        "active": true,
                        "spawnable": true,
                        "definition_revision": 1
                      },
                      {
                        "key": "warrior_guildmaster",
                        "display_name": "Warrior Guildmaster",
                        "profession_key": "warrior_guildmaster",
                        "minecraft_entity_type_key": "britannia_mod:service_npc",
                        "default_dialogue_key": "warrior_guildmaster_default",
                        "allowed_service_keys": ["__GUILD_KEY__"],
                        __TAUGHT__
                        "active": true,
                        "spawnable": true,
                        "definition_revision": 1
                      }
                    ],
                    "dialogue_sets": [
                      {
                        "key": "bank_teller_default",
                        "entry_node_key": "greeting",
                        "definition_revision": 1,
                        "nodes": [
                          {
                            "key": "greeting",
                            "body": "How may I assist you?",
                            "options": [
                              { "id": "open_bank_box", "label": "Open my bank box.",
                                "action_type": "invoke_service", "service_key": "bank.open" },
                              { "id": "goodbye", "label": "Goodbye.", "action_type": "close" }
                            ]
                          }
                        ]
                      },
                      {
                        "key": "warrior_guildmaster_default",
                        "entry_node_key": "greeting",
                        "definition_revision": 1,
                        "nodes": [
                          {
                            "key": "greeting",
                            "body": "Wouldst thou learn the way of the blade?",
                            "options": [
                              { "id": "train", "label": "Teach me.",
                                "action_type": "invoke_service", "service_key": "__GUILD_KEY__" },
                              { "id": "farewell", "label": "Farewell.", "action_type": "close" }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """
                .replace("__EXTRA_ACTION__", extraAction)
                .replace("__GUILD_KEY__", guildServiceKey)
                .replace("__TAUGHT__", taughtSkillsMember);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return ServiceNpcRegistryParser.parseBootstrapRoot(root);
    }

    private static void assertAccepted(ServiceNpcRegistryParser.ParseResult result) {
        assertEquals(ServiceNpcRegistryParser.ParseStatus.ACCEPTED, result.status(), result.error());
    }

    private static void assertRejected(ServiceNpcRegistryParser.ParseResult result, String expectedFragment) {
        assertEquals(ServiceNpcRegistryParser.ParseStatus.REJECTED, result.status());
        assertTrue(result.snapshot().isEmpty(), "a rejected registry must fall back to the empty snapshot");
        assertNotNull(result.error());
        assertTrue(
                result.error().contains(expectedFragment),
                "expected error to mention '" + expectedFragment + "' but was: " + result.error()
        );
    }
}
