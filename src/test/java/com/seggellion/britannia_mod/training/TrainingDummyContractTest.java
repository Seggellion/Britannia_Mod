package com.seggellion.britannia_mod.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.stream.StreamSupport;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrainingDummyContractTest {
    private static final Path RESOURCES = Path.of(
            System.getProperty("britannia.projectDir", "."), "src/main/resources");

    @Test
    void policyUsesPerPlayerThreeSecondBoundaryAndOnlyApprovedSkills() {
        assertEquals(60, TrainingDummyService.COOLDOWN_TICKS);
        assertFalse(TrainingDummyService.isReady(159, 160));
        assertTrue(TrainingDummyService.isReady(160, 160));
        assertEquals(220, TrainingDummyService.nextAllowedTick(160));
        assertEquals(25.0F, TrainingDummyService.WEAPON_SKILL_CAP);

        Set<String> slugs = Set.of(
                TrainingWeaponSkill.WRESTLING.skillSlug(),
                TrainingWeaponSkill.SWORDSMANSHIP.skillSlug(),
                TrainingWeaponSkill.MACE_FIGHTING.skillSlug(),
                TrainingWeaponSkill.FENCING.skillSlug(),
                TrainingDummyService.TACTICS_SKILL_SLUG);
        assertEquals(Set.of("wrestling", "swordsmanship", "mace_fighting", "fencing", "tactics"), slugs);
        assertFalse(slugs.contains("anatomy"));
        assertFalse(slugs.contains("lumberjacking"));
    }

    @Test
    void importedModelAndAnimationMeetRequiredEnvelope() throws Exception {
        JsonObject geo = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "assets/britannia_mod/geo/training_dummy.geo.json"))).getAsJsonObject();
        assertEquals("1.12.0", geo.get("format_version").getAsString());
        JsonObject geometry = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        assertEquals("geometry.training_dummy",
                geometry.getAsJsonObject("description").get("identifier").getAsString());
        JsonArray bones = geometry.getAsJsonArray("bones");
        Set<String> boneNames = StreamSupport.stream(bones.spliterator(), false)
                .map(value -> value.getAsJsonObject().get("name").getAsString())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(boneNames.containsAll(Set.of("root", "rope", "sack2")));
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (var boneValue : bones) {
            JsonArray cubes = boneValue.getAsJsonObject().getAsJsonArray("cubes");
            if (cubes == null) continue;
            for (var cubeValue : cubes) {
                JsonObject cube = cubeValue.getAsJsonObject();
                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                double ox = origin.get(0).getAsDouble(), oy = origin.get(1).getAsDouble();
                double sx = size.get(0).getAsDouble(), sy = size.get(1).getAsDouble();
                minX = Math.min(minX, ox); maxX = Math.max(maxX, ox + sx);
                minY = Math.min(minY, oy); maxY = Math.max(maxY, oy + sy);
            }
        }
        assertTrue(minX >= -32.0D && maxX <= 32.0D,
                "training dummy exceeds its supported horizontal render envelope");
        assertEquals(0.0D, minY, 0.0001D);
        assertEquals(48.0D, maxY, 0.0001D);

        JsonObject sack = StreamSupport.stream(bones.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(bone -> "sack2".equals(bone.get("name").getAsString()))
                .findFirst()
                .orElseThrow();
        JsonObject arrow = StreamSupport.stream(sack.getAsJsonArray("cubes").spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(cube -> cube.getAsJsonArray("size").get(2).getAsDouble() == 0.0D)
                .filter(cube -> cube.getAsJsonObject("uv").has("north"))
                .filter(cube -> cube.getAsJsonObject("uv").getAsJsonObject("north")
                        .getAsJsonArray("uv").get(0).getAsDouble() == 64.0D)
                .findFirst()
                .orElseThrow();
        JsonObject sackBody = StreamSupport.stream(sack.getAsJsonArray("cubes").spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(cube -> cube.getAsJsonArray("size").get(2).getAsDouble() == 8.0D)
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of("north"), arrow.getAsJsonObject("uv").keySet(),
                "arrow decal must not render coincident back/edge faces");
        assertTrue(sackBody.getAsJsonArray("origin").get(2).getAsDouble()
                        - arrow.getAsJsonArray("origin").get(2).getAsDouble() >= 0.1D,
                "arrow decal must be separated from the sack face to prevent z-fighting");

        JsonObject animationRoot = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "assets/britannia_mod/animations/training_dummy.animation.json"))).getAsJsonObject();
        JsonObject hit = animationRoot.getAsJsonObject("animations").getAsJsonObject("animation.training_dummy.hit");
        assertFalse(hit.get("loop").getAsBoolean());
        assertEquals(1.0D, hit.get("animation_length").getAsDouble(), 0.0001D);
    }

    @Test
    void interactionSupportsBothButtonsAndThreeRandomHitSounds() throws Exception {
        String clientHandler = Files.readString(Path.of(
                System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/client/TrainingDummyClientAttackHandler.java"));
        assertTrue(clientHandler.contains("event.isAttack()"));
        assertTrue(clientHandler.contains("GameType.ADVENTURE"));

        String payload = Files.readString(Path.of(
                System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/network/payload/TrainingDummyHitC2SPayload.java"));
        assertTrue(payload.contains("getGameModeForPlayer() != GameType.ADVENTURE"));
        assertTrue(payload.contains("canInteractWithBlock"));

        String block = Files.readString(Path.of(
                System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/block/TrainingDummyBlock.java"));
        assertTrue(block.contains("ItemInteractionResult useItemOn"));
        assertTrue(block.contains("player.isCreative()"));

        JsonObject sounds = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "assets/britannia_mod/sounds.json"))).getAsJsonObject();
        JsonArray variants = sounds.getAsJsonObject("training_dummy_hit").getAsJsonArray("sounds");
        Set<String> names = new HashSet<>();
        variants.forEach(value -> names.add(value.getAsJsonObject().get("name").getAsString()));
        assertEquals(Set.of(
                "britannia_mod:hit06", "britannia_mod:hit07", "britannia_mod:hit08"), names);

        for (String file : Set.of("hit06.ogg", "hit07.ogg", "hit08.ogg")) {
            assertTrue(Files.size(RESOURCES.resolve("assets/britannia_mod/sounds").resolve(file)) > 0L);
        }
    }
}
