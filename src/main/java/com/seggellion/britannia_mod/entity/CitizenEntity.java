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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;

// clothing
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import javax.annotation.Nullable;

// Attributes
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// Registries
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.ModSounds;

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
    private String gender = "unknown";
    private boolean stepToggle = false;

private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);

    protected String getRoleTitle() {
        return "Citizen";
    }

    protected final List<Product> catalog = new ArrayList<>();

    protected CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

// Define both sets of animations
    private static final RawAnimation IDLE_FEMALE = RawAnimation.begin().thenLoop("animation.idle_female");
    private static final RawAnimation WALK_FEMALE = RawAnimation.begin().thenLoop("animation.walk_female");
    
    // Ensure these match the exact names of the animations inside your Blockbench file
    private static final RawAnimation IDLE_MALE = RawAnimation.begin().thenLoop("animation.idle_male");
    private static final RawAnimation WALK_MALE = RawAnimation.begin().thenLoop("animation.walk_male");

    private static final EntityDataAccessor<String> DATA_GENDER =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);


private static final EntityDataAccessor<String> DATA_PERSONAL_NAME =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<String> DATA_CITY_NAME =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);


    private static final EntityDataAccessor<Boolean> DATA_BLINK =
        SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);

    private int blinkTicks = 0; // local timer for how long we keep eyes closed


// ---------- EntityData Accessors for Clothing ----------
private static final EntityDataAccessor<Integer> DATA_HAIR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_FACIAL_HAIR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_SHIRT = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_CHEST = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_PANTS = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_SHOES = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
private static final EntityDataAccessor<Integer> DATA_CAPE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

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
            .add(Attributes.MAX_HEALTH, 5.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.2D)
            .add(Attributes.FOLLOW_RANGE, 35.0D)
            .add(Attributes.ATTACK_DAMAGE, 2.0D)
            .add(Attributes.ARMOR, 0.0D)
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
        builder.define(DATA_PERSONAL_NAME, "Unnamed");
        builder.define(DATA_CITY_NAME, "");

    // Default indices (1-3)
    builder.define(DATA_HAIR, 1);
    builder.define(DATA_FACIAL_HAIR, 1);
    builder.define(DATA_SHIRT, 1);
    builder.define(DATA_CHEST, 1);
    builder.define(DATA_PANTS, 1);
    builder.define(DATA_SHOES, 1);
    builder.define(DATA_CAPE, 1);

    }

public int getClothingIndex(String slot) {
    return switch (slot) {
        case "hair" -> this.entityData.get(DATA_HAIR);
        case "facial_hair" -> this.entityData.get(DATA_FACIAL_HAIR);
        case "shirt" -> this.entityData.get(DATA_SHIRT);
        case "chest" -> this.entityData.get(DATA_CHEST);
        case "pants" -> this.entityData.get(DATA_PANTS);
        case "shoes" -> this.entityData.get(DATA_SHOES);
        case "cape" -> this.entityData.get(DATA_CAPE);
        default -> 1;
    };
}

// ---------- Randomize on Spawn ----------
@Nullable
@Override
public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
    // Randomize gender
    this.setGender(this.random.nextBoolean() ? "male" : "female");

    // Randomize clothing (1, 2, or 3)
    this.entityData.set(DATA_HAIR, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_FACIAL_HAIR, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_SHIRT, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_CHEST, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_PANTS, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_SHOES, this.random.nextInt(3) + 1);
    this.entityData.set(DATA_CAPE, this.random.nextInt(3) + 1);

    return super.finalizeSpawn(level, difficulty, reason, spawnData);
}

    // ---------- City / Gender / Name ----------

public void setGender(String gender) { 
        this.entityData.set(DATA_GENDER, gender); 
    }
    
    public String getGender() { 
        return this.entityData.get(DATA_GENDER); 
    }

// ---------- City / Gender / Name ----------
    public void setCityName(String city) { 
        this.entityData.set(DATA_CITY_NAME, city); 
    }
    
    public String getCityName() { 
        return this.entityData.get(DATA_CITY_NAME); 
    }
    
    public String city() { 
        return getCityName(); 
    }

    public void setPersonalName(String personalName) {
        this.entityData.set(DATA_PERSONAL_NAME, personalName);
        this.updateDisplayName(); 
    }

    public String getPersonalName() { 
        return this.entityData.get(DATA_PERSONAL_NAME); 
    }

protected void updateDisplayName() {
        // Fetch the name from the SynchedEntityData via our getter
        Component styledName = Component.literal(this.getPersonalName()).withStyle(UO_STYLE);
        this.setCustomName(styledName);
        this.setCustomNameVisible(true);
    }



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
        tag.putString("cityName", this.getCityName());
        tag.putString("gender", this.getGender());
        tag.putString("personalName", this.getPersonalName());

    tag.putInt("hairIndex", this.entityData.get(DATA_HAIR));
    tag.putInt("facialHairIndex", this.entityData.get(DATA_FACIAL_HAIR));
    tag.putInt("shirtIndex", this.entityData.get(DATA_SHIRT));
    tag.putInt("chestIndex", this.entityData.get(DATA_CHEST));
    tag.putInt("pantsIndex", this.entityData.get(DATA_PANTS));
    tag.putInt("shoesIndex", this.entityData.get(DATA_SHOES));
    tag.putInt("capeIndex", this.entityData.get(DATA_CAPE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        
        // Use the setters so the entityData is populated correctly upon loading
        this.setCityName(tag.getString("cityName"));
        this.setGender(tag.getString("gender"));
        
        // This setter automatically calls updateDisplayName() for us!
        this.setPersonalName(tag.getString("personalName")); 

        if (tag.contains("hairIndex")) {
        this.entityData.set(DATA_HAIR, tag.getInt("hairIndex"));
        this.entityData.set(DATA_FACIAL_HAIR, tag.getInt("facialHairIndex"));
        this.entityData.set(DATA_SHIRT, tag.getInt("shirtIndex"));
        this.entityData.set(DATA_CHEST, tag.getInt("chestIndex"));
        this.entityData.set(DATA_PANTS, tag.getInt("pantsIndex"));
        this.entityData.set(DATA_SHOES, tag.getInt("shoesIndex"));
        this.entityData.set(DATA_CAPE, tag.getInt("capeIndex"));
    }
    }
    
    @Override
    public void tick() {
        super.tick();

        // Only handle the random logic on the server so all clients see the blink at the same time
        if (!this.level().isClientSide) {
            if (this.isBlinking()) {
                this.blinkTicks++;
                // Keep eyes closed for 3 ticks (a very fast, natural human blink)
                if (this.blinkTicks >= 3) {
                    this.setBlinking(false);
                    this.blinkTicks = 0;
                }
            } else {
                // Random chance to blink while eyes are open. 
                // nextInt(80) means they will average about one blink every 4 seconds.
                if (this.random.nextInt(80) == 0) {
                    this.setBlinking(true);
                    this.blinkTicks = 0;
                }
            }
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Access the SoundEvent directly from your DeferredHolders using .get()
        SoundEvent stepSound = this.stepToggle
                ? ModSounds.FEET12A.get()
                : ModSounds.FEET12B.get();

        this.stepToggle = !this.stepToggle;
        this.playSound(stepSound, 0.15f, 1.0f);
    }

    // Implement the required GeckoLib methods
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

private PlayState animationPredicate(AnimationState<CitizenEntity> state) {
        // Check the synced gender data
        boolean isMale = "male".equals(this.getGender());

        // Determine animation based on movement AND gender
        if (state.isMoving()) {
            state.setAnimation(isMale ? WALK_MALE : WALK_FEMALE);
        } else {
            state.setAnimation(isMale ? IDLE_MALE : IDLE_FEMALE);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
}
