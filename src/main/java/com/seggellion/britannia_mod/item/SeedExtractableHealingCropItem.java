package com.seggellion.britannia_mod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public class SeedExtractableHealingCropItem extends WeightedCommodityItem {
    private final Supplier<? extends Item> seedItem;
    private final float healthRestored;

    public SeedExtractableHealingCropItem(Properties properties, Supplier<? extends Item> seedItem, float healthRestored) {
        super(properties);
        this.seedItem = seedItem;
        this.healthRestored = healthRestored;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (CropSeedExtractor.shouldExtract(player, hand)) {
            return CropSeedExtractor.tryExtractSeed(level, player, hand, stack -> new ItemStack(seedItem.get()));
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && healthRestored > 0.0f && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(healthRestored);
        }
        return result;
    }
}
