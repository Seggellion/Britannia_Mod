package com.seggellion.britannia_mod.skill.crafting;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CraftableRegistry {
    private static final Map<String, CraftableDef> BLACKSMITH_CRAFTABLES = new ConcurrentHashMap<>();

    private static IngredientRequirement ing(String materialKey, int amount) {
        return new IngredientRequirement(materialKey, amount);
    }

    private static SkillRequirement skill(String skillKey, float minValue) {
        return new SkillRequirement(skillKey, minValue);
    }

    private static void register(
            String id,
            String category,
            String displayName,
            String itemPath,
            boolean exceptionalOnly,
            IngredientRequirement[] ingredients,
            SkillRequirement[] skillRequirements
    ) {
        ResourceLocation resultItem = ResourceLocation.fromNamespaceAndPath("britannia_mod", itemPath);
        BLACKSMITH_CRAFTABLES.put(
                id,
                new CraftableDef(
                        id,
                        category,
                        displayName,
                        resultItem,
                        List.of(ingredients),
                        List.of(skillRequirements),
                        exceptionalOnly
                )
        );
    }

    public static void init() {
        if (!BLACKSMITH_CRAFTABLES.isEmpty()) return;

        // --- Armor ---
        register("chainmail_coif", "Armor", "Chainmail Coif", "chainmail_coif", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 14.5f) });

        register("chainmail_leggings", "Armor", "Chainmail Leggings", "chainmail_leggings", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 36.7f) });

        register("chainmail_tunic", "Armor", "Chainmail Tunic", "chainmail_tunic", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 39.1f) });

        register("dragon_barding_deed", "Armor", "Dragon Barding Deed", "dragon_barding_deed", false,
                new IngredientRequirement[]{ ing("ingot", 750) },
                new SkillRequirement[]{ skill("blacksmith", 72.5f) });

        register("female_plate", "Armor", "Female Plate", "female_plate", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 44.1f) });

        register("gargish_amulet", "Armor", "Gargish Amulet", "gargish_amulet", false,
                new IngredientRequirement[]{ ing("ingot", 3) },
                new SkillRequirement[]{ skill("blacksmith", 60.0f) });

        register("gargish_platemail_arms", "Armor", "Gargish Platemail Arms", "gargish_platemail_arms", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 66.3f) });

        register("gargish_platemail_chest", "Armor", "Gargish Platemail Chest", "gargish_platemail_chest", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("gargish_platemail_kilt", "Armor", "Gargish Platemail Kilt", "gargish_platemail_kilt", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 58.9f) });

        register("gargish_platemail_leggings", "Armor", "Gargish Platemail Leggings", "gargish_platemail_leggings", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 68.8f) });

        register("platemail_arms", "Armor", "Platemail Arms", "platemail_arms", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 66.3f) });

        register("platemail_do", "Armor", "Platemail Do", "platemail_do", false,
                new IngredientRequirement[]{ ing("ingot", 28) },
                new SkillRequirement[]{ skill("blacksmith", 87.0f) });

        register("platemail_gloves", "Armor", "Platemail Gloves", "platemail_gloves", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 58.9f) });

        register("platemail_gorget", "Armor", "Platemail Gorget", "platemail_gorget", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 56.4f) });

        register("platemail_haidate", "Armor", "Platemail Haidate", "platemail_haidate", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 65.0f) });

        register("platemail_hiro_sode", "Armor", "Platemail Hiro Sode", "platemail_hiro_sode", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 80.0f) });

        register("platemail_legs", "Armor", "Platemail Legs", "platemail_legs", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 68.8f) });

        register("platemail_mempo", "Armor", "Platemail Mempo", "platemail_mempo", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 80.0f) });

        register("platemail_suneate", "Armor", "Platemail Suneate", "platemail_suneate", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 65.0f) });

        register("platemail_tunic", "Armor", "Platemail Tunic", "platemail_tunic", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("ringmail_gloves", "Armor", "Ringmail Gloves", "ringmail_gloves", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 12.0f) });

        register("ringmail_leggings", "Armor", "Ringmail Leggings", "ringmail_leggings", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 19.4f) });

        register("ringmail_sleeves", "Armor", "Ringmail Sleeves", "ringmail_sleeves", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 16.9f) });

        register("ringmail_tunic", "Armor", "Ringmail Tunic", "ringmail_tunic", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 21.9f) });

        // --- Axes ---
        register("axe", "Axes", "Axe", "axe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("battle_axe", "Axes", "Battle Axe", "battle_axe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 30.5f) });

        register("double_axe", "Axes", "Double Axe", "double_axe", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 29.3f) });

        register("dual_short_axes", "Axes", "Dual Short Axes", "dual_short_axes", false,
                new IngredientRequirement[]{ ing("ingot", 24) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("executioners_axe", "Axes", "Executioner's Axe", "executioners_axe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("gargish_axe", "Axes", "Gargish Axe", "gargish_axe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("gargish_battle_axe", "Axes", "Gargish Battle Axe", "gargish_battle_axe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 30.5f) });

        register("guardian_axe", "Axes", "Guardian Axe", "guardian_axe", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("heavy_ornate_axe", "Axes", "Heavy Ornate Axe", "heavy_ornate_axe", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("turquoise", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("large_battle_axe", "Axes", "Large Battle Axe", "large_battle_axe", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 28.0f) });

        register("ornate_axe", "Axes", "Ornate Axe", "ornate_axe", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("singing_axe", "Axes", "Singing Axe", "singing_axe", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("brilliant_amber", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("thundering_axe", "Axes", "Thundering Axe", "thundering_axe", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("ecru_citrine", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("two_handed_axe", "Axes", "Two-Handed Axe", "two_handed_axe", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 33.0f) });

        register("war_axe", "Axes", "War Axe", "war_axe", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 39.1f) });

        // --- Bashing ---
        register("diamond_mace", "Bashing", "Diamond Mace", "diamond_mace", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("disc_mace", "Bashing", "Disc Mace", "disc_mace", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("emerald_mace", "Bashing", "Emerald Mace", "emerald_mace", true,
                new IngredientRequirement[]{ ing("ingot", 20), ing("perfect_emerald", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("gargish_maul", "Bashing", "Gargish Maul", "gargish_maul", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 19.4f) });

        register("gargish_tessen", "Bashing", "Gargish Tessen", "gargish_tessen", false,
                new IngredientRequirement[]{ ing("cloth", 10) },
                new SkillRequirement[]{ skill("blacksmith", 50.0f) });

        register("gargish_war_hammer", "Bashing", "Gargish War Hammer", "gargish_war_hammer", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("hammer_pick", "Bashing", "Hammer Pick", "hammer_pick", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("mace", "Bashing", "Mace", "mace", false,
                new IngredientRequirement[]{ ing("ingot", 6) },
                new SkillRequirement[]{ skill("blacksmith", 14.5f) });

        register("maul", "Bashing", "Maul", "maul", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 19.4f) });

        register("ruby_mace", "Bashing", "Ruby Mace", "ruby_mace", true,
                new IngredientRequirement[]{ ing("ingot", 20), ing("fire_ruby", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("sapphire_mace", "Bashing", "Sapphire Mace", "sapphire_mace", true,
                new IngredientRequirement[]{ ing("ingot", 20), ing("dark_sapphire", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("scepter", "Bashing", "Scepter", "scepter", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 21.6f) });

        register("shard_thrasher", "Bashing", "Shard Thrasher", "shard_thrasher", true,
                new IngredientRequirement[]{ ing("ingot", 20), ing("corruption", 10), ing("eye_of_the_travesty", 1), ing("muculent", 10) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("silver_etched_mace", "Bashing", "Silver-Etched Mace", "silver_etched_mace", true,
                new IngredientRequirement[]{ ing("ingot", 20), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("tessen", "Bashing", "Tessen", "tessen", false,
                new IngredientRequirement[]{ ing("ingot", 16), ing("cloth", 10) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f), skill("tailoring", 50.0f) });

        register("war_hammer", "Bashing", "War Hammer", "war_hammer", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 34.2f) });

        register("war_mace", "Bashing", "War Mace", "war_mace", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 28.0f) });

        // --- Bladed ---
        register("adventurers_machete", "Bladed", "Adventurer's Machete", "adventurers_machete", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("white_pearl", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("assassin_spike", "Bladed", "Assassin Spike", "assassin_spike", false,
                new IngredientRequirement[]{ ing("ingot", 9) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("bloodblade", "Bladed", "Bloodblade", "bloodblade", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 44.1f) });

        register("bone_harvester", "Bladed", "Bone Harvester", "bone_harvester", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 33.0f) });

        register("bone_machete", "Bladed", "Bone Machete", "bone_machete", true,
                new IngredientRequirement[]{ ing("bone", 6) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("broadsword", "Bladed", "Broadsword", "broadsword", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 35.4f) });

        register("butchers_war_cleaver", "Bladed", "Butcher's War Cleaver", "butchers_war_cleaver", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("turquoise", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("charged_assassin_spike", "Bladed", "Charged Assassin Spike", "charged_assassin_spike", true,
                new IngredientRequirement[]{ ing("ingot", 9), ing("ecru_citrine", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("cold_forged_blade", "Bladed", "Cold Forged Blade", "cold_forged_blade", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("blight", 10), ing("grizzled_bones", 1), ing("taint", 10) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("corrupted_rune_blade", "Bladed", "Corrupted Rune Blade", "corrupted_rune_blade", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("corruption", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("crescent_blade", "Bladed", "Crescent Blade", "crescent_blade", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("cutlass", "Bladed", "Cutlass", "cutlass", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 24.3f) });
        
        register("dagger", "Bladed", "Dagger", "dagger", false,
            new IngredientRequirement[]{ ing("ingot", 3) },
            new SkillRequirement[]{ skill("blacksmithy", 0.0f) });

        register("daisho", "Bladed", "Daisho", "daisho", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 60.0f) });

        register("darkglow_scimitar", "Bladed", "Darkglow Scimitar", "darkglow_scimitar", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("dark_sapphire", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("diseased_machete", "Bladed", "Diseased Machete", "diseased_machete", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("blight", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("dread_sword", "Bladed", "Dread Sword", "dread_sword", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("elven_machete", "Bladed", "Elven Machete", "elven_machete", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("elven_spellblade", "Bladed", "Elven Spellblade", "elven_spellblade", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("fiery_spellblade", "Bladed", "Fiery Spellblade", "fiery_spellblade", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("fire_ruby", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("gargish_bone_harvester", "Bladed", "Gargish Bone Harvester", "gargish_bone_harvester", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 33.0f) });

        register("gargish_dagger", "Bladed", "Gargish Dagger", "gargish_dagger", false,
                new IngredientRequirement[]{ ing("ingot", 3) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("gargish_daisho", "Bladed", "Gargish Daisho", "gargish_daisho", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 60.0f) });

        register("gargish_katana", "Bladed", "Gargish Katana", "gargish_katana", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 44.1f) });

        register("gargish_kryss", "Bladed", "Gargish Kryss", "gargish_kryss", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 36.7f) });

        register("gargish_talwar", "Bladed", "Gargish Talwar", "gargish_talwar", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("gargish_tekagi", "Bladed", "Gargish Tekagi", "gargish_tekagi", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 55.0f) });

        register("icy_scimitar", "Bladed", "Icy Scimitar", "icy_scimitar", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("dark_sapphire", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("icy_spellblade", "Bladed", "Icy Spellblade", "icy_spellblade", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("turquoise", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("kama", "Bladed", "Kama", "kama", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 40.0f) });

        register("katana", "Bladed", "Katana", "katana", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 44.1f) });

        register("knights_war_cleaver", "Bladed", "Knight's War Cleaver", "knights_war_cleaver", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("perfect_emerald", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("kryss", "Bladed", "Kryss", "kryss", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 36.7f) });

        register("lajatang", "Bladed", "Lajatang", "lajatang", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 80.0f) });

        register("leafblade", "Bladed", "Leafblade", "leafblade", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("leafblade_of_ease", "Bladed", "Leafblade of Ease", "leafblade_of_ease", true,
                new IngredientRequirement[]{ ing("ingot", 12), ing("perfect_emerald", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("longsword", "Bladed", "Longsword", "longsword", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 28.0f) });

        register("luckblade", "Bladed", "Luckblade", "luckblade", true,
                new IngredientRequirement[]{ ing("ingot", 12), ing("white_pearl", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("luminous_rune_blade", "Bladed", "Luminous Rune Blade", "luminous_rune_blade", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("corruption", 10), ing("grizzled_bones", 1), ing("putrefaction", 10) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("machete_of_defense", "Bladed", "Machete of Defense", "machete_of_defense", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("brilliant_amber", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("mages_rune_blade", "Bladed", "Mage's Rune Blade", "mages_rune_blade", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("magekiller_assassin_spike", "Bladed", "Magekiller Assassin Spike", "magekiller_assassin_spike", true,
                new IngredientRequirement[]{ ing("ingot", 9), ing("brilliant_amber", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("magekiller_leafblade", "Bladed", "Magekiller Leafblade", "magekiller_leafblade", true,
                new IngredientRequirement[]{ ing("ingot", 12), ing("fire_ruby", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("no_dachi", "Bladed", "No-Dachi", "no_dachi", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("orcish_machete", "Bladed", "Orcish Machete", "orcish_machete", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("scourge", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("overseer_sundered_blade", "Bladed", "Overseer Sundered Blade", "overseer_sundered_blade", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("blight", 10), ing("grizzled_bones", 1), ing("scourge", 10) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("radiant_scimitar", "Bladed", "Radiant Scimitar", "radiant_scimitar", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("rune_blade", "Bladed", "Rune Blade", "rune_blade", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("rune_blade_of_knowledge", "Bladed", "Rune Blade of Knowledge", "rune_blade_of_knowledge", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("ecru_citrine", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("rune_carving_knife", "Bladed", "Rune Carving Knife", "rune_carving_knife", true,
                new IngredientRequirement[]{ ing("ingot", 9), ing("dread_horn_mane", 1), ing("muculent", 10), ing("putrefaction", 10) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("runesabre", "Bladed", "Runesabre", "runesabre", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("turquoise", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("exodus_sacrificial_dagger", "Bladed", "Exodus Sacrificial Dagger", "exodus_sacrificial_dagger", false,
                new IngredientRequirement[]{ ing("ingot", 12), ing("small_piece_of_blackrock", 10), ing("blue_diamond", 2), ing("fire_ruby", 2) },
                new SkillRequirement[]{ skill("blacksmith", 95.0f) });

        register("sai", "Bladed", "Sai", "sai", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 50.0f) });

        register("scimitar", "Bladed", "Scimitar", "scimitar", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 31.7f) });

        register("serrated_war_cleaver", "Bladed", "Serrated War Cleaver", "serrated_war_cleaver", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("ecru_citrine", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("shortblade", "Bladed", "Shortblade", "shortblade", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 28.0f) });

        register("shuriken", "Bladed", "Shuriken", "shuriken", false,
                new IngredientRequirement[]{ ing("ingot", 5) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("spellblade_of_defense", "Bladed", "Spellblade of Defense", "spellblade_of_defense", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("white_pearl", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("tekagi", "Bladed", "Tekagi", "tekagi", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 55.0f) });

        register("true_assassin_spike", "Bladed", "True Assassin Spike", "true_assassin_spike", true,
                new IngredientRequirement[]{ ing("ingot", 9), ing("dark_sapphire", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("true_leafblade", "Bladed", "True Leafblade", "true_leafblade", true,
                new IngredientRequirement[]{ ing("ingot", 12), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("true_radiant_scimitar", "Bladed", "True Radiant Scimitar", "true_radiant_scimitar", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("brilliant_amber", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("true_spellblade", "Bladed", "True Spellblade", "true_spellblade", true,
                new IngredientRequirement[]{ ing("ingot", 14), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("true_war_cleaver", "Bladed", "True War Cleaver", "true_war_cleaver", true,
                new IngredientRequirement[]{ ing("ingot", 18), ing("brilliant_amber", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("twinkling_scimitar", "Bladed", "Twinkling Scimitar", "twinkling_scimitar", true,
                new IngredientRequirement[]{ ing("ingot", 15), ing("dark_sapphire", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("viking_sword", "Bladed", "Viking Sword", "viking_sword", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 24.3f) });

        register("wakizashi", "Bladed", "Wakizashi", "wakizashi", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 50.0f) });

        register("war_cleaver", "Bladed", "War Cleaver", "war_cleaver", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("wounding_assassin_spike", "Bladed", "Wounding Assassin Spike", "wounding_assassin_spike", true,
                new IngredientRequirement[]{ ing("ingot", 9), ing("perfect_emerald", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        // --- Cannons ---
        register("heavy_cannonball", "Cannons", "Heavy Cannonball", "heavy_cannonball", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 10.0f) });

        register("heavy_grapeshot", "Cannons", "Heavy Grapeshot", "heavy_grapeshot", false,
                new IngredientRequirement[]{ ing("ingot", 12), ing("cloth", 2) },
                new SkillRequirement[]{ skill("blacksmith", 15.0f) });

        register("heavy_ship_cannon", "Cannons", "Heavy Ship Cannon", "heavy_ship_cannon", false,
                new IngredientRequirement[]{ ing("ingot", 1800), ing("board", 75) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f), skill("carpentry", 70.0f) });

        register("light_cannonball", "Cannons", "Light Cannonball", "light_cannonball", false,
                new IngredientRequirement[]{ ing("ingot", 6) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("light_grapeshot", "Cannons", "Light Grapeshot", "light_grapeshot", false,
                new IngredientRequirement[]{ ing("ingot", 6), ing("cloth", 1) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("light_ship_cannon", "Cannons", "Light Ship Cannon", "light_ship_cannon", false,
                new IngredientRequirement[]{ ing("ingot", 900), ing("board", 50) },
                new SkillRequirement[]{ skill("blacksmith", 65.0f), skill("carpentry", 65.0f) });

        // --- Dragon Scale Armor ---
        register("dragon_breastplate", "Dragon Scale Armor", "Dragon Breastplate", "dragon_breastplate", false,
                new IngredientRequirement[]{ ing("dragon_scale", 36) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("dragon_gloves", "Dragon Scale Armor", "Dragon Gloves", "dragon_gloves", false,
                new IngredientRequirement[]{ ing("dragon_scale", 16) },
                new SkillRequirement[]{ skill("blacksmith", 68.9f) });

        register("dragon_helm", "Dragon Scale Armor", "Dragon Helm", "dragon_helm", false,
                new IngredientRequirement[]{ ing("dragon_scale", 20) },
                new SkillRequirement[]{ skill("blacksmith", 72.6f) });

        register("dragon_leggings", "Dragon Scale Armor", "Dragon Leggings", "dragon_leggings", false,
                new IngredientRequirement[]{ ing("dragon_scale", 28) },
                new SkillRequirement[]{ skill("blacksmith", 78.8f) });

        register("dragon_sleeves", "Dragon Scale Armor", "Dragon Sleeves", "dragon_sleeves", false,
                new IngredientRequirement[]{ ing("dragon_scale", 24) },
                new SkillRequirement[]{ skill("blacksmith", 76.3f) });

        // --- Helmets ---
        register("bascinet", "Helmets", "Bascinet", "bascinet", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 8.3f) });

        register("chainmail_hatsuburi", "Helmets", "Chainmail Hatsuburi", "chainmail_hatsuburi", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 30.0f) });

        register("circlet", "Helmets", "Circlet", "circlet", false,
                new IngredientRequirement[]{ ing("ingot", 6) },
                new SkillRequirement[]{ skill("blacksmith", 62.1f) });

        register("close_helmet", "Helmets", "Close Helmet", "close_helmet", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 37.9f) });

        register("decorative_platemail_kabuto", "Helmets", "Decorative Platemail Kabuto", "decorative_platemail_kabuto", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 90.0f) });

        register("gemmed_circlet", "Helmets", "Gemmed Circlet", "gemmed_circlet", false,
                new IngredientRequirement[]{ ing("ingot", 6), ing("amethyst", 1), ing("blue_diamond", 1), ing("tourmaline", 1) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("heavy_platemail_jingasa", "Helmets", "Heavy Platemail Jingasa", "heavy_platemail_jingasa", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("helmet", "Helmets", "Helmet", "helmet", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 37.9f) });

        register("light_platemail_jingasa", "Helmets", "Light Platemail Jingasa", "light_platemail_jingasa", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("norse_helm", "Helmets", "Norse Helm", "norse_helm", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 37.9f) });

        register("plate_helm", "Helmets", "Plate Helm", "plate_helm", false,
                new IngredientRequirement[]{ ing("ingot", 15) },
                new SkillRequirement[]{ skill("blacksmith", 62.6f) });

        register("platemail_battle_kabuto", "Helmets", "Platemail Battle Kabuto", "platemail_battle_kabuto", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 90.0f) });

        register("platemail_hatsuburi", "Helmets", "Platemail Hatsuburi", "platemail_hatsuburi", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("royal_circlet", "Helmets", "Royal Circlet", "royal_circlet", false,
                new IngredientRequirement[]{ ing("ingot", 6) },
                new SkillRequirement[]{ skill("blacksmith", 70.0f) });

        register("small_platemail_jingasa", "Helmets", "Small Platemail Jingasa", "small_platemail_jingasa", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("standard_platemail_kabuto", "Helmets", "Standard Platemail Kabuto", "standard_platemail_kabuto", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 90.0f) });

        // --- Miscellaneous ---
        register("crushed_glass", "Miscellaneous", "Crushed Glass", "crushed_glass", false,
                new IngredientRequirement[]{ ing("glass_sword", 5), ing("blue_diamond", 1) },
                new SkillRequirement[]{ skill("blacksmith", 110.0f) });

        register("metal_keg", "Miscellaneous", "Metal Keg", "metal_keg", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("polished_plating", "Miscellaneous", "Polished Plating", "polished_plating", false,
                new IngredientRequirement[]{ ing("malleable_alloy", 20), ing("polish", 1) },
                new SkillRequirement[]{ skill("blacksmith", 100.0f) });

        register("powdered_iron", "Miscellaneous", "Powdered Iron", "powdered_iron", false,
                new IngredientRequirement[]{ ing("ingot", 20), ing("white_pearl", 1) },
                new SkillRequirement[]{ skill("blacksmith", 110.0f) });

        register("scoured_plating", "Miscellaneous", "Scoured Plating", "scoured_plating", false,
                new IngredientRequirement[]{ ing("malleable_alloy", 20), ing("scour", 1) },
                new SkillRequirement[]{ skill("blacksmith", 100.0f) });

        // --- Polearms ---
        register("bardiche", "Polearms", "Bardiche", "bardiche", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 31.7f) });

        register("bladed_staff", "Polearms", "Bladed Staff", "bladed_staff", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 40.0f) });

        register("double_bladed_staff", "Polearms", "Double Bladed Staff", "double_bladed_staff", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 45.0f) });

        register("dual_pointed_spear", "Polearms", "Dual Pointed Spear", "dual_pointed_spear", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 47.0f) });

        register("gargish_bardiche", "Polearms", "Gargish Bardiche", "gargish_bardiche", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 31.7f) });

        register("gargish_lance", "Polearms", "Gargish Lance", "gargish_lance", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 48.0f) });

        register("gargish_pike", "Polearms", "Gargish Pike", "gargish_pike", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 47.0f) });

        register("gargish_scythe", "Polearms", "Gargish Scythe", "gargish_scythe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("gargish_war_fork", "Polearms", "Gargish War Fork", "gargish_war_fork", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 42.9f) });

        register("halberd", "Polearms", "Halberd", "halberd", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 39.1f) });

        register("lance", "Polearms", "Lance", "lance", false,
                new IngredientRequirement[]{ ing("ingot", 20) },
                new SkillRequirement[]{ skill("blacksmith", 48.0f) });

        register("pike", "Polearms", "Pike", "pike", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 47.0f) });

        register("scythe", "Polearms", "Scythe", "scythe", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 39.0f) });

        register("short_spear", "Polearms", "Short Spear", "short_spear", false,
                new IngredientRequirement[]{ ing("ingot", 6) },
                new SkillRequirement[]{ skill("blacksmith", 45.3f) });

        register("spear", "Polearms", "Spear", "spear", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 49.0f) });

        register("war_fork", "Polearms", "War Fork", "war_fork", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 42.9f) });

        // --- Shields ---
        register("bronze_shield", "Shields", "Bronze Shield", "bronze_shield", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("buckler", "Shields", "Buckler", "buckler", false,
                new IngredientRequirement[]{ ing("ingot", 10) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("chaos_shield", "Shields", "Chaos Shield", "chaos_shield", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("gargish_chaos_shield", "Shields", "Gargish Chaos Shield", "gargish_chaos_shield", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("gargish_kite_shield", "Shields", "Gargish Kite Shield", "gargish_kite_shield", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 4.6f) });

        register("gargish_order_shield", "Shields", "Gargish Order Shield", "gargish_order_shield", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("heater_shield", "Shields", "Heater Shield", "heater_shield", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 24.3f) });

        register("large_plate_shield", "Shields", "Large Plate Shield", "large_plate_shield", false,
                new IngredientRequirement[]{ ing("ingot", 18) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("medium_plate_shield", "Shields", "Medium Plate Shield", "medium_plate_shield", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("metal_kite_shield", "Shields", "Metal Kite Shield", "metal_kite_shield", false,
                new IngredientRequirement[]{ ing("ingot", 16) },
                new SkillRequirement[]{ skill("blacksmith", 4.6f) });

        register("metal_shield", "Shields", "Metal Shield", "metal_shield", false,
                new IngredientRequirement[]{ ing("ingot", 14) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("order_shield", "Shields", "Order Shield", "order_shield", false,
                new IngredientRequirement[]{ ing("ingot", 25) },
                new SkillRequirement[]{ skill("blacksmith", 85.0f) });

        register("small_plate_shield", "Shields", "Small Plate Shield", "small_plate_shield", false,
                new IngredientRequirement[]{ ing("ingot", 12) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        register("tear_kite_shield", "Shields", "Tear Kite Shield", "tear_kite_shield", false,
                new IngredientRequirement[]{ ing("ingot", 8) },
                new SkillRequirement[]{ skill("blacksmith", 0.0f) });

        // --- Throwing ---
        register("boomerang", "Throwing", "Boomerang", "boomerang", false,
                new IngredientRequirement[]{ ing("ingot", 5) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("cyclone", "Throwing", "Cyclone", "cyclone", false,
                new IngredientRequirement[]{ ing("ingot", 9) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });

        register("soul_glaive", "Throwing", "Soul Glaive", "soul_glaive", false,
                new IngredientRequirement[]{ ing("ingot", 9) },
                new SkillRequirement[]{ skill("blacksmith", 75.0f) });
    }

    public static CraftableDef get(String id) {
        return BLACKSMITH_CRAFTABLES.get(id);
    }

    public static List<CraftableDef> getAll() {
        return new ArrayList<>(BLACKSMITH_CRAFTABLES.values());
    }

    public static List<CraftableDef> getByCategory(String category) {
        return BLACKSMITH_CRAFTABLES.values().stream()
                .filter(def -> def.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public static List<String> getCategories() {
        return BLACKSMITH_CRAFTABLES.values().stream()
                .map(CraftableDef::category)
                .distinct()
                .collect(Collectors.toList());
    }
}