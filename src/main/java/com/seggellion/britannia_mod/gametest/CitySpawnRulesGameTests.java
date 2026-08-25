package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.CitySpawnRules;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * City spawn policy for Patch 18's Service NPCs and for the parrot/ocelot city wildlife.
 *
 * <p>These are GameTests rather than JUnit tests because {@link CitySpawnRules}' static
 * initialiser resolves {@code DeferredHolder}s ({@code EntityRegistry.RAT_ENTITY.get()} and
 * friends), which throws outside a loaded mod — the class cannot be loaded by the plain unit
 * harness at all.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CitySpawnRulesGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "city_spawn_rules";

    /** Enough draws that a four-member pool missing a guard would show up rather than pass by luck. */
    private static final int AMBIENT_POOL_DRAWS = 200;

    private CitySpawnRulesGameTests() {
    }

    /**
     * The regression that motivated this file: a Service NPC is a {@code CitizenEntity} but is
     * neither an {@code ITrader} nor an {@code AbstractEconomyMerchantEntity}, so it fell through
     * every allow-list and {@code CitySpawner} cancelled its {@code EntityJoinLevelEvent} inside
     * any city area — a Rails-assigned banker could never enter the world it was assigned to.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void serviceNpcIsAllowedInsideCityAreas(GameTestHelper helper) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));

        check(CitySpawnRules.isAllowed(npc), "Service NPC is not allowed in a city area");
        check(!CitySpawnRules.isDisallowed(npc), "Service NPC is still rejected by the join-level guard");
        helper.succeed();
    }

    /**
     * Rails owns Service NPC population through {@code CityStaffing::DesiredStaffing}'s
     * {@code staffing_max_per_city}. The mod's local cap must not also cull them: discarding one
     * would orphan a live {@code NpcSpawnAssignment} with nothing in the world to match it.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void serviceNpcIsCriticalAndNotManagedByTheLocalCap(GameTestHelper helper) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));

        check(CitySpawnRules.isCritical(npc), "Service NPC is not reported as critical");
        check(!CitySpawnRules.isManagedByCityLimit(npc),
                "Service NPC is managed by the local city cap and can therefore be discarded");
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void parrotsAndOcelotsAreCityWildlife(GameTestHelper helper) {
        Entity parrot = helper.spawn(EntityType.PARROT, new BlockPos(1, 1, 1));
        Entity ocelot = helper.spawn(EntityType.OCELOT, new BlockPos(2, 1, 1));

        check(CitySpawnRules.isCityAmbientEntity(parrot), "parrot is not counted as city ambient wildlife");
        check(CitySpawnRules.isCityAmbientEntity(ocelot), "ocelot is not counted as city ambient wildlife");
        check(!CitySpawnRules.isDisallowed(parrot), "parrot is rejected by the join-level guard");
        check(!CitySpawnRules.isDisallowed(ocelot), "ocelot is rejected by the join-level guard");
        helper.succeed();
    }

    /**
     * A flamingo is {@code britannia_mod:} namespaced, so {@code isAllowedVanillaAnimal} never
     * covered it and the join-level guard cancelled it inside every city area.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void flamingosAreAllowedAndPopulatedInCities(GameTestHelper helper) {
        Entity flamingo = helper.spawn(EntityRegistry.FLAMINGO_ENTITY.get(), new BlockPos(1, 1, 1));

        check(CitySpawnRules.isAllowed(flamingo), "flamingo is not allowed in a city area");
        check(!CitySpawnRules.isDisallowed(flamingo), "flamingo is rejected by the join-level guard");
        check(CitySpawnRules.isCityAmbientEntity(flamingo), "flamingo is not counted as city ambient wildlife");
        helper.succeed();
    }

    /**
     * The regression behind "custom cats never spawn": a custom cat was allowed in, then
     * {@code CitySpawner.enforceEntityLimit} discarded it because merchants and villagers had
     * already consumed the shared {@code MAX_ENTITIES_PER_AREA} budget and the cull had only
     * non-critical wildlife left to delete. Ambient wildlife must not be in the cull's scope at all.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void ambientWildlifeIsNotCulledByTheIntentionalNpcCap(GameTestHelper helper) {
        Entity customCat = helper.spawn(EntityRegistry.CUSTOM_CAT_ENTITY.get(), new BlockPos(1, 1, 1));
        Entity rat = helper.spawn(EntityRegistry.RAT_ENTITY.get(), new BlockPos(2, 1, 1));
        Entity flamingo = helper.spawn(EntityRegistry.FLAMINGO_ENTITY.get(), new BlockPos(3, 1, 1));

        check(CitySpawnRules.isAllowed(customCat), "custom cat is not allowed in a city area");
        check(!CitySpawnRules.isManagedByCityLimit(customCat),
                "custom cat is still in the cull's scope and will be discarded as excess");
        check(!CitySpawnRules.isManagedByCityLimit(rat),
                "rat is still in the cull's scope and will be discarded as excess");
        check(!CitySpawnRules.isManagedByCityLimit(flamingo),
                "flamingo is still in the cull's scope and will be discarded as excess");
        helper.succeed();
    }

    /**
     * The drift guard. Everything the spawner can produce must also be counted by
     * {@link CitySpawnRules#isCityAmbientEntity}, because that count is the only gate on further
     * spawning — a spawnable-but-uncounted type never trips the cap and the city fills with it
     * without bound.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void everySpawnableAmbientTypeIsAlsoCounted(GameTestHelper helper) {
        boolean sawParrot = false;
        boolean sawOcelot = false;
        boolean sawFlamingo = false;

        for (int draw = 0; draw < AMBIENT_POOL_DRAWS; draw++) {
            Entity spawned = CitySpawnRules.createRandomCityAmbientEntity(helper.getLevel());
            check(spawned != null, "city ambient spawn pool produced nothing");
            check(CitySpawnRules.isCityAmbientEntity(spawned),
                    "spawnable ambient type is not counted against the city cap: " + spawned.getType());
            check(CitySpawnRules.isAllowed(spawned),
                    "spawnable ambient type would be cancelled on join: " + spawned.getType());

            sawParrot |= spawned.getType() == EntityType.PARROT;
            sawOcelot |= spawned.getType() == EntityType.OCELOT;
            sawFlamingo |= spawned.getType() == EntityRegistry.FLAMINGO_ENTITY.get();
            spawned.discard();
        }

        check(sawParrot, "parrots are never produced by the city ambient spawn pool");
        check(sawOcelot, "ocelots are never produced by the city ambient spawn pool");
        check(sawFlamingo, "flamingos are never produced by the city ambient spawn pool");
        helper.succeed();
    }

    /**
     * The counterweight to {@link #ambientWildlifeIsNotCulledByTheIntentionalNpcCap}: narrowing
     * {@code isManagedByCityLimit} must not empty the cull entirely. A villager is an intentional
     * city entity, not ambient wildlife, and stays in scope.
     */
    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void intentionalCityEntitiesRemainInTheCullsScope(GameTestHelper helper) {
        Entity villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));

        check(CitySpawnRules.isManagedByCityLimit(villager),
                "villager dropped out of the city cap's scope");
        check(CitySpawnRules.isCritical(villager), "villager is no longer protected from the cull");
        helper.succeed();
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
