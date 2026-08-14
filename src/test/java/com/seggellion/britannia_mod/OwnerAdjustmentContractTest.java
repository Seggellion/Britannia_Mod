package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.entity.IbisVariantPolicy;
import com.seggellion.britannia_mod.entity.FlamingoVariant;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contracts for the follow-up Ibis, hedge, and Flamingo owner adjustments. */
class OwnerAdjustmentContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void scarletIbisUsesThirtyFivePercentSpawnWeight() {
        assertEquals(100, IbisVariantPolicy.TOTAL_WEIGHT);
        assertEquals(35, IbisVariantPolicy.SCARLET_WEIGHT);
        for (int roll = 0; roll < 100; roll++) {
            assertEquals(roll < 35, IbisVariantPolicy.usesScarletVariant(roll));
        }
    }

    @Test
    void jhelomPopulationIsAutomaticLoadedChunkBoundAndOverworldOnly() throws Exception {
        String population = javaSource("spawner/JhelomIbisPopulation.java");
        String citySpawner = javaSource("spawner/CitySpawner.java");
        assertTrue(population.contains("MAX_POPULATION = 15"));
        assertTrue(population.contains("Level.OVERWORLD.equals(level.dimension())"));
        assertTrue(population.contains("level.hasChunkAt(column)"));
        assertTrue(population.contains("CitySpawnRules.SPAWN_ATTEMPTS"));
        assertTrue(citySpawner.contains("JhelomIbisPopulation.tick(level)"));
    }

    @Test
    void moonglowBushIdIsRemovedRatherThanAliased() throws Exception {
        assertTrue(javaSource("registry/BlockRegistry.java").contains("register(\"hedge_bush\""));
        assertTrue(javaSource("registry/ItemRegistry.java").contains("register(\"hedge_bush\""));
        assertFalse(javaSource("registry/BlockRegistry.java").contains("register(\"moonglow_bush\""));
        assertFalse(javaSource("registry/ItemRegistry.java").contains("register(\"moonglow_bush\""));
        assertFalse(Files.exists(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/blockstates/moonglow_bush.json")));
    }

    @Test
    void flamingoUsesThreePersistentRandomVariantsAndSuppliedAnimatedArt() throws Exception {
        String entities = javaSource("registry/EntityRegistry.java");
        String flamingo = javaSource("entity/FlamingoEntity.java");
        String model = javaSource("client/renderer/entity/FlamingoModel.java");
        String renderer = javaSource("client/renderer/entity/FlamingoRenderer.java");
        String client = javaSource("ClientModSetup.java");
        String spawnBlock = javaSource("block/entity/BritanniaSpawnBlockEntity.java");
        assertTrue(entities.contains("FLAMINGO_ENTITY"));
        assertTrue(entities.contains("EntityType.Builder.of(FlamingoEntity::new, MobCategory.CREATURE)"));
        assertTrue(flamingo.contains("random.nextInt(FlamingoVariant.count())"));
        assertTrue(flamingo.contains("SynchedEntityData.defineId"));
        assertTrue(flamingo.contains("tag.putInt(VARIANT_TAG"));
        assertTrue(spawnBlock.contains("MobSpawnType.SPAWNER"));
        assertTrue(spawnBlock.contains("mob.finalizeSpawn"));
        assertTrue(flamingo.contains("ModSounds.FLAMINGO_AMBIENT"));
        assertTrue(flamingo.contains("SoundEvents.PARROT_HURT"));
        assertTrue(flamingo.contains("SoundEvents.PARROT_DEATH"));
        assertTrue(renderer.contains("extends GeoEntityRenderer<FlamingoEntity>"));
        assertTrue(model.contains("textures/entity/flamingo_pink.png"));
        assertTrue(model.contains("textures/entity/flamingo_rose.png"));
        assertTrue(model.contains("textures/entity/flamingo_white.png"));
        assertFalse(client.contains("event.register(FlamingoRenderer.MODEL)"));
        assertTrue(Files.isRegularFile(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/geo/flamingo.geo.json")));
        assertTrue(Files.isRegularFile(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/animations/flamingo.animation.json")));
        for (String variant : new String[] {"pink", "rose", "white"}) {
            assertTrue(Files.isRegularFile(PROJECT.resolve(
                    "src/main/resources/assets/britannia_mod/textures/entity/flamingo_"
                            + variant + ".png")));
        }
        assertTrue(Files.isRegularFile(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/sounds/flamingo_idle.ogg")));
        assertTrue(Files.isRegularFile(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/item/flamingo_spawn_egg.json")));
        assertFalse(Files.exists(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/new_assets/flamingo.json")));
        String geometry = Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/geo/flamingo.geo.json"));
        String rightFoot = geometry.substring(geometry.indexOf("\"name\": \"rightFoot\""),
                geometry.indexOf("\"name\": \"leftLeg\""));
        String leftFoot = geometry.substring(geometry.indexOf("\"name\": \"leftFoot\""),
                geometry.indexOf("\"name\": \"tag_name\""));
        assertFalse(rightFoot.contains("\"down\":"));
        assertFalse(leftFoot.contains("\"down\":"));
        assertTrue(spawnBlock.contains("this.cooldown = 0"));
        assertTrue(spawnBlock.contains("type.getCategory() == MobCategory.MONSTER"));
        assertTrue(spawnBlock.contains("if (sl.addFreshEntity(mob))"));
    }

    @Test
    void flamingoVariantIdsAreStableForSynchronizationAndPersistence() {
        assertEquals(3, FlamingoVariant.count());
        assertEquals(FlamingoVariant.PINK, FlamingoVariant.byId(0));
        assertEquals(FlamingoVariant.ROSE, FlamingoVariant.byId(1));
        assertEquals(FlamingoVariant.WHITE, FlamingoVariant.byId(2));
        assertEquals(FlamingoVariant.PINK, FlamingoVariant.byId(-1));
        assertEquals(FlamingoVariant.PINK, FlamingoVariant.byId(3));
    }

    private static String javaSource(String relative) throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative));
    }
}
