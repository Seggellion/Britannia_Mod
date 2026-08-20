package com.seggellion.britannia_mod.economy.crafting;

import com.seggellion.britannia_mod.item.GradeStoneItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * An ordinary furnace smelt between two quarried stones, keeping the grade the miner earned.
 *
 * <h2>Why it is not a plain smelting recipe</h2>
 * A recipe's result in JSON is a fixed stack, so a plain {@code minecraft:smelting} row could
 * declare the stone type the conversion produces but would have to invent the grade. Every stone a
 * player mines is rolled 1–5 by {@code BlockBreakUtils.generateStoneGrade}; writing a constant into
 * the recipe would flatten that roll into a number somebody chose, which is a balance decision
 * rather than a reachability fix. Worse, leaving the grade off entirely reads back as 0, and
 * {@code EntityStoneMerchant} skips any stone whose grade is not positive — the material would be
 * quietly unsellable on that path.
 *
 * <p>So the conversion changes what the stone IS and nothing else. The type comes from the
 * recipe's own result, declared in the shipped JSON; the grade is copied off the stone that went
 * in. Nothing here decides either value, and no stone-normalisation logic is duplicated: the
 * result carries its identity in data, and {@link GradeStoneItem#setGradeValue} is what stamps the
 * grade, exactly as the managed break flow does.
 *
 * <p>The type stays {@link net.minecraft.world.item.crafting.RecipeType#SMELTING}, so this is a
 * recipe an ordinary furnace finds and runs. Only the serializer is the mod's.
 */
public class GradedStoneSmeltingRecipe extends SmeltingRecipe {

    public GradedStoneSmeltingRecipe(String group, CookingBookCategory category,
                                     Ingredient ingredient, ItemStack result,
                                     float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack assembled = super.assemble(input, registries);
        ItemStack consumed = input.item();
        if (assembled.getItem() instanceof GradeStoneItem produced
                && consumed.getItem() instanceof GradeStoneItem eaten) {
            produced.setGradeValue(assembled, eaten.getGradeValue(consumed));
        }
        return assembled;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GRADED_STONE_SMELTING.get();
    }
}
