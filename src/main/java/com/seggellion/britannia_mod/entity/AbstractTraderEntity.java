package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.ITrader;
import com.seggellion.britannia_mod.trader.TraderDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public abstract class AbstractTraderEntity extends CitizenEntity implements ITrader {
    private BlockPos spawnBlockPos;

    protected AbstractTraderEntity(EntityType<? extends AbstractTraderEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    public abstract TraderDefinition getTraderDefinition();

    @Override
    protected String getRoleTitle() {
        return getTraderRoleTitle();
    }

    @Override
    public String getTraderCityName() {
        return getCityName();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        this.restrictTo(this.blockPosition(), getTraderDefinition().spawnSettings().homeRestrictionRadius());
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return super.interactAt(player, hit, hand);
        }

        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        String city = getTraderCityName();
        if (city == null || city.isBlank()) {
            player.displayClientMessage(Component.literal("This trader is not associated with any city."), true);
            return InteractionResult.CONSUME;
        }

        openTraderCatalog(serverPlayer);
        return InteractionResult.CONSUME;
    }

    public void setSpawnBlockPos(BlockPos pos) {
        this.spawnBlockPos = pos;
    }

    public BlockPos getSpawnBlockPos() {
        if (this.spawnBlockPos != null && this.level().isInWorldBounds(this.spawnBlockPos)) {
            return this.spawnBlockPos;
        }
        return null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (spawnBlockPos != null) tag.putLong("SpawnBlockPos", spawnBlockPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SpawnBlockPos")) {
            spawnBlockPos = BlockPos.of(tag.getLong("SpawnBlockPos"));
        }
    }

    @Override
    protected void updateDisplayName() {
        this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
        this.setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && spawnBlockPos != null) {
            this.restrictTo(spawnBlockPos, getTraderDefinition().spawnSettings().homeRestrictionRadius());
        }
    }
}
