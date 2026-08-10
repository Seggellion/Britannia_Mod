package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guildmaster milestone 2. Mirrors Rails' {@code CityStaffing::EconomicEligibility} closely enough
 * that the admin readout agrees with the decision Rails actually made -- see especially the
 * at-the-threshold case, which must be {@code >=} on both sides or the screen will contradict the
 * server for every city sitting exactly on 200 food.
 */
class ServiceNpcSpawnEligibilityTest {
    private static final Map<String, Double> GUILD_MINIMUM =
            Map.of("food", 200.0, "silver", 5.0, "alcohol", 5.0);

    @Test
    void aTypeWithNoRequirementsIsAlwaysSatisfied() {
        // Every bank teller.
        ServiceNpcSpawnEligibility.Result result =
                ServiceNpcSpawnEligibility.evaluate(type(Map.of()), city(Map.of()));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED, result.status());
        assertFalse(result.blocksSpawning());
        assertTrue(result.requirements().isEmpty());
    }

    @Test
    void noSelectedTypeIsSatisfied() {
        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED,
                ServiceNpcSpawnEligibility.evaluate(null, city(Map.of())).status());
    }

    @Test
    void aCityAboveEveryMinimumIsSatisfied() {
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(GUILD_MINIMUM), city(Map.of("food", 250.0, "silver", 10.0, "alcohol", 10.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED, result.status());
        assertEquals(3, result.requirements().size());
        assertTrue(result.shortfalls().isEmpty());
    }

    @Test
    void aSupplyExactlyAtItsMinimumIsSatisfied() {
        // >= not >, matching Rails. A city sitting exactly on 200 food keeps its Guildmaster, and
        // the screen must not claim otherwise.
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(GUILD_MINIMUM), city(Map.of("food", 200.0, "silver", 5.0, "alcohol", 5.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED, result.status());
    }

    @Test
    void oneSupplyBelowItsMinimumBlocksSpawning() {
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(GUILD_MINIMUM), city(Map.of("food", 199.9, "silver", 10.0, "alcohol", 10.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM, result.status());
        assertTrue(result.blocksSpawning());
        assertEquals(List.of("food"), result.shortfalls().stream()
                .map(ServiceNpcSpawnEligibility.SupplyStatus::supply).toList());
    }

    @Test
    void shortfallsAreListedFirstAndThenAlphabetically() {
        // Deterministic so the line does not reshuffle between refreshes.
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(GUILD_MINIMUM), city(Map.of("food", 143.0, "silver", 10.0, "alcohol", 2.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM, result.status());
        assertEquals(List.of("alcohol", "food", "silver"), result.requirements().stream()
                .map(ServiceNpcSpawnEligibility.SupplyStatus::supply).toList());
    }

    @Test
    void missingSupplyFiguresAreUnknownRatherThanSatisfied() {
        // An older Rails build publishes no supply figures. Reporting "all clear" from absent data
        // is how an admin ends up trusting a screen that knows nothing.
        ServiceNpcSpawnEligibility.Result result =
                ServiceNpcSpawnEligibility.evaluate(type(GUILD_MINIMUM), city(Map.of()));

        assertEquals(ServiceNpcSpawnEligibility.Status.UNKNOWN, result.status());
        assertFalse(result.blocksSpawning(), "an unknown reading must not be reported as a block");
        assertEquals(3, result.shortfalls().size());
    }

    @Test
    void aNullCityIsUnknownWhenTheTypeHasRequirements() {
        assertEquals(ServiceNpcSpawnEligibility.Status.UNKNOWN,
                ServiceNpcSpawnEligibility.evaluate(type(GUILD_MINIMUM), null).status());
    }

    @Test
    void aConfirmedShortfallOutranksAMissingFigure() {
        // food is provably below minimum; alcohol is unmeasurable. The spawn is blocked either
        // way, so the honest answer is BELOW_MINIMUM rather than the softer UNKNOWN.
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(GUILD_MINIMUM), city(Map.of("food", 10.0, "silver", 10.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM, result.status());
    }

    @Test
    void anExtraSupplyTheTypeDoesNotRequireIsIgnored() {
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(Map.of("food", 200.0)),
                city(Map.of("food", 250.0, "wood", 0.0, "stone", 0.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED, result.status());
        assertEquals(1, result.requirements().size());
    }

    @Test
    void aZeroMinimumIsSatisfiedByAnEmptyStore() {
        ServiceNpcSpawnEligibility.Result result = ServiceNpcSpawnEligibility.evaluate(
                type(Map.of("food", 0.0)), city(Map.of("food", 0.0)));

        assertEquals(ServiceNpcSpawnEligibility.Status.SATISFIED, result.status());
    }

    private static ServiceNpcTypeDefinition type(Map<String, Double> minimums) {
        return new ServiceNpcTypeDefinition(
                "warrior_guildmaster", "Warrior Guildmaster", "warrior_guildmaster",
                "britannia_mod:service_npc", "warrior_guildmaster_default",
                List.of("guild.train"), List.of("swords"), minimums, true, true, 1L
        );
    }

    private static BootstrapCityDefinition city(Map<String, Double> supplies) {
        return new BootstrapCityDefinition(
                UUID.fromString("11111111-1111-4111-8111-111111111111"), "Britain", supplies);
    }
}
