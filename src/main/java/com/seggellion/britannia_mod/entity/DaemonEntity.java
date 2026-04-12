package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.magic.Caster;
import com.seggellion.britannia_mod.magic.MagicArrowSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DaemonEntity extends BaseBritanniaMonster implements Caster {

    // Mana-related attributes
    private int mana = 10;
    private final int maxMana = 10;
    private int manaCooldown = 0;
    private static final int MANA_REGEN_COOLDOWN = 20;  // 1 second in ticks
    private static final int MAGIC_ARROW_COST = 4;

    public DaemonEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "daemon");
    }

    // Attributes restored to boss-level
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        // This pulls in FloatGoal, RandomStrollGoal, and RandomLookAroundGoal
        super.registerGoals(); 
        
        // Re-added the magic casting goal!
        this.goalSelector.addGoal(1, new DaemonCastMagicGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

    // =========================================
    // CASTER INTERFACE IMPLEMENTATION
    // =========================================

    @Override
    public void tick() {
        super.tick();

        if (manaCooldown > 0) {
            manaCooldown--;
        } else if (mana < maxMana) {
            mana++;
            manaCooldown = MANA_REGEN_COOLDOWN; // Reset cooldown after regen
        }
    }
    
    @Override
    public LivingEntity asLivingEntity() {
        return this;
    }

    public boolean canCastMagicArrow() {
        return this.getMana() >= MAGIC_ARROW_COST;
    }

    public void castMagicArrow(LivingEntity target) {
        if (canCastMagicArrow()) {
            consumeMana(MAGIC_ARROW_COST);
            MagicArrowSpell magicArrowSpell = new MagicArrowSpell();
            magicArrowSpell.applyEffect(this, target);
        }
    }

    @Override
    public void consumeMana(int amount) {
        this.mana = Math.max(0, this.mana - amount);
    }

    public int getMana() {
        return mana;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}