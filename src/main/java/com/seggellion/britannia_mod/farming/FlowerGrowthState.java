package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;

public record FlowerGrowthState(float progress, int tickProgress, boolean blocked) {
    public FlowerGrowthState {
        if (!Float.isFinite(progress) || progress < 0.0f || progress > 1.0f) {
            throw new IllegalArgumentException("Flower growth progress must be in 0..1: " + progress);
        }
        if (tickProgress < 0) {
            throw new IllegalArgumentException("Flower growth tick progress cannot be negative: " + tickProgress);
        }
    }

    public static FlowerGrowthState newlyPlanted() {
        return new FlowerGrowthState(0.0f, 0, false);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Progress", progress);
        tag.putInt("TickProgress", tickProgress);
        tag.putBoolean("Blocked", blocked);
        return tag;
    }

    public static FlowerGrowthState fromTag(CompoundTag tag) {
        return new FlowerGrowthState(tag.getFloat("Progress"), tag.getInt("TickProgress"), tag.getBoolean("Blocked"));
    }
}
