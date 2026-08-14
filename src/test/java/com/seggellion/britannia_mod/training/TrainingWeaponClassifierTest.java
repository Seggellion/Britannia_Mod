package com.seggellion.britannia_mod.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class TrainingWeaponClassifierTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mapsCatalogueCategoriesWithoutDisplayNameMatching() {
        assertEquals(Optional.of(TrainingWeaponSkill.SWORDSMANSHIP),
                TrainingWeaponClassifier.classifyCatalogueCategory("Axes", "executioners_axe"));
        assertEquals(Optional.of(TrainingWeaponSkill.SWORDSMANSHIP),
                TrainingWeaponClassifier.classifyCatalogueCategory("Bladed", "longsword"));
        assertEquals(Optional.of(TrainingWeaponSkill.MACE_FIGHTING),
                TrainingWeaponClassifier.classifyCatalogueCategory("Bashing", "war_hammer"));
        assertEquals(Optional.of(TrainingWeaponSkill.FENCING),
                TrainingWeaponClassifier.classifyCatalogueCategory("Polearms", "spear"));
        assertEquals(Optional.of(TrainingWeaponSkill.FENCING),
                TrainingWeaponClassifier.classifyCatalogueCategory("Bladed", "kryss"));
        assertTrue(TrainingWeaponClassifier.classifyCatalogueCategory("Throwing", "boomerang").isEmpty());
    }

    @Test
    void mapsVanillaWeaponTypesAndBareHandsWhileRejectingUnsupportedItems() {
        assertEquals(Optional.of(TrainingWeaponSkill.SWORDSMANSHIP),
                TrainingWeaponClassifier.classify(new ItemStack(Items.IRON_AXE)));
        assertEquals(Optional.of(TrainingWeaponSkill.SWORDSMANSHIP),
                TrainingWeaponClassifier.classify(new ItemStack(Items.IRON_SWORD)));
        assertEquals(Optional.of(TrainingWeaponSkill.MACE_FIGHTING),
                TrainingWeaponClassifier.classify(new ItemStack(Items.MACE)));
        assertEquals(Optional.of(TrainingWeaponSkill.FENCING),
                TrainingWeaponClassifier.classify(new ItemStack(Items.TRIDENT)));
        assertTrue(TrainingWeaponClassifier.classify(new ItemStack(Items.BOW)).isEmpty());
        assertEquals(Optional.of(TrainingWeaponSkill.WRESTLING),
                TrainingWeaponClassifier.classify(ItemStack.EMPTY));
    }
}
