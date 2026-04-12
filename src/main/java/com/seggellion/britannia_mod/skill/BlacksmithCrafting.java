package com.seggellion.britannia_mod.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.IngredientRequirement;
import com.seggellion.britannia_mod.skill.crafting.SkillRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
import java.util.Random;

public class BlacksmithCrafting {
    
    private static final Random RNG = new Random();
    private static final String PRIMARY_SKILL_ID = "blacksmithy";

    public static void processCraftRequest(ServerPlayer player, CraftableDef def) {
        
        float primaryMinSkill = 0f;
        float currentPrimarySkill = SkillManager.getSkill(player, PRIMARY_SKILL_ID);

        // 1. Loop through all skill requirements and ensure the player meets them
        for (SkillRequirement req : def.skillRequirements()) {
            // FIX: Use req.skillKey() instead of req.skillId()
            float playerSkill = SkillManager.getSkill(player, req.skillKey());
            
            // FIX: Use req.minValue() instead of req.minSkill()
            if (playerSkill < req.minValue()) {
                player.sendSystemMessage(Component.literal("You are not skilled enough to attempt this.").withStyle(ChatFormatting.RED));
                return;
            }
            
            // Capture the primary skill's minimum to use in our math later
            // FIX: Use req.skillKey() and req.minValue()
            if (req.skillKey().equals(PRIMARY_SKILL_ID)) {
                primaryMinSkill = req.minValue();
            }
        }

        // 2. Handle Ingredients
        // Assuming the first ingredient in the list dictates the metal required in the offhand
        if (!def.ingredients().isEmpty()) {
            IngredientRequirement primaryIng = def.ingredients().get(0);
            ItemStack offHand = player.getOffhandItem();
            
            // Note: Make sure IngredientRequirement uses .amount()! 
            // If it's named 'count' in the record, you'll need to change this to .count()
            if (offHand.getCount() < primaryIng.amount()) {
                player.sendSystemMessage(Component.literal("You do not have enough materials.").withStyle(ChatFormatting.RED));
                return;
            }
            // Consume the items
            offHand.shrink(primaryIng.amount());
        }

        // 3. UO Logic: Success chance scales from 50% at minSkill up to 100% at minSkill + 50.0
        // We use the primaryMinSkill we found in the loop above
        double successChance = 0.50 + ((currentPrimarySkill - primaryMinSkill) / 100.0);
        boolean success = RNG.nextDouble() < successChance;

        // Fire your existing SkillManager hook for gain rolls
        SkillManager.trySkillGain(player, PRIMARY_SKILL_ID, success);

        if (!success) {
            player.sendSystemMessage(Component.literal("You failed to create the item and lost some metal.").withStyle(ChatFormatting.RED));
            return;
        }

        // 4. Determine quality (Exceptional)
        double exceptionalChance = Math.max(0.01, (currentPrimarySkill - primaryMinSkill) / 50.0);
        boolean isExceptional = RNG.nextDouble() < exceptionalChance;

        // If the item is marked as exceptionalOnly, fail if they didn't roll exceptional
        if (def.exceptionalOnly() && !isExceptional) {
            player.sendSystemMessage(Component.literal("You failed to craft this item with the required precision.").withStyle(ChatFormatting.RED));
            return;
        }

        // 5. Generate the crafted Item
        ItemStack craftedItem = new ItemStack(BuiltInRegistries.ITEM.get(def.resultItem()));

        if (isExceptional) {
            craftedItem.set(DataComponents.CUSTOM_NAME, Component.literal("Exceptional " + craftedItem.getHoverName().getString()).withStyle(ChatFormatting.AQUA));
            
            // Maker's Mark check
            if (currentPrimarySkill >= 100.0f) {
                ItemLore lore = new ItemLore(List.of(
                    Component.literal("Crafted by " + player.getScoreboardName()).withStyle(ChatFormatting.GRAY)
                ));
                craftedItem.set(DataComponents.LORE, lore);
            }
        }

        // Give to player or drop
        if (!player.getInventory().add(craftedItem)) {
            player.drop(craftedItem, false);
        }
        
        player.sendSystemMessage(Component.literal("You successfully crafted the item!").withStyle(ChatFormatting.GREEN));
    }
}