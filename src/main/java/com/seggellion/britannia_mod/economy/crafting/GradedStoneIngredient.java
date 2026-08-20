package com.seggellion.britannia_mod.economy.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.economy.CityCommodity;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * A recipe ingredient that means one particular quarried stone, not "any stone".
 *
 * <h2>Why this exists</h2>
 * Every rock a player mines is the same item. {@code britannia_mod:grade_stone_item} carries which
 * stone it is in its data, which is what lets one item stand for the whole quarry and what lets
 * the economy price Limestone differently from Cobblestone. A vanilla recipe ingredient matches on
 * item id, so a smelting recipe written against that item would accept every rock in the game —
 * and "generic stone becomes plaster" is precisely the substitution the economy refused when it
 * insisted plaster carry its own commodity key.
 *
 * <p>So the ingredient reads the stone type. It is one class and one registered type; the recipe
 * itself stays an ordinary {@code minecraft:smelting} JSON that any furnace can run, rather than a
 * new workstation or a bespoke recipe serializer.
 *
 * <p>Matching goes through {@link CityCommodity#normalize}, the same normaliser the sale path uses,
 * so the recipe agrees with the economy about what the stone in the player's hand is called: the
 * item says {@code "Limestone"}, the commodity says {@code limestone}, and both resolve here.
 */
public record GradedStoneIngredient(String stoneType) implements ICustomIngredient {

    public static final MapCodec<GradedStoneIngredient> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    com.mojang.serialization.Codec.STRING
                            .fieldOf("stone_type")
                            .forGetter(GradedStoneIngredient::stoneType)
            ).apply(instance, GradedStoneIngredient::new));

    public GradedStoneIngredient {
        stoneType = CityCommodity.normalize(stoneType);
        if (stoneType.isBlank()) {
            throw new IllegalArgumentException("a graded stone ingredient must name a stone");
        }
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof GradeStoneItem graded)) return false;
        return stoneType.equals(CityCommodity.normalize(graded.getStoneType(stack)));
    }

    /**
     * One example stack, stamped with the stone this ingredient wants.
     *
     * <p>Used for the recipe book and for JEI-style listings. It has to carry the stone type or
     * the displayed example would be a rock that does not actually satisfy the recipe.
     */
    @Override
    public Stream<ItemStack> getItems() {
        ItemStack example = new ItemStack(ItemRegistry.GRADE_STONE_ITEM.get());
        ((GradeStoneItem) example.getItem()).setStoneType(example, stoneType);
        return Stream.of(example);
    }

    /** False: what matches depends on the stack's data, not only on its item. */
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModIngredients.GRADED_STONE.get();
    }

    /** Never used by the ingredient itself; kept so a debug print names the item, not the class. */
    public String itemId() {
        return BuiltInRegistries.ITEM.getKey(ItemRegistry.GRADE_STONE_ITEM.get()).toString();
    }
}
