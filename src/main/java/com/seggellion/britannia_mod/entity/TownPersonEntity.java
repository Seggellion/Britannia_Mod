package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TownPersonEntity extends CitizenEntity {
    private static final Logger LOGGER = LogManager.getLogger();

    private BlockPos spawnPosition;
    private static final int MAX_HOME_DISTANCE = 30;

    public TownPersonEntity(EntityType<? extends TownPersonEntity> type, Level level) {
        super(type, level);
        this.spawnPosition = this.blockPosition();
    }

    public static TownPersonEntity create(EntityType<TownPersonEntity> type, Level level) {
        return new TownPersonEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Townsperson";
    }

    @Override
    protected void registerGoals() {
        // Basic AI similar to citizens
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            player.displayClientMessage(
                Component.literal("Greetings! I'm " + getPersonalName() + " of " + getCityName() + "."),
                true
            );
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, hit, hand);
    }

    // ---------- Save / Load ----------
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("SpawnPosition", this.spawnPosition.asLong());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SpawnPosition")) {
            this.spawnPosition = BlockPos.of(tag.getLong("SpawnPosition"));
        }
    }

    public BlockPos getSpawnPosition() {
        return spawnPosition;
    }

    public void setSpawnPosition(BlockPos pos) {
        this.spawnPosition = pos;
    }

    // ---------- Display Name ----------
    @Override
    protected void updateDisplayName() {
        this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
        this.setCustomNameVisible(true);
    }

    // ---------- Attributes ----------

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    // ---------- Home Check ----------
    @Override
        public void tick() {
            super.tick();
            if (!level().isClientSide && this.spawnPosition != null) {
                double distanceSq = this.blockPosition().distSqr(spawnPosition);
                if (distanceSq > (MAX_HOME_DISTANCE * MAX_HOME_DISTANCE)) {
                    // Move back home slowly when too far away
                    this.getNavigation().moveTo(
                        spawnPosition.getX() + 0.5,
                        spawnPosition.getY(),
                        spawnPosition.getZ() + 0.5,
                        1.0D
                    );
                }
            }
        }

}
