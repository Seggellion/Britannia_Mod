package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;

import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.SpawnGroupData;
import javax.annotation.Nullable;

public class AlcoholTraderEntity extends CitizenEntity {

    public AlcoholTraderEntity(EntityType<? extends AlcoholTraderEntity> type, Level level) {
        super(type, level);
    }

    public static AlcoholTraderEntity create(EntityType<AlcoholTraderEntity> type, Level level) {
        return new AlcoholTraderEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Alcohol Trader";
    }

    @Override
    protected void registerGoals() {
        // Priority 1: Don't drown
        this.goalSelector.addGoal(1, new FloatGoal(this));
        
        // Priority 2: If we are pushed away from our 'Restriction' (Home), walk back effectively
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0D));

        // Priority 3: Look at customers
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        
        // Note: We still omit RandomStrollGoal so they don't wander voluntarily
    }

@Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, 
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        // When the entity spawns, set the "Home" position to the current block
        // The '1' is the radius they are allowed to exist in before the AI thinks "I'm too far"
        this.restrictTo(this.blockPosition(), 1); 
        
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            if (player instanceof ServerPlayer sp) {
                // This triggers the GUI opening on the client side.
                // The "Alcohol Trader" string is crucial: it tells the GUI 
                // to hit the Rails endpoint for alcohol prices.
                ClientboundOpenNpcScreenPayload.send(
                    sp,
                    NpcType.TRADER,              // TRADER mode = Player Sells to NPC
                    "Alcohol Trader",            // Role ID for Rails API catalog lookup
                    this.getCityName(),          // City Context
                    this.getId()                 // Entity ID for transaction verification
                );
            }
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, hit, hand);
    }

    // ---------- Attributes ----------
    public static AttributeSupplier.Builder createAttributes() {
        // Re-uses the base Citizen attributes
        return CitizenEntity.baseAttributes();
    }

    // ---------- Display Name ----------
    @Override
    protected void updateDisplayName() {
        // Renders as "John the Alcohol Trader"
        this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
        this.setCustomNameVisible(true);
    }
}