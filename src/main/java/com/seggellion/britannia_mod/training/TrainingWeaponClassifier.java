package com.seggellion.britannia_mod.training;

import com.seggellion.britannia_mod.item.BlacksmithEquipmentItem;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** Stable type/catalogue-based weapon mapping for training; never inspects display names. */
public final class TrainingWeaponClassifier {
    private static final Set<String> FENCING_BLADED_IDS = Set.of(
            "assassin_spike", "charged_assassin_spike", "true_assassin_spike",
            "wounding_assassin_spike", "magekiller_assassin_spike", "dagger",
            "gargish_dagger", "kryss", "gargish_kryss", "leafblade",
            "leafblade_of_ease", "true_leafblade", "magekiller_leafblade",
            "sai", "shortblade", "tekagi", "gargish_tekagi");

    private TrainingWeaponClassifier() {
    }

    public static Optional<TrainingWeaponSkill> classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String path = itemId == null ? "" : itemId.getPath();
        if (FENCING_BLADED_IDS.contains(path)) {
            return Optional.of(TrainingWeaponSkill.FENCING);
        }
        if (item instanceof AxeItem) {
            return Optional.of(TrainingWeaponSkill.SWORDSMANSHIP);
        }
        if (item instanceof MaceItem) {
            return Optional.of(TrainingWeaponSkill.MACE_FIGHTING);
        }
        if (item instanceof TridentItem) {
            return Optional.of(TrainingWeaponSkill.FENCING);
        }
        if (item instanceof BlacksmithEquipmentItem equipment) {
            return classifyCatalogueCategory(equipment.definition().category(), path);
        }
        if (item instanceof SwordItem) {
            return Optional.of(TrainingWeaponSkill.SWORDSMANSHIP);
        }
        return Optional.empty();
    }

    static Optional<TrainingWeaponSkill> classifyCatalogueCategory(String category, String itemPath) {
        if (FENCING_BLADED_IDS.contains(itemPath)) {
            return Optional.of(TrainingWeaponSkill.FENCING);
        }
        if (category == null) {
            return Optional.empty();
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "axes", "bladed" -> Optional.of(TrainingWeaponSkill.SWORDSMANSHIP);
            case "bashing" -> Optional.of(TrainingWeaponSkill.MACE_FIGHTING);
            case "polearms" -> Optional.of(TrainingWeaponSkill.FENCING);
            default -> Optional.empty();
        };
    }
}
