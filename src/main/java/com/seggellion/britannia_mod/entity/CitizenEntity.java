package com.seggellion.britannia_mod.entity;


// Minecraft & NeoForge core
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

// Attributes
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// Registries
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

// Networking & NBT
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;


// Custom + Mod-specific
import net.neoforged.neoforge.common.NeoForgeMod;
import com.seggellion.britannia_mod.ModAttributes;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.RailsCatalog;

// Geckolib
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.AnimationController;

// Java utils
import java.util.ArrayList;
import java.util.List;

public abstract class CitizenEntity extends PathfinderMob implements GeoAnimatable  {
    private String cityName = "";
    private String gender = "unknown";
    private String personalName = "Unnamed";

    protected String getRoleTitle() {
        return "Citizen";
    }

    public String getCityName() {
        return cityName;
    }

    protected final List<Product> catalog = new ArrayList<>();

    protected CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.idle_female");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.walk_female");

    private static final EntityDataAccessor<String> DATA_GENDER =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);


    private static final EntityDataAccessor<Boolean> DATA_BLINK =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);

    private int blinkTicks = 0; // local timer for how long we keep eyes closed

    @Override
    public boolean shouldBeSaved() {
        // Prevent traders or temporary NPCs from being saved between sessions
        return !(this instanceof FishTraderEntity
            || this instanceof SalvageTraderEntity
            || this instanceof MeatTraderEntity
            || this instanceof AlcoholTraderEntity);
    }


    // ---------- Goals ----------
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    // ---------- Attributes ----------
    public static AttributeSupplier.Builder baseAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.2D)
            .add(Attributes.FOLLOW_RANGE, 35.0D)
            .add(Attributes.ATTACK_DAMAGE, 2.0D)
            .add(Attributes.ARMOR, 10.0D)
             .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
            .add(Attributes.MOVEMENT_EFFICIENCY, 1.0D)
            .add(Attributes.JUMP_STRENGTH, 0.1D)
            .add(Attributes.SAFE_FALL_DISTANCE, 2.0D)
            .add(Attributes.MAX_ABSORPTION, 0.0D)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
            // Your custom attributes (require Attribute → Holder)
            .add(getAttributeHolder(ModAttributes.SCALE.get()), 1.0D)
            .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 0.08D)
            .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D)

            // Built-in NeoForge attributes (use directly, they are already holders)
            .add(NeoForgeMod.SWIM_SPEED, 1.0D)
            .add(NeoForgeMod.NAMETAG_DISTANCE, 64.0D);
    }

    private static Holder<Attribute> getAttributeHolder(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
            .flatMap(BuiltInRegistries.ATTRIBUTE::getHolder)
            .orElseThrow(() -> new IllegalArgumentException("Attribute not registered: " + attribute));
    }

   @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GENDER, "female"); // default -> female
        builder.define(DATA_BLINK, false);
    }

    // ---------- City / Gender / Name ----------
    public void setCityName(String city) { this.cityName = city; }
    public String city() { return cityName; }

    public void setGender(String gender) { this.gender = gender; }
    public String getGender() { return this.gender; }

    public void setPersonalName(String personalName) {
        this.personalName = personalName;
        this.updateDisplayName(); // Sets visible name based on subclass role
    }

    protected void updateDisplayName() {
        // Default behavior: just show personal name
        this.setCustomName(Component.literal(this.personalName));
        this.setCustomNameVisible(true);
    }

    public String getPersonalName() { return personalName; }


        public boolean isBlinking() {
            return this.entityData.get(DATA_BLINK);
        }

        private void setBlinking(boolean blinking) {
            this.entityData.set(DATA_BLINK, blinking);
        }

        @Override
        public double getTick(Object object) {
            // Return the entity's age in ticks for animation purposes
            return this.tickCount;
        }



    // ---------- Catalog ----------
    public List<Product> getCatalog() { return catalog; }

    public void loadCatalogFromRails(Runnable onComplete) {
        if (!level().isClientSide && !city().isBlank()) {
            RailsCatalog.fetch(city()).thenAcceptAsync(fetched -> {
                catalog.clear();
                catalog.addAll(fetched);
                if (onComplete != null) onComplete.run();
            }, ((ServerLevel) level()).getServer());
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    // ---------- Attribute overrides ----------
    @Override
    public float getScale() {
        AttributeInstance inst = this.getAttribute(getAttributeHolder(ModAttributes.SCALE.get()));
        return inst != null ? (float) inst.getValue() : 1.0F;
    }
    @Override
    public double getDefaultGravity() {
        AttributeInstance inst = this.getAttribute(getAttributeHolder(ModAttributes.GRAVITY.get()));
        return inst != null ? inst.getValue() : 0.08D;
    }
    @Override
    public float maxUpStep() {
        AttributeInstance inst = this.getAttribute(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()));
        return inst != null ? (float) inst.getValue() : super.maxUpStep();
    }

    // ---------- Save / Load ----------
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
        // this.setCustomName(Component.literal(personalName));
        // this.setCustomNameVisible(true);
        this.updateDisplayName();
    }

    // Implement the required GeckoLib methods
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<CitizenEntity> state) {
        // Determine animation based on movement
        if (state.isMoving()) {
            state.setAnimation(WALK);
        } else {
            state.setAnimation(IDLE);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
}
