package com.seggellion.britannia_mod.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
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
                TrainingWeaponSkill.SWORDSMANSHIP.skillSlug(),
                TrainingWeaponSkill.MACE_FIGHTING.skillSlug(),
                TrainingWeaponSkill.FENCING.skillSlug(),
                TrainingDummyService.TACTICS_SKILL_SLUG);
        assertEquals(Set.of("swordsmanship", "mace_fighting", "fencing", "tactics"), slugs);
        assertFalse(slugs.contains("wrestling"));
        assertFalse(slugs.contains("anatomy"));
        assertFalse(slugs.contains("lumberjacking"));
    }

    @Test
    void importedModelAndAnimationMeetRequiredEnvelope() throws Exception {
        JsonObject geo = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "assets/britannia_mod/geo/training_dummy.geo.json"))).getAsJsonObject();
        JsonArray bones = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
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
        assertEquals(-24.0D, minX, 0.0001D);
        assertEquals(8.0D, maxX, 0.0001D);
        assertEquals(0.0D, minY, 0.0001D);
        assertEquals(48.0D, maxY, 0.0001D);

        JsonObject animationRoot = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "assets/britannia_mod/animations/training_dummy.animation.json"))).getAsJsonObject();
        JsonObject hit = animationRoot.getAsJsonObject("animations").getAsJsonObject("animation.training_dummy.hit");
        assertFalse(hit.get("loop").getAsBoolean());
        assertEquals(1.0D, hit.get("animation_length").getAsDouble(), 0.0001D);
    }
}
