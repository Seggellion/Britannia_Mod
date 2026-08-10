package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code BankingCapabilityTest}: proves the Guildmaster gate reads live capability from
 * the registry cache rather than a hardcoded guild roster.
 */
class GuildmasterCapabilityTest {
    @AfterEach
    void clearRegistry() {
        ServiceNpcRegistryCache.clear();
    }

    @Test
    void anActiveTypeAllowingGuildTrainWithSkillsSupportsIt() {
        publish(guildmaster("warrior_guildmaster", true, List.of("guild.train"), List.of("swords", "tactics")));

        assertTrue(GuildmasterCapability.supportsGuildTrain("warrior_guildmaster"));
        assertEquals(List.of("swords", "tactics"), GuildmasterCapability.taughtSkillSlugs("warrior_guildmaster"));
    }

    @Test
    void aBankTellerIsNotAGuildmaster() {
        publish(guildmaster("bank_teller", true, List.of("bank.open"), List.of()));

        assertFalse(GuildmasterCapability.supportsGuildTrain("bank_teller"));
        assertEquals(List.of(), GuildmasterCapability.taughtSkillSlugs("bank_teller"));
        assertTrue(GuildmasterCapability.roleTitle("bank_teller").isEmpty());
    }

    @Test
    void anInactiveGuildmasterSupportsNothingEvenWhenAllowlisted() {
        publish(guildmaster("warrior_guildmaster", false, List.of("guild.train"), List.of("swords")));

        assertFalse(GuildmasterCapability.supportsGuildTrain("warrior_guildmaster"));
        assertEquals(List.of(), GuildmasterCapability.taughtSkillSlugs("warrior_guildmaster"));
        assertFalse(GuildmasterCapability.teaches("warrior_guildmaster", "swords"));
    }

    @Test
    void aTypeAllowingTrainingButTeachingNothingIsNotAGuildmaster() {
        // The parser refuses to publish this shape, but the gate must not depend on that.
        publish(guildmaster("hollow_guildmaster", true, List.of("guild.train"), List.of()));

        assertFalse(GuildmasterCapability.supportsGuildTrain("hollow_guildmaster"));
    }

    @Test
    void teachesIsMembershipNotMerePresence() {
        publish(guildmaster("tinker_guildmaster", true, List.of("guild.train"),
                List.of("lockpicking", "tinkering", "remove-trap")));

        assertTrue(GuildmasterCapability.teaches("tinker_guildmaster", "tinkering"));
        assertTrue(GuildmasterCapability.teaches("tinker_guildmaster", "remove-trap"));
        assertFalse(GuildmasterCapability.teaches("tinker_guildmaster", "magery"));
        assertFalse(GuildmasterCapability.teaches("tinker_guildmaster", "TINKERING"));
        assertFalse(GuildmasterCapability.teaches("tinker_guildmaster", null));
        assertFalse(GuildmasterCapability.teaches("tinker_guildmaster", ""));
    }

    @Test
    void twoGuildsMayBothTeachTheSameSkill() {
        // MagicResist belongs to four RunUO guilds; a shared skill must resolve for each.
        ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                1, 1L, Map.of(),
                Map.of(
                        "warrior_guildmaster",
                        guildmaster("warrior_guildmaster", true, List.of("guild.train"),
                                List.of("magic-resist", "swords")),
                        "mage_guildmaster",
                        guildmaster("mage_guildmaster", true, List.of("guild.train"),
                                List.of("magery", "magic-resist"))
                ),
                Map.of()
        ));

        assertTrue(GuildmasterCapability.teaches("warrior_guildmaster", "magic-resist"));
        assertTrue(GuildmasterCapability.teaches("mage_guildmaster", "magic-resist"));
        assertFalse(GuildmasterCapability.teaches("warrior_guildmaster", "magery"));
        assertFalse(GuildmasterCapability.teaches("mage_guildmaster", "swords"));
    }

    @Test
    void roleTitleIsThePublishedDisplayName() {
        publish(guildmaster("warrior_guildmaster", true, List.of("guild.train"), List.of("swords")));

        assertEquals("Warrior Guildmaster", GuildmasterCapability.roleTitle("warrior_guildmaster").orElseThrow());
    }

    @Test
    void unknownNullAndBlankKeysSupportNothing() {
        publish(guildmaster("warrior_guildmaster", true, List.of("guild.train"), List.of("swords")));

        assertFalse(GuildmasterCapability.supportsGuildTrain("nonexistent_type"));
        assertFalse(GuildmasterCapability.supportsGuildTrain(null));
        assertFalse(GuildmasterCapability.supportsGuildTrain(""));
        assertFalse(GuildmasterCapability.supportsGuildTrain("   "));
        assertEquals(List.of(), GuildmasterCapability.taughtSkillSlugs(null));
        assertTrue(GuildmasterCapability.roleTitle(null).isEmpty());
    }

    @Test
    void anEmptyRegistrySupportsNothing() {
        // This is the client's permanent state: the registry cache is server-populated only.
        assertFalse(GuildmasterCapability.supportsGuildTrain("warrior_guildmaster"));
        assertEquals(List.of(), GuildmasterCapability.taughtSkillSlugs("warrior_guildmaster"));
        assertTrue(GuildmasterCapability.roleTitle("warrior_guildmaster").isEmpty());
    }

    private static void publish(ServiceNpcTypeDefinition definition) {
        ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                1, 1L, Map.of(), Map.of(definition.key(), definition), Map.of()
        ));
    }

    private static ServiceNpcTypeDefinition guildmaster(
            String key, boolean active, List<String> allowedServiceKeys, List<String> taughtSkillSlugs
    ) {
        return new ServiceNpcTypeDefinition(
                key,
                displayNameFor(key),
                key,
                "britannia_mod:service_npc",
                key + "_default",
                allowedServiceKeys,
                taughtSkillSlugs,
                active,
                true,
                1L
        );
    }

    private static String displayNameFor(String key) {
        StringBuilder display = new StringBuilder();
        for (String word : key.split("_")) {
            if (!display.isEmpty()) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }
}
