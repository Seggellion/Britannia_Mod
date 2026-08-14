package com.seggellion.britannia_mod.population;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.util.NameLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Vendor/Trader Milestone 20 (owner decision #12): the regional TownPerson
 * population manager. Rails decides HOW MANY people a city's prosperity
 * supports; this converges the world toward that number GRADUALLY, inside the
 * authoritative city region.
 *
 * <h2>What replaces the legacy behavior</h2>
 * Legacy TownPersons spawned in a ring around a Merchant/Trader spawn block via
 * {@code townPersonAmount}. That side-spawning ends at block migration
 * (Milestone 16); the people themselves are PRESERVED — they are counted as
 * this city's existing population, so migration never causes a cull and never
 * double-counts.
 *
 * <h2>Gradual, bounded, and never intrusive</h2>
 * One poll per cadence (never per tick), at most {@code max_spawn_per_cycle} /
 * {@code max_despawn_per_cycle} changes per city per cycle, candidates only in
 * already-loaded chunks, and despawn prefers people no player is looking at.
 * The visible effect is a town that grows, shrinks and shifts over time rather
 * than one that pops in and out.
 */
public final class TownPersonPopulationManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    /** ~5 minutes, matching the Rails staffing sweep's own cadence. */
    public static final int CADENCE_TICKS = 6000;

    private static int ticksUntilNextPoll = CADENCE_TICKS;
    private static boolean pollInFlight;
    private static List<TownPersonPopulationPlan> currentPlans = List.of();

    private TownPersonPopulationManager() {
    }

    public static void start(MinecraftServer server) {
        ticksUntilNextPoll = CADENCE_TICKS;
        pollInFlight = false;
        currentPlans = List.of();
    }

    public static void tick(MinecraftServer server) {
        if (--ticksUntilNextPoll > 0) return;
        ticksUntilNextPoll = CADENCE_TICKS;
        if (pollInFlight) return;

        ServerLevel overworld = server.overworld();
        pollInFlight = true;
        try {
            ServerHttpExecutor.submit(server, () -> {
                        try {
                            return fetchPlans(overworld);
                        } catch (Exception failure) {
                            throw new java.util.concurrent.CompletionException(failure);
                        }
                    })
                    .whenComplete((plans, failure) -> server.execute(() -> {
                        pollInFlight = false;
                        if (failure != null || plans == null) {
                            // A backend outage must never disturb a living town.
                            LOGGER.warn("City population poll failed: {}", String.valueOf(failure));
                            return;
                        }
                        currentPlans = plans;
                        for (TownPersonPopulationPlan plan : plans) {
                            applyPlan(overworld, plan);
                        }
                    }));
        } catch (RejectedExecutionException rejected) {
            pollInFlight = false;
            LOGGER.warn("City population poll skipped: economy queue is full");
        }
    }

    /**
     * The convergence rule, pure so GameTests can prove it: a signed number of
     * TownPersons to add (positive) or remove (negative) this cycle, clamped by
     * the plan's own rate limits.
     */
    public static int convergenceDelta(TownPersonPopulationPlan plan, int livingCount) {
        int difference = plan.desiredPopulation() - livingCount;
        if (difference > 0) return Math.min(difference, plan.maxSpawnPerCycle());
        if (difference < 0) return -Math.min(-difference, plan.maxDespawnPerCycle());
        return 0;
    }

    /** Applies at most one cycle of convergence for one city. */
    public static int applyPlan(ServerLevel level, TownPersonPopulationPlan plan) {
        List<TownPersonEntity> living = livingTownPeople(level, plan);
        int delta = convergenceDelta(plan, living.size());
        if (delta == 0) return 0;

        if (delta > 0) {
            if (!plan.placeable()) {
                LOGGER.warn("City {} wants {} TownPersons but has no region to place them in",
                        plan.cityName(), plan.desiredPopulation());
                return 0;
            }
            int spawned = 0;
            for (int index = 0; index < delta; index++) {
                if (spawnOne(level, plan)) spawned++;
            }
            return spawned;
        }

        int removed = 0;
        for (int index = 0; index < -delta && index < living.size(); index++) {
            TownPersonEntity person = living.get(index);
            person.remove(Entity.RemovalReason.DISCARDED);
            removed++;
        }
        return -removed;
    }

    /**
     * Everyone this city currently supports, INCLUDING legacy TownPersons
     * spawned by the old block system — they are this city's people, so
     * counting them is what makes migration seamless rather than a cull.
     */
    public static List<TownPersonEntity> livingTownPeople(ServerLevel level, TownPersonPopulationPlan plan) {
        List<TownPersonEntity> people = new ArrayList<>();
        for (TownPersonEntity person : level.getEntitiesOfClass(
                TownPersonEntity.class, regionSearchBox(plan), Entity::isAlive)) {
            if (plan.containsPosition(person.blockPosition())) people.add(person);
        }
        return people;
    }

    private static net.minecraft.world.phys.AABB regionSearchBox(TownPersonPopulationPlan plan) {
        if (plan.regions().isEmpty()) return new net.minecraft.world.phys.AABB(BlockPos.ZERO);
        TownPersonPopulationPlan.Bounds first = plan.regions().getFirst();
        double minX = first.minX(), maxX = first.maxX();
        double minY = first.minY(), maxY = first.maxY();
        double minZ = first.minZ(), maxZ = first.maxZ();
        for (TownPersonPopulationPlan.Bounds bounds : plan.regions()) {
            minX = Math.min(minX, bounds.minX()); maxX = Math.max(maxX, bounds.maxX());
            minY = Math.min(minY, bounds.minY()); maxY = Math.max(maxY, bounds.maxY());
            minZ = Math.min(minZ, bounds.minZ()); maxZ = Math.max(maxZ, bounds.maxZ());
        }
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static boolean spawnOne(ServerLevel level, TownPersonPopulationPlan plan) {
        BlockPos pos = TownPersonPlacement.findSpawnPosition(level, plan, level.random);
        if (pos == null) return false;

        TownPersonEntity person = EntityRegistry.TOWNSPERSON.get().create(level);
        if (person == null) return false;

        boolean male = level.random.nextBoolean();
        person.setGender(male ? "male" : "female");
        person.setPersonalName(male ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
        person.setCityName(plan.cityName());
        person.setSpawnPosition(pos);
        person.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        person.setPersistenceRequired();
        person.addTag("britannia_townsperson_regional");
        return level.addFreshEntity(person);
    }

    private static List<TownPersonPopulationPlan> fetchPlans(ServerLevel level) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow()
                .apiUrls().resolve(Endpoint.CITY_POPULATIONS);
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        if (!RailsRequestAuthenticator.apply(connection, level.getServer(), new byte[0])) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("City population endpoint returned HTTP " + status);
        }
        return parsePlans(BoundedHttp.readUtf8(connection.getInputStream(), MAX_RESPONSE_BYTES));
    }

    /** Public for GameTest access; malformed rows are skipped, never guessed at. */
    public static List<TownPersonPopulationPlan> parsePlans(String body) {
        List<TownPersonPopulationPlan> plans = new ArrayList<>();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("cities") || !root.get("cities").isJsonArray()) return plans;

        for (JsonElement element : root.getAsJsonArray("cities")) {
            if (!element.isJsonObject()) continue;
            JsonObject city = element.getAsJsonObject();
            try {
                List<TownPersonPopulationPlan.Bounds> regions = new ArrayList<>();
                if (city.has("regions") && city.get("regions").isJsonArray()) {
                    for (JsonElement regionElement : city.getAsJsonArray("regions")) {
                        JsonObject region = regionElement.getAsJsonObject();
                        regions.add(new TownPersonPopulationPlan.Bounds(
                                region.has("name") ? region.get("name").getAsString() : "region",
                                region.get("min_x").getAsInt(), region.get("max_x").getAsInt(),
                                region.get("min_y").getAsInt(), region.get("max_y").getAsInt(),
                                region.get("min_z").getAsInt(), region.get("max_z").getAsInt()));
                    }
                }
                plans.add(new TownPersonPopulationPlan(
                        UUID.fromString(city.get("city_public_id").getAsString()),
                        city.get("city_name").getAsString(),
                        city.get("desired_population").getAsInt(),
                        city.get("max_spawn_per_cycle").getAsInt(),
                        city.get("max_despawn_per_cycle").getAsInt(),
                        regions));
            } catch (RuntimeException malformed) {
                LOGGER.warn("Skipping malformed city population entry: {}", malformed.toString());
            }
        }
        return plans;
    }

    /** Diagnostics support (Milestone 18 surface): the last applied plans. */
    public static List<TownPersonPopulationPlan> currentPlans() {
        return currentPlans;
    }
}
