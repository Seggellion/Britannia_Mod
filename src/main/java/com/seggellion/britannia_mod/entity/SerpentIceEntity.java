package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class SerpentIceEntity extends BaseBritanniaMonster {

    // 1. Declare the segments
    public final SerpentPartEntity head;
    public final SerpentPartEntity body1;
    public final SerpentPartEntity body2;
    public final SerpentPartEntity body3;
    public final SerpentPartEntity body4;
    public final SerpentPartEntity body5;
    public final SerpentPartEntity tail;
    public final SerpentPartEntity[] subEntities;

    public SerpentIceEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "serpent_giant_ice");

        // 2. Initialize the parts keeping the exact sizes from the Silver Serpent
        this.head = new SerpentPartEntity(this, "head", 0.8F, 0.6F);
        this.body1 = new SerpentPartEntity(this, "body1", 0.6F, 0.6F);
        this.body2 = new SerpentPartEntity(this, "body2", 0.6F, 0.6F);
        this.body3 = new SerpentPartEntity(this, "body3", 0.6F, 0.6F);
        this.body4 = new SerpentPartEntity(this, "body4", 0.6F, 0.6F);
        this.body5 = new SerpentPartEntity(this, "body5", 0.6F, 0.6F);
        this.tail = new SerpentPartEntity(this, "tail", 0.6F, 0.6F);
        
        this.subEntities = new SerpentPartEntity[]{this.head, this.body1, this.body2, this.body3, this.body4, this.body5, this.tail};
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
        this.positionMultipart(this.head, 0.5F, 0.0F);   
        this.positionMultipart(this.body1, -0.5F, 0.0F); 
        this.positionMultipart(this.body2, -1.4F, 0.0F);   
        this.positionMultipart(this.body3, -2.6F, 0.0F);   
        this.positionMultipart(this.body4, -3.8F, 0.0F);
         this.positionMultipart(this.body5, -5.0F, 0.0F);
        this.positionMultipart(this.tail, -6.8F, 0.0F);
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

    // Attributes (Preserved original Ice Serpent stats)
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_SPEED, -2.0D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        // This pulls in FloatGoal, RandomStrollGoal, and RandomLookAroundGoal
        super.registerGoals(); 
        
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