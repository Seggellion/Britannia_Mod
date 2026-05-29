package com.seggellion.britannia_mod.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class SerpentSeaEntity extends BaseBritanniaMonster {

    // 1. Declare the new segments here
    public final SerpentPartEntity head;
    public final SerpentPartEntity body1;
    public final SerpentPartEntity body2;
    public final SerpentPartEntity body3;
    public final SerpentPartEntity body4;
    public final SerpentPartEntity tail;
    public final SerpentPartEntity[] subEntities;

    public SerpentSeaEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "serpent_sea");
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);

        // 2. Initialize the new parts. 
        this.head = new SerpentPartEntity(this, "head", 0.8F, 0.6F);
        this.body1 = new SerpentPartEntity(this, "body1", 0.6F, 0.6F);
        this.body2 = new SerpentPartEntity(this, "body2", 0.6F, 0.6F);
        this.body3 = new SerpentPartEntity(this, "body3", 0.6F, 0.6F);
        this.body4 = new SerpentPartEntity(this, "body4", 0.6F, 0.6F);
        this.tail = new SerpentPartEntity(this, "tail", 0.4F, 0.4F);
        
        // 3. Add them to the master array so the game registers them
        this.subEntities = new SerpentPartEntity[]{this.head, this.body1, this.body2, this.body3, this.body4, this.tail};
    }

    // --- MULTIPART LOGIC START ---

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return this.subEntities;
    }

    public boolean hurtParts(SerpentPartEntity part, DamageSource source, float amount) {
        return this.hurt(source, amount);
    }

@Override
    public void aiStep() {
        super.aiStep();
        
        // Now using the shared method from BaseBritanniaMonster!
        this.positionMultipart(this.head, -2.4F, 0.0F);   
        this.positionMultipart(this.body1, -1.2F, 0.0F); 
        this.positionMultipart(this.body2, 0.0F, 0.0F);   
        this.positionMultipart(this.body3, 1.2F, 0.0F);   
        this.positionMultipart(this.body4, 2.4F, 0.0F);
        this.positionMultipart(this.tail, 3.6F, 0.0F);

        // Temporary Debugging: Spawn particles so you can SEE the invisible hitboxes
        if (this.level().isClientSide) {
            for (SerpentPartEntity part : this.subEntities) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, part.getX(), part.getY() + 0.5, part.getZ(), 0, 0, 0);
            }
        }
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.subEntities != null) {
            for (int i = 0; i < this.subEntities.length; i++) {
                this.subEntities[i].setId(id + i + 1);
            }
        }
    }

    // --- MULTIPART LOGIC END ---

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_SPEED, -1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(8, new RandomSwimmingGoal(this, 1.0D, 10)); 
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (this.swinging) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("animation.model.attack"));
            return PlayState.CONTINUE;
        } else if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.swim"));
            return PlayState.CONTINUE;
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.idle"));
            return PlayState.CONTINUE;
        }
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
    }
}