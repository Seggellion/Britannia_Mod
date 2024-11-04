package com.seggellion.britannia_mod.magic;

import net.minecraft.world.entity.LivingEntity;

public interface Caster {
    void consumeMana(int amount);
    int getMana();
    LivingEntity asLivingEntity();
}
