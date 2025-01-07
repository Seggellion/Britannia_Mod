package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.ai.goal.RestrictedStrollGoal;
import net.minecraft.server.level.ServerPlayer; 
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.market.MarketManager;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.BritanniaMod;


import com.seggellion.britannia_mod.ModAttributes;
import com.seggellion.britannia_mod.registry.ItemRegistry;
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
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EntityFishMerchant extends AbstractVillager implements IEntityExtension, ICityEntity  {
    private String cityName;
    private BlockPos spawnPosition;
    private int maxHomeDistance = 5; // Set to match the spawner's radius
    private static final Logger LOGGER = LogManager.getLogger();
    
    public EntityFishMerchant(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.cityName = ""; 
        this.spawnPosition = this.blockPosition();
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
        tag.putLong("SpawnPosition", spawnPosition.asLong());
    }

@Override
public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    cityName = tag.getString("CityName");
    spawnPosition = BlockPos.of(tag.getLong("SpawnPosition"));
    
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
    if (!this.level().isClientSide) {
        LOGGER.info("Player interacting with Fish Merchant");

MarketManager.initializeCity(cityName);
LOGGER.info("Market prices for city {} initialized: {}", cityName, MarketManager.getCityPrices(cityName));


        if (cityName == null || cityName.isEmpty()) {
            player.displayClientMessage(Component.literal("This merchant is not associated with any city."), true);
            return InteractionResult.SUCCESS;
        }

        Map<ItemStack, Double> fishStacks = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            LOGGER.info("Checking item in inventory: {}", stack.getItem());
            if (stack.getItem() instanceof WeightedFishItem wfi) {
                LOGGER.info("Found WeightedFishItem: {}", stack.getItem());
                double weight = wfi.getWeight(stack);
                LOGGER.info("Retrieved weight: {}", weight);
                if (weight > 0.0) {
                    fishStacks.put(stack, weight);
                }
            } else {
                LOGGER.info("Item is not a WeightedFishItem: {}", stack.getItem());
            }
        }
         LOGGER.info("Is this loading?");
        if (!fishStacks.isEmpty()) {
            int totalCoins = 0;
            int codCount = 0, salmonCount = 0, tropicalFishCount = 0;
            int tunaCount = 0, troutCount = 0, swordfishCount = 0; // Add new counts

            double codWeightTotal = 0.0, salmonWeightTotal = 0.0, tropicalFishWeightTotal = 0.0;
            double tunaWeightTotal = 0.0, troutWeightTotal = 0.0, swordfishWeightTotal = 0.0; // Add new weights

            for (Map.Entry<ItemStack, Double> entry : fishStacks.entrySet()) {
                ItemStack fishStack = entry.getKey();
                double weight = entry.getValue();

                String fishType = "unknown";
                if (fishStack.getItem() instanceof WeightedFishItem wfi) {
                    // Retrieve the stored fish type from the WeightedFishItem
                    fishType = wfi.getFishType(fishStack);
                } else {
                    // Fallback if needed (shouldn't happen since we only handle WeightedFishItem)
                    fishType = getFishType(fishStack.getItem());
                }


                double marketPrice = MarketManager.getMarketPrice(cityName, fishType);
                LOGGER.info("Market price for {} in {}: {}", fishType, cityName, marketPrice);


                int coins = (int) (weight * marketPrice);
                totalCoins += coins;
                LOGGER.info("Calculated coins for {} (weight: {}): {}", fishType, weight, coins);

                // Update counts
                if (fishType.equals("cod")) codCount += fishStack.getCount();
                if (fishType.equals("salmon")) salmonCount += fishStack.getCount();
                if (fishType.equals("tuna")) tunaCount += fishStack.getCount();
                if (fishType.equals("trout")) troutCount += fishStack.getCount();
                if (fishType.equals("swordfish")) swordfishCount += fishStack.getCount();

                // Remove fish from inventory
                player.getInventory().removeItem(fishStack);
            }

      LOGGER.info("What is total coins? {}", totalCoins);
            if (totalCoins > 0) {
                giveGoldCoins(player, totalCoins);
                player.displayClientMessage(Component.literal("Thank you for your fish! Here are your gold coins."), true);

                if (this.level() instanceof ServerLevel serverLevel) {
                    CityManager cityManager = CityManager.get(serverLevel);
                    City city = cityManager.getCity(cityName);

                    // Calculate total weights
                    for (Map.Entry<ItemStack, Double> entry : fishStacks.entrySet()) {
                        ItemStack fishStack = entry.getKey();
                        double weight = entry.getValue();
                        WeightedFishItem wfi = (WeightedFishItem) fishStack.getItem();
                        String fishType = wfi.getFishType(fishStack);
                        int count = fishStack.getCount();
                        double totalFishWeight = weight * count;

                        switch (fishType) {
                            case "cod":
                                codWeightTotal += totalFishWeight;
                                break;
                            case "salmon":
                                salmonWeightTotal += totalFishWeight;
                                break;
                            case "tuna":
                                tunaWeightTotal += totalFishWeight;
                                break;
                            case "trout":
                                troutWeightTotal += totalFishWeight;
                                break;
                            case "swordfish":
                                swordfishWeightTotal += totalFishWeight;
                                break;
                            case "tropical_fish":
                                tropicalFishWeightTotal += totalFishWeight;
                                break;
                            default:
                                LOGGER.warn("Unknown fish type encountered: {}", fishType);
                                break;
                        }
                    }


                    if (city != null) {
                        CityInventory cityInventory = city.getInventory();
                        cityInventory.addCommodity("food", "fish", "cod", codCount);
                        cityInventory.addCommodity("food", "fish", "salmon", salmonCount);
                        cityInventory.addCommodity("food", "fish", "tuna", tunaCount);
                        cityInventory.addCommodity("food", "fish", "trout", troutCount);
                        cityInventory.addCommodity("food", "fish", "swordfish", swordfishCount);

                        // Add weight data
                        cityInventory.addCommodityWeight("food", "fish", "cod", codWeightTotal);
                        cityInventory.addCommodityWeight("food", "fish", "salmon", salmonWeightTotal);
                        cityInventory.addCommodityWeight("food", "fish", "tuna", tunaWeightTotal);
                        cityInventory.addCommodityWeight("food", "fish", "trout", troutWeightTotal);
                        cityInventory.addCommodityWeight("food", "fish", "swordfish", swordfishWeightTotal);

                        cityManager.setDirty();
                        LOGGER.info("Calling adjustPrices for city: {}", cityName);
                        MarketManager.adjustPrices(cityName, cityInventory);


                        // Convert fishStacks (Map<ItemStack, Double>) to Map<String, Double> fishTypeContributions
                        Map<String, Double> fishTypeContributions = new HashMap<>();
                        for (Map.Entry<ItemStack, Double> entry : fishStacks.entrySet()) {
                            ItemStack fishStack = entry.getKey();
                            double weight = entry.getValue();
                            WeightedFishItem wfi = (WeightedFishItem) fishStack.getItem();
                            String fishType = wfi.getFishType(fishStack);
                            
                            // If there are multiple items in this stack, each contributes 'weight'
                            // If weight is per stack, adjust logic accordingly. Assuming weight is per item:
                            double totalFishContribution = weight * fishStack.getCount();
                            fishTypeContributions.merge(fishType, totalFishContribution, Double::sum);
                        }

                        PlayerDataManager manager = PlayerDataManager.get(serverLevel);
                        if (player instanceof ServerPlayer serverPlayer) {
                            manager.recordSale(serverPlayer, fishTypeContributions);
                        }
                    } else {
                        player.displayClientMessage(Component.literal("City not found: " + cityName), true);
                    }
                }
            } else {
                player.displayClientMessage(Component.literal("No coins awarded. Are these fish valid?"), true);
            }
        } else {
            player.displayClientMessage(Component.literal("You don't have any fish to sell."), true);
        }

        return InteractionResult.SUCCESS;
    }
    return super.mobInteract(player, hand);
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

    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null; // This NPC is not meant to breed.
    }
}
