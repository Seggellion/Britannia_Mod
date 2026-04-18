package com.seggellion.britannia_mod.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.IngredientRequirement;
import com.seggellion.britannia_mod.skill.crafting.SkillRequirement;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Random;

public class BlacksmithCrafting {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RNG = new Random();
    private static final String PRIMARY_SKILL_ID = "blacksmithy"; 

    public static void processCraftRequest(ServerPlayer player, CraftableDef def) {
        
        float primaryMinSkill = 0f;
        float currentPrimarySkill = SkillManager.getSkill(player.getUUID(), PRIMARY_SKILL_ID);

        // 1. Loop through all skill requirements and ensure the player meets them
        for (SkillRequirement req : def.skillRequirements()) {
            float playerSkill = SkillManager.getSkill(player.getUUID(), req.skillKey());
            
            if (playerSkill < req.minValue()) {
                player.sendSystemMessage(Component.literal("You are not skilled enough to attempt this.").withStyle(ChatFormatting.RED));
                return;
            }
            
            if (req.skillKey().equals(PRIMARY_SKILL_ID)) {
                primaryMinSkill = req.minValue();
            }
        }

        // 2. Handle Ingredients & Determine Material dynamically
        ItemStack offHand = player.getOffhandItem();
        
        // Dynamically grab the material based on the held ingot
        UOMetalToolMaterial craftMaterial = UOMetalToolMaterial.getMaterialByIngot(offHand.getItem());
        
        // Safety check: if they somehow swapped items before the packet arrived
        if (craftMaterial == null) {
            player.sendSystemMessage(Component.literal("You must hold a valid forging metal in your offhand.").withStyle(ChatFormatting.RED));
            return;
        }
        
        if (!def.ingredients().isEmpty()) {
            IngredientRequirement primaryIng = def.ingredients().get(0);
            
            if (offHand.getCount() < primaryIng.amount()) {
                player.sendSystemMessage(Component.literal("You do not have enough materials.").withStyle(ChatFormatting.RED));
                return;
            }
            // Consume the items
            offHand.shrink(primaryIng.amount());
        }

        // 3. UO Logic: Success chance scales from 50% at minSkill up to 100% at minSkill + 50.0
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

        if (def.exceptionalOnly() && !isExceptional) {
            player.sendSystemMessage(Component.literal("You failed to craft this item with the required precision.").withStyle(ChatFormatting.RED));
            return;
        }

        int quality = isExceptional ? 2 : 1;

        // 5. Generate the crafted Item
        Item baseItem = BuiltInRegistries.ITEM.get(def.resultItem());

        if (baseItem == Items.AIR) {
            LOGGER.error("CRITICAL CRAFTING ERROR: Tried to forge '{}' but the item does not exist in the game registry!", def.resultItem());
            player.sendSystemMessage(Component.literal("Crafting failed: Item registry mismatch. Check server console.").withStyle(ChatFormatting.DARK_RED));
            return;
        }

        // Build the ItemStack using your weapon factory and the correctly identified material!
        ItemStack craftedItem = WeaponRegistry.createWeapon(baseItem, craftMaterial, quality);

        // Apply Exceptional Name & Maker's Mark
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

        // 6. Give to player safely
        ItemHandlerHelper.giveItemToPlayer(player, craftedItem);
        
        player.sendSystemMessage(Component.literal("You successfully crafted the item!").withStyle(ChatFormatting.GREEN));
    }
}