package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

public class SerpentLavaEntity extends BaseBritanniaMonster {

    // 1. Declare the segments
    public final SerpentPartEntity head;
    public final SerpentPartEntity body1;
    public final SerpentPartEntity body2;
    public final SerpentPartEntity body3;
    public final SerpentPartEntity body4;
    public final SerpentPartEntity tail;
    public final SerpentPartEntity[] subEntities;

    public SerpentLavaEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "serpent_lava");

        // 2. Initialize the parts
        this.head = new SerpentPartEntity(this, "head", 0.8F, 0.6F);
        this.body1 = new SerpentPartEntity(this, "body1", 0.6F, 0.6F);
        this.body2 = new SerpentPartEntity(this, "body2", 0.6F, 0.6F);
        this.body3 = new SerpentPartEntity(this, "body3", 0.6F, 0.6F);
        this.body4 = new SerpentPartEntity(this, "body4", 0.6F, 0.6F);
        this.tail = new SerpentPartEntity(this, "tail", 0.4F, 0.4F);
        
        this.subEntities = new SerpentPartEntity[]{this.head, this.body1, this.body2, this.body3, this.body4, this.tail};

        // --- LAVA PATHFINDING SETUP ---
        // Remove hesitation for entering lava and fire
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        
        // 3D movement control (works in lava due to our isInWater override below)
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 1.0F, 1.0F, true);
    }

    // --- NAVIGATION & MOVEMENT OVERRIDES ---

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /**
     * THE LAVA SWIMMING TRICK:
     * Vanilla's AmphibiousPathNavigation and RandomSwimmingGoal strictly check for WATER.
     * By telling the game the entity "is in water" when it's actually in lava, 
     * we get full 3D swimming AI for free without rewriting complex node evaluators.
     */
    @Override
    public boolean isInWater() {
        return super.isInWater() || this.isInLava();
    }

    // --- FIRE IMMUNITY OVERRIDES ---

    @Override
    public boolean fireImmune() {
        // You should also ideally add .fireImmune() to your EntityType.Builder in your registry,
        // but this ensures the entity logic fundamentally rejects fire damage.
        return true;
    }

    @Override
    public boolean isOnFire() {
        // Returning false completely prevents the visual fire overlay from rendering on the entity
        return false;
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
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

    @Override
    public void aiStep() {
        super.aiStep();
        
        // Using negative numbers flips the direction 180 degrees!
        this.positionMultipart(this.head, 1.0F, 0.0F);   
        this.positionMultipart(this.body1, -0.2F, 0.0F); 
        this.positionMultipart(this.body2, -1.4F, 0.0F);   
        this.positionMultipart(this.body3, -2.6F, 0.0F);   
        this.positionMultipart(this.body4, -3.8F, 0.0F);
        this.positionMultipart(this.tail, -4.0F, 0.0F);

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

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 54.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_SPEED, -1.0D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {

        // Add standard 3D movement goals
        this.goalSelector.addGoal(7, new RandomSwimmingGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }




    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}