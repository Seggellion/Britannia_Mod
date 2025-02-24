package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.entity.ai.goal.RestrictedStrollGoal;
import com.seggellion.britannia_mod.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import  net.neoforged.neoforge.common.NeoForgeMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A simple "townsperson" NPC who:
 *  - Walks randomly within 30 blocks of its home (spawnPosition) using RestrictedStrollGoal
 *  - Looks at players, looks around, and can greet them upon interaction
 *  
 * If you want this NPC to sleep, you'll need a custom behavior or a Brain-based approach
 * (currently no built-in SleepInBedGoal in official docs).
 */
public class TownPersonEntity extends AbstractVillager  implements ICityEntity {
    private static final Logger LOGGER = LogManager.getLogger();

    private String cityName = "";
    private BlockPos spawnPosition;    
    private final int maxHomeDistance = 30;

    public TownPersonEntity(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired(); // prevents despawning
        this.spawnPosition = this.blockPosition(); // default home at spawn
    }

    @Override
    protected void registerGoals() {
    this.goalSelector.addGoal(1, new StrollThroughVillageGoal(this, 10));

        // Idle behavior
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CityName", this.cityName);
        tag.putLong("SpawnPosition", this.spawnPosition.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.cityName = tag.getString("CityName");
        this.spawnPosition = BlockPos.of(tag.getLong("SpawnPosition"));
    }

    public String getCityName() {
        return this.cityName;
    }

    public void setCityName(String name) {
        this.cityName = name;
    }

    public BlockPos getSpawnPosition() {
        return spawnPosition;
    }

    public void setSpawnPosition(BlockPos pos) {
        this.spawnPosition = pos;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            // Greet the player
            player.displayClientMessage(
                Component.literal("Hello! I'm a simple townsperson of " + cityName + "!"),
                true
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void updateTrades() {
        // TownPerson has no trades
    }

    @Override
    protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer offer) {
        // No XP to reward
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        // This NPC doesn't breed
        return null;
    }

    /**
     * Basic attributes for a non-combat, low-health NPC
     */
    public static AttributeSupplier.Builder createAttributes() {
        return AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 50.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MAX_ABSORPTION, 0.0D)
                .add(Attributes.MOVEMENT_EFFICIENCY, 1.0D)
                .add(Attributes.BURNING_TIME, 5.0D)
                .add(Attributes.JUMP_STRENGTH, 1.0D)
                .add(Attributes.SAFE_FALL_DISTANCE, 2.0D)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
                .add(NeoForgeMod.SWIM_SPEED, 1.0D)
                .add(getAttributeHolder(ModAttributes.SCALE.get()), 1.0D)
                .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 0.08D)
                .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D);
    }

    private static Holder<Attribute> getAttributeHolder(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
            .flatMap(BuiltInRegistries.ATTRIBUTE::getHolder)
            .orElseThrow(() -> new IllegalArgumentException("Attribute not registered: " + attribute));
    }

    @Override
    public float getScale() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.SCALE.get()));
        return instance != null ? (float) instance.getValue() : 1.0F;
    }

    @Override
    public double getDefaultGravity() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.GRAVITY.get()));
        return instance != null ? instance.getValue() : 0.08D;
    }

    @Override
    public float maxUpStep() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()));
        return instance != null ? (float) instance.getValue() : super.maxUpStep();
    }
}
