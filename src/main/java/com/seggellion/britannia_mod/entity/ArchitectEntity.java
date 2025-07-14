package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModAttributes;

import com.seggellion.britannia_mod.network.ClientboundOpenArchitectScreenPayload;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import  net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.seggellion.britannia_mod.network.RailsCatalog;
import net.minecraft.nbt.CompoundTag;
import com.seggellion.britannia_mod.network.payload.RequestCatalogC2SPayload;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.ArrayList;

import java.util.List;
public class ArchitectEntity extends PathfinderMob {
    private String cityName = "";
    private String gender = "unknown";
    private String personalName = "Unnamed";
    private final List<Product> catalog = new ArrayList<>();

    protected ArchitectEntity(EntityType<? extends PathfinderMob> t, Level lvl) {
        super(t, lvl);
        this.setPersistenceRequired(); // Ensure it doesn't despawn
    }

    public static ArchitectEntity create(EntityType<ArchitectEntity> type, Level level) {
        return new ArchitectEntity(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

  @Override
public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
    if (hand == InteractionHand.MAIN_HAND && player.level().isClientSide) {
        NetworkHandler.sendToServer(new RequestCatalogC2SPayload(this.getId()));
        return InteractionResult.SUCCESS;
    }
    return super.interactAt(player, hit, hand);
}

public List<Product> getCatalog() {
    return this.catalog;
}


    public static AttributeSupplier.Builder createAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH, 2.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.FOLLOW_RANGE, 35.0D)
            .add(Attributes.ATTACK_DAMAGE, 2.0D)
            .add(Attributes.ARMOR, 50.0D)
            .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.MAX_ABSORPTION, 0.0D)
            .add(Attributes.MOVEMENT_EFFICIENCY, 1.0D)
            .add(Attributes.BURNING_TIME, 5.0D)
            .add(Attributes.JUMP_STRENGTH, 0.2D)
            .add(Attributes.SAFE_FALL_DISTANCE, 2.0D)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
            .add(getAttributeHolder(ModAttributes.SCALE.get()), 1.0D)
            .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 1.0D)
            .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D)
            .add(NeoForgeMod.SWIM_SPEED, 1.0D).add(getAttributeHolder(BuiltInRegistries.ATTRIBUTE
    .get(ResourceLocation.fromNamespaceAndPath("neoforge", "nametag_distance"))), 64.0D);

    }

    private static Holder<Attribute> getAttributeHolder(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
            .flatMap(BuiltInRegistries.ATTRIBUTE::getHolder)
            .orElseThrow(() -> new IllegalArgumentException("Attribute not registered: " + attribute));
    }



    public void setCityName(String city) {
        this.cityName = city;
    }

    public String city() {
        return cityName;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getGender() {
        return this.gender;
    }

    public void setPersonalName(String personalName) {
        this.personalName = personalName;
this.setCustomName(Component.literal(personalName + " the Architect"));
        this.setCustomNameVisible(true);
    }

    public String getPersonalName() {
        return this.personalName;
    }

    public List<Product> catalog() {
        return catalog;
    }

public void loadCatalogFromRails(Runnable onComplete) {
    if (!level().isClientSide && !city().isBlank()) {
        RailsCatalog.fetch(city()).thenAcceptAsync(fetched -> {
            this.catalog.clear();
            this.catalog.addAll(fetched);
            if (onComplete != null) onComplete.run();
        }, ((ServerLevel) level()).getServer());
    } else {
        if (onComplete != null) onComplete.run();
    }
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

    // ----------------------------
    // NBT Save/Load
    // ----------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("cityName", cityName);
        tag.putString("gender", gender);
        tag.putString("personalName", personalName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.cityName = tag.getString("cityName");
        this.gender = tag.getString("gender");
        this.personalName = tag.getString("personalName");

        // Re-apply name for nametag display
        this.setCustomName(Component.literal(personalName));
        this.setCustomNameVisible(true);
    }
}
