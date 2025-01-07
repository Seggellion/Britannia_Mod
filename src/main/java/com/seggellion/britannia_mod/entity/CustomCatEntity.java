

package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;

public class CustomCatEntity extends Cat {

    public CustomCatEntity(EntityType<? extends Cat> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Add a goal to attack RatEntity
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, RatEntity.class, true));
    }
}
