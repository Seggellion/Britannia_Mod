package com.seggellion.britannia_mod.skill.crafting;

import com.seggellion.britannia_mod.client.screen.BlacksmithyScreen;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CraftableRegistryTest {
    @Test
    void authoritativeCatalogueHasExpectedCoverage() {
        var all = CraftableRegistry.getAll();
        assertEquals(202, all.size());
        Map<String, Long> counts = all.stream().collect(Collectors.groupingBy(CraftableDef::category, Collectors.counting()));
        assertEquals(34, counts.get("Armor"));
        assertEquals(15, counts.get("Axes"));
        assertEquals(17, counts.get("Bashing"));
        assertEquals(69, counts.get("Bladed"));
        assertEquals(4, counts.get("Cannons"));
        assertEquals(17, counts.get("Helmets"));
        assertEquals(12, counts.get("Miscellaneous"));
        assertEquals(16, counts.get("Polearms"));
        assertEquals(15, counts.get("Shields"));
        assertEquals(3, counts.get("Throwing"));
        assertEquals(120, all.stream().filter(def -> CraftableDef.isWeaponCategory(def.category())).count());
    }

    @Test
    void compoundSkillsAndRecipeFlagsSurviveImport() {
        CraftableDef tessen = CraftableRegistry.get("tessen");
        assertEquals(2, tessen.skillRequirements().size());
        assertTrue(tessen.skillRequirements().stream().anyMatch(s -> s.skillKey().equals("tailoring") && s.minValue() == 50));
        CraftableDef shieldOrb = CraftableRegistry.get("shield_orb");
        assertTrue(shieldOrb.requiresLearnedRecipe());
        assertTrue(shieldOrb.skillRequirements().stream().anyMatch(s -> s.skillKey().equals("magery") && s.minValue() == 100));
        assertFalse(CraftableRegistry.get("shard_thrasher").retainsMaterialColor());
    }

    @Test
    void repairAndSmeltRoundingAreExact() {
        assertEquals(2, BlacksmithCrafting.repairCost(3));
        assertEquals(5, BlacksmithCrafting.repairCost(10));
        assertEquals(13, BlacksmithCrafting.repairCost(25));
        assertEquals(1, BlacksmithCrafting.smeltRecovery(3));
        assertEquals(5, BlacksmithCrafting.smeltRecovery(10));
        assertEquals(12, BlacksmithCrafting.smeltRecovery(25));
    }

    @Test
    void screenExposesExactlyFiveOrderedWorkflows() {
        assertEquals(java.util.List.of(BlacksmithyScreen.Workflow.REPAIR, BlacksmithyScreen.Workflow.SMELT,
                BlacksmithyScreen.Workflow.SHIELDS, BlacksmithyScreen.Workflow.ARMOR,
                BlacksmithyScreen.Workflow.WEAPONS), BlacksmithyScreen.WORKFLOWS);
    }

    @Test
    void everyCatalogueThumbnailHasAModelResource() {
        for (CraftableDef definition : CraftableRegistry.getAll()) {
            String path = "/assets/britannia_mod/models/item/" + definition.resultItem().getPath() + ".json";
            assertNotNull(getClass().getResource(path), () -> "Missing model " + path);
        }
    }
}
