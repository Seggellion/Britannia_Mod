package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 7 item 3: that republished policy actually takes effect.
 *
 * <p>The whole reason Guildmaster policy lives in Rails and arrives through the world-bootstrap
 * cache is so an admin can change it without restarting a Minecraft server. Nothing tested that the
 * change is <em>observed</em> — a stale read anywhere between the cache and
 * {@link GuildmasterCapability} would leave an admin editing a value that silently does nothing,
 * which is far worse than an obvious failure because it looks like the edit was applied.
 *
 * <p>Every read here goes through the same accessors production uses, so a future optimisation that
 * caches a snapshot in a field would break these rather than pass quietly.
 */
class GuildmasterPolicyRefreshTest {
    private static final String TYPE = "warrior_guildmaster";
    private static final UUID CITY_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @AfterEach
    void clearRegistry() {
        ServiceNpcRegistryCache.clear();
    }

    private static void publish(ServiceNpcTypeDefinition definition) {
        ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                1, 1L, Map.of(), Map.of(definition.key(), definition), Map.of()));
    }

    private static ServiceNpcTypeDefinition guild(
            List<String> taught, Map<String, Double> minimums, boolean active
    ) {
        return new ServiceNpcTypeDefinition(
                TYPE, "Warrior Guildmaster", TYPE, "britannia_mod:service_npc", TYPE + "_default",
                List.of("guild.train"), taught, minimums, active, true, 1L);
    }

    private static BootstrapCityDefinition city(double food) {
        return new BootstrapCityDefinition(CITY_ID, "Britain", Map.of("food", food, "gold", 12.0));
    }

    @Test
    void loweringTheEconomicMinimumTakesEffectWithoutARestart() {
        publish(guild(List.of("swordsmanship"), Map.of("food", 200.0), true));
        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM,
                ServiceNpcSpawnEligibility.evaluate(
                        ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(TYPE), city(100.0)).status());

        // The admin lowers the bar in Rails; the next bootstrap republishes the registry.
        publish(guild(List.of("swordsmanship"), Map.of("food", 50.0), true));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED,
                ServiceNpcSpawnEligibility.evaluate(
                        ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(TYPE), city(100.0)).status(),
                "a republished minimum must be observed, not read from a stale snapshot");
    }

    @Test
    void raisingTheEconomicMinimumAlsoTakesEffect() {
        // The direction that withdraws a Guildmaster rather than granting one, which is the one an
        // admin is more likely to be surprised by.
        publish(guild(List.of("swordsmanship"), Map.of("food", 50.0), true));
        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED,
                ServiceNpcSpawnEligibility.evaluate(
                        ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(TYPE), city(100.0)).status());

        publish(guild(List.of("swordsmanship"), Map.of("food", 500.0), true));

        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM,
                ServiceNpcSpawnEligibility.evaluate(
                        ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(TYPE), city(100.0)).status());
    }

    @Test
    void addingATaughtSkillIsVisibleImmediately() {
        publish(guild(List.of("swordsmanship"), Map.of(), true));
        assertFalse(GuildmasterCapability.teaches(TYPE, "tactics"));

        publish(guild(List.of("swordsmanship", "tactics"), Map.of(), true));

        assertTrue(GuildmasterCapability.teaches(TYPE, "tactics"),
                "a skill added to a guild in Rails must be trainable without a restart");
        assertEquals(List.of("swordsmanship", "tactics"), GuildmasterCapability.taughtSkillSlugs(TYPE));
    }

    @Test
    void removingATaughtSkillStopsItBeingTrainable() {
        // The security-relevant direction: a skill withdrawn from a guild must stop being
        // purchasable at once, or a player could keep buying something Rails no longer offers.
        publish(guild(List.of("swordsmanship", "tactics"), Map.of(), true));
        assertTrue(GuildmasterCapability.teaches(TYPE, "tactics"));

        publish(guild(List.of("swordsmanship"), Map.of(), true));

        assertFalse(GuildmasterCapability.teaches(TYPE, "tactics"));
    }

    @Test
    void deactivatingTheTypeIsTheGlobalOffSwitch() {
        publish(guild(List.of("swordsmanship"), Map.of(), true));
        assertTrue(GuildmasterCapability.supportsGuildTrain(TYPE));

        publish(guild(List.of("swordsmanship"), Map.of(), false));

        assertFalse(GuildmasterCapability.supportsGuildTrain(TYPE),
                "deactivating a type in Rails must disable training everywhere at once");
        assertTrue(GuildmasterCapability.taughtSkillSlugs(TYPE).isEmpty());
    }

    @Test
    void aFailedBootstrapClearsPolicyRatherThanKeepingStaleTerms() {
        // A rejected or missing registry falls back to the empty snapshot. Guildmasters then stop
        // training, which is correct: continuing to sell skills on terms Rails has disowned is the
        // worse failure.
        publish(guild(List.of("swordsmanship"), Map.of("food", 200.0), true));
        assertTrue(GuildmasterCapability.supportsGuildTrain(TYPE));

        ServiceNpcRegistryCache.replace(ServiceNpcRegistrySnapshot.empty());

        assertFalse(GuildmasterCapability.supportsGuildTrain(TYPE));
        assertTrue(GuildmasterCapability.roleTitle(TYPE).isEmpty());
    }
}
