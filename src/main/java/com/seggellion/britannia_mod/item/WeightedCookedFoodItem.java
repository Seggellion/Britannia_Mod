package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class WeightedCookedFoodItem extends WeightedCommodityItem {
    public static final int MIN_RESTORE = 1;
    public static final int MAX_RESTORE = 20;
    public static final double CHICKEN_MULTIPLIER = 4.0D;
    public static final double BIRD_MULTIPLIER = 4.0D;
    public static final double RABBIT_MULTIPLIER = 3.5D;
    public static final double FISH_MULTIPLIER = 5.0D;
    public static final double PORK_MULTIPLIER = 5.0D;
    public static final double LAMB_MULTIPLIER = 5.5D;
    public static final double VENISON_MULTIPLIER = 6.0D;
    public static final double BEEF_MULTIPLIER = 7.0D;
    public static final double MYTHIC_FISH_MULTIPLIER = 7.0D;
    public static final double GRAIN_MULTIPLIER = 3.0D;
    public static final double PRODUCE_MULTIPLIER = 2.5D;
    public static final double GENERIC_MULTIPLIER = 3.0D;

    private final String foodType;
    private final double restoreMultiplier;

    public WeightedCookedFoodItem(Properties properties, String foodType, double restoreMultiplier) {
        super(properties);
        this.foodType = foodType;
        this.restoreMultiplier = restoreMultiplier;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        int restore = restoreAmount(stack);
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            int bonus = Math.max(0, restore - 1);
            if (bonus > 0) {
                player.getFoodData().eat(bonus, 0.55F);
            }
        }
        return result;
    }

    public int restoreAmount(ItemStack stack) {
        int calculated = (int) Math.round(getWeight(stack) * restoreMultiplier);
        return Math.max(MIN_RESTORE, Math.min(MAX_RESTORE, calculated));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!CropQualityCalculator.hasCropQuality(stack)) {
            tooltip.add(Component.literal("Restores: " + restoreAmount(stack)));
        }
    }

    public String foodType() {
        return foodType;
    }
}
