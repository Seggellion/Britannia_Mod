package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.ai.goal.RestrictedStrollGoal;
import net.minecraft.server.level.ServerPlayer; 
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.market.MarketManager;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.util.SendTransactionToAPI;
import com.seggellion.britannia_mod.BritanniaMod;

import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.ModAttributes;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.JsonObject;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.AnimationController;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EntityFishMerchant extends AbstractVillager implements IEntityExtension, ICityEntity, GeoEntity   {
    private String cityName;
    private BlockPos spawnPosition;
    private int maxHomeDistance = 5; // Set to match the spawner's radius
    private static final Logger LOGGER = LogManager.getLogger();
    private static final double MESSAGE_RADIUS = 20.0;

    private static final EntityDataAccessor<String> DATA_GENDER =
        SynchedEntityData.defineId(EntityFishMerchant.class, EntityDataSerializers.STRING);

    private String gender = "female";
    private String personalName = "Unnamed";
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE  = RawAnimation.begin().thenLoop("animation.fish_merchant.idle");
    private static final RawAnimation WALK  = RawAnimation.begin().thenLoop("animation.fish_merchant.walk");

    private static final EntityDataAccessor<Boolean> DATA_BLINK =
        SynchedEntityData.defineId(EntityFishMerchant.class, EntityDataSerializers.BOOLEAN);

    private int blinkTicks = 0; // local timer for how long we keep eyes closed


    public EntityFishMerchant(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.cityName = ""; 
        this.spawnPosition = this.blockPosition();
    }



 // Getter/Setter now read/write the synced field
    public void setGender(String gender) {
        String g = normalizeGender(gender);
        this.gender = g;
        this.entityData.set(DATA_GENDER, g);
    }
    

    public String getGender() {
        // read from synced data; fallback to local/“female”
        String g = this.entityData.get(DATA_GENDER);
        if (g == null || g.isEmpty()) g = "female";
        return g;
    }

    private static String normalizeGender(String in) {
        if (in == null) return "female";
        String s = in.trim().toLowerCase();
        return (s.equals("male") || s.equals("m")) ? "male" : "female"; // default → female
    }


   @Override
protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_GENDER, "female"); // default -> female
    builder.define(DATA_BLINK, false);
}
    public void setPersonalName(String personalName) {
        this.personalName = personalName;
        this.setCustomName(Component.literal(personalName));
        this.setCustomNameVisible(true);
    }

    public String getPersonalName() {
        return this.personalName;
    }

public boolean isBlinking() {
    return this.entityData.get(DATA_BLINK);
}

private void setBlinking(boolean blinking) {
    this.entityData.set(DATA_BLINK, blinking);
}

@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(
        this,
        "base",            // controller name (any string)
        0,                 // transition length in ticks
        (AnimationState<EntityFishMerchant> state) -> {
            boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
            return state.setAndContinue(moving ? WALK : IDLE);
        }
    ));
}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

@Override
public void tick() {
    super.tick();
    if (!this.level().isClientSide) {
        // Check if city is starving (set by the API consumption logic)
        boolean isStarving = this.getPersistentData().getBoolean("starving");

        // Optionally, store city name in the merchant's persistent data or a field
    
        // If you store cityName in a separate field, you can use getCityName() instead

        if (isStarving && cityName != null && !cityName.isEmpty()) {
                  LOGGER.info("EntityFishMerchant is starving");
            // Gather all players in a 20-block radius
            double radius = 20.0;
            AABB area = this.getBoundingBox().inflate(radius);
            List<Player> nearbyPlayers = this.level().getEntitiesOfClass(Player.class, area);

            // Display the starving message
            for (Player player : nearbyPlayers) {
                player.displayClientMessage(
                    Component.literal(cityName + " is starving! Please sell them some food."),
                    true // Action bar display
                );
            }
        }
    }

    if (!level().isClientSide) {
        if (blinkTicks > 0) {
            blinkTicks--;
            if (blinkTicks == 0) setBlinking(false);
        } else {
            // ~0.5% chance per tick ≈ every ~5 seconds at 20 TPS (tune to taste)
            if (this.random.nextFloat() < 0.005f) {
                blinkTicks = 3;       // eyes closed for ~3 ticks (≈150 ms)
                setBlinking(true);
            }
        }
    }

}


    public static void notifyNearbyPlayers(ServerLevel serverLevel, String cityName) {
        // For each FishMerchant that belongs to cityName, notify players
        for (Entity e : serverLevel.getEntities().getAll()) {
            if (e instanceof EntityFishMerchant fishMerchant 
                && fishMerchant.getCityName().equalsIgnoreCase(cityName)) {
                AABB area = fishMerchant.getBoundingBox().inflate(MESSAGE_RADIUS);
                List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(Player.class, area);
                for (Player player : nearbyPlayers) {
                    player.displayClientMessage(
                        Component.literal(cityName + " is starving! Please sell them some fish."),
                        true
                    );
                }
            }
        }
    }


    @Override
    protected void registerGoals() {

            this.goalSelector.addGoal(2, new RestrictedStrollGoal(this, 1.0D, maxHomeDistance));
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CityName", cityName);
        tag.putString("personalName", personalName);
        tag.putLong("SpawnPosition", spawnPosition.asLong());
        tag.putString("Gender", getGender());
    }

@Override
public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    cityName = tag.getString("CityName");
    this.personalName = tag.getString("personalName");
    spawnPosition = BlockPos.of(tag.getLong("SpawnPosition"));
    
    String nameFromTag = tag.contains("personalName") ? tag.getString("personalName") : "Unnamed";
    this.personalName = nameFromTag == null || nameFromTag.isEmpty() ? "Unnamed" : nameFromTag;
    this.setCustomName(Component.literal(this.personalName));

    this.setCustomNameVisible(true);

      // Restore gender (default female if missing)
        String savedGender = tag.contains("Gender") ? tag.getString("Gender") : "female";
        setGender(savedGender);

    if (!this.level().isClientSide) {
        setCityName(cityName);
    }
}

@Override
public void onAddedToLevel() { 
    LOGGER.warn("EntityFishMerchant {} is being added to the level.", this.getUUID());
    if (!this.cityName.isEmpty()) {
        LOGGER.info("City name already set to {}, skipping re-association.", this.cityName);
    }
}

    // Getter and Setter for cityName
    public String getCityName() {
        return this.cityName;
    }



    @Override
    protected void updateTrades() {
        // No trades to update since trading is handled directly in mobInteract()
    }



    @Override
    protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer offer) {
        // No XP to reward since trading is handled directly in mobInteract()
    }

    public BlockPos getSpawnPosition() {
        return spawnPosition;
    }

@Override
public InteractionResult mobInteract(Player player, InteractionHand hand) {
    // Short-circuit on the client side to prevent unnecessary logic execution.
    if (this.level().isClientSide) {
        return InteractionResult.sidedSuccess(true); // Prevent further client-side interactions.
    }

    // Ensure the merchant is associated with a valid city.
    if (cityName == null || cityName.isEmpty()) {
        player.displayClientMessage(Component.literal("This merchant is not associated with any city."), true);
        return InteractionResult.CONSUME; 
    }

    // Collect fish stacks from the player's inventory.
    Map<ItemStack, Double> fishStacks = new HashMap<>();
    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem() instanceof WeightedFishItem wfi) {
            double weight = wfi.getWeight(stack);
            if (weight > 0.0) {
                fishStacks.put(stack, weight);
            }
        }
    }

    // If the player has fish to sell, process the transaction.
    if (!fishStacks.isEmpty()) {
        String playerUuid = player.getUUID().toString();
        List<JsonObject> transactionItems = new ArrayList<>();

        // Build transaction data for the API call.
        for (Map.Entry<ItemStack, Double> entry : fishStacks.entrySet()) {
            ItemStack fishStack = entry.getKey();
            double weight = entry.getValue();
            WeightedFishItem wfi = (WeightedFishItem) fishStack.getItem();
            String fishType = wfi.getFishType(fishStack);

            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item_id", "britannia_mod:weighted_fish_item");
            itemJson.addProperty("item_name", fishType);

            if (useWeight(fishStack.getItem())) {
                itemJson.addProperty("weight", weight);
            } else {
                itemJson.addProperty("quantity", (int) weight);
            }
            transactionItems.add(itemJson);
        }

        // Send the transaction data to the API if there are valid items.
        if (!transactionItems.isEmpty()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                SendTransactionToAPI.send(
                    serverLevel,
                    playerUuid,
                    cityName,
                    transactionItems,
                    "sell",
                    "FishMerchant",
                    this.getUUID().toString(),
                    this.getName().getString(),
                    player
                );
                this.getPersistentData().remove("starving");
            }
        } else {
            player.displayClientMessage(Component.literal("No valid items to sell."), true);
        }
    } else {
        // Notify the player if they don't have any fish to sell.
        player.displayClientMessage(Component.literal("You don't have any fish to sell."), true);
    }

    return InteractionResult.CONSUME; // Indicate that the interaction was fully handled by the server.
}


public static boolean useWeight(Item item) {
    if (item instanceof WeightedFishItem) {
        return true;
    }
    return false;
}

public void setCityName(String cityName) {
    if (this.cityName != null && this.cityName.equals(cityName)) {
        LOGGER.warn("CityName already set to {}, skipping association.", cityName);
        return;
    }
    this.cityName = cityName;
    if (this.level() instanceof ServerLevel sLevel) {
        BritanniaMod.associateNpcToCity(sLevel, cityName, this);
    } else {
        LOGGER.warn("Level is not an instance of ServerLevel.");
    }
}


/*
public void associateWithCity(String cityName) {
    this.cityName = cityName;
    if (this.level() instanceof ServerLevel sLevel) {
        CityInventory cityInventory = BritanniaMod.getCityInventory(sLevel, cityName);
        cityInventory.associateMerchant(this);
        BritanniaMod.associateNpcToCity(sLevel, cityName, this);
    } else {
        LOGGER.warn("Level is not an instance of ServerLevel.");
    }
}
*/

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntityFishMerchant)) return false;
        EntityFishMerchant other = (EntityFishMerchant) obj;
        return this.getUUID().equals(other.getUUID());
    }

    @Override
    public int hashCode() {
        return this.getUUID().hashCode();
    }

private String getFishType(Item item) {
    if (item == Items.COD) return "cod";
    if (item == Items.SALMON) return "salmon";
    if (item == Items.TROPICAL_FISH) return "tropical_fish";
    return "unknown";
}


    private void giveGoldCoins(Player player, int amount) {
        // Create a sample ItemStack to get the max stack size
        ItemStack sampleStack = new ItemStack(ItemRegistry.GOLD_COIN.get());
        int stackSize = sampleStack.getMaxStackSize();

        while (amount > 0) {
            int giveAmount = Math.min(amount, stackSize);
            ItemStack coinStack = new ItemStack(ItemRegistry.GOLD_COIN.get(), giveAmount);
            if (!player.getInventory().add(coinStack)) {
                // If inventory is full, drop the coins on the ground
                this.spawnAtLocation(coinStack, 0.0F);
            }
            amount -= giveAmount;
        }
    }

    private int getItemCount(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Item item, int count) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                int stackCount = stack.getCount();
                if (stackCount >= count) {
                    stack.shrink(count);
                    return;
                } else {
                    stack.shrink(stackCount);
                    count -= stackCount;
                }
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH, 2000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
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
                .add(getAttributeHolder(ModAttributes.SCALE.get()), 0.5D)
                .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 0.08D)
                .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D)
.add(getAttributeHolder(BuiltInRegistries.ATTRIBUTE
    .get(ResourceLocation.fromNamespaceAndPath("neoforge", "nametag_distance"))), 64.0D);

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



    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null; // This NPC is not meant to breed.
    }
}
