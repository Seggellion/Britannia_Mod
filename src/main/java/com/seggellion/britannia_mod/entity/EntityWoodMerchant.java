package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.market.MarketManager;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import  net.neoforged.neoforge.common.NeoForgeMod;
import java.util.HashMap;
import java.util.Map;

/**
 * Wood merchant that buys WeightedWoodItem from players and updates city inventory accordingly.
 */
public class EntityWoodMerchant extends AbstractVillager  implements ICityEntity {
    private static final Logger LOGGER = LogManager.getLogger();

    private String cityName;

    public EntityWoodMerchant(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.cityName = "";
    }

    /**
     * AbstractVillager requires these abstract methods:
     *  - updateTrades()
     *  - rewardTradeXp(MerchantOffer) [already inherited but can be overridden if needed]
     */
    @Override
    protected void updateTrades() {
        // WoodMerchant does not use typical trades. All logic is in mobInteract.
    }



    @Override
    protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer offer) {
        // No XP to reward
    }

    /**
     * The mobInteract method handles the logic of exchanging WeightedWoodItem for gold coins.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            if (cityName == null || cityName.isEmpty()) {
                player.displayClientMessage(Component.literal("This merchant is not associated with any city."), true);
                return InteractionResult.SUCCESS;
            }

            Map<ItemStack, Double> woodStacks = new HashMap<>();
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof WeightedWoodItem wwi) {
                    double weight = wwi.getWeight(stack);
                    if (weight > 0.0) {
                        woodStacks.put(stack, weight);
                    }
                }
            }

            if (!woodStacks.isEmpty()) {
                double totalCoins = 0.0;
                Map<String, Integer> woodCounts = new HashMap<>();
                Map<String, Double> woodWeights = new HashMap<>();

                for (Map.Entry<ItemStack, Double> entry : woodStacks.entrySet()) {
                    ItemStack woodStack = entry.getKey();
                    double weight = entry.getValue();
                    WeightedWoodItem wwi = (WeightedWoodItem) woodStack.getItem();
                    String woodType = wwi.getWoodType(woodStack);

                    double price = MarketManager.getMarketPrice(cityName, woodType);
                    double coinsForThisStack = weight * price;
                    totalCoins += coinsForThisStack;

                    int count = woodStack.getCount();
                    woodCounts.merge(woodType, count, Integer::sum);
                    woodWeights.merge(woodType, weight * count, Double::sum);

                    // Remove from player's inventory
                    player.getInventory().removeItem(woodStack);
                }

                if (totalCoins > 0) {
                    giveGoldCoins(player, (int) totalCoins);
                    player.displayClientMessage(Component.literal("Thank you for your wood! Here are your gold coins. Lumber added to the supply" + cityName), true);

                    if (this.level() instanceof ServerLevel serverLevel) {
                        CityManager cityManager = CityManager.get(serverLevel);
                        City city = cityManager.getCity(cityName);
                        if (city != null) {
                            CityInventory cityInventory = city.getInventory();

                            // Tally up wood data
                            for (Map.Entry<String, Integer> e : woodCounts.entrySet()) {
                                cityInventory.addCommodity("wood", "logs", e.getKey(), e.getValue());
                            }
                            for (Map.Entry<String, Double> e : woodWeights.entrySet()) {
                                cityInventory.addCommodityWeight("wood", "logs_weight", e.getKey(), e.getValue());
                            }

                            cityManager.setDirty();

                            // Adjust prices
                            MarketManager.adjustPrices(cityName, cityInventory);

                            // Record sale in PlayerDataManager
                            Map<String, Double> woodTypeContributions = new HashMap<>();
                            for (Map.Entry<ItemStack, Double> entry : woodStacks.entrySet()) {
                                ItemStack s = entry.getKey();
                                WeightedWoodItem wwi = (WeightedWoodItem) s.getItem();
                                String wType = wwi.getWoodType(s);
                                double w = entry.getValue() * s.getCount();
                                woodTypeContributions.merge(wType, w, Double::sum);
                            }

                            PlayerDataManager manager = PlayerDataManager.get(serverLevel);
                            if (player instanceof ServerPlayer serverPlayer) {
                                manager.recordSale(serverPlayer, woodTypeContributions);
                            }
                        } else {
                            player.displayClientMessage(Component.literal("City not found: " + cityName), true);
                        }
                    }
                } else {
                    player.displayClientMessage(Component.literal("No coins awarded. Are these wood items valid?"), true);
                }
            } else {
                player.displayClientMessage(Component.literal("You don't have any wood to sell."), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private void giveGoldCoins(Player player, int amount) {
        // Basic logic to spawn gold coin stacks
        ItemStack sampleStack = new ItemStack(ItemRegistry.GOLD_COIN.get());
        int stackSize = sampleStack.getMaxStackSize();

        while (amount > 0) {
            int giveAmount = Math.min(amount, stackSize);
            ItemStack coinStack = new ItemStack(ItemRegistry.GOLD_COIN.get(), giveAmount);
            if (!player.getInventory().add(coinStack)) {
                this.spawnAtLocation(coinStack, 0.0F);
            }
            amount -= giveAmount;
        }
    }

@Override
public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putString("CityName", this.cityName != null ? this.cityName : "");
}

@Override
public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    this.cityName = tag.getString("CityName");
}

    public String getCityName() {
        return this.cityName;
    }


    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    /**
     * If you want to set custom attributes (like health, speed, etc.),
     * define a static createAttributes() method just like your fish merchant.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH, 20.0D)
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
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
                .add(getAttributeHolder(ModAttributes.SCALE.get()), 1.0D)
                .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 0.08D)
                .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D)
.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
                           .add(NeoForgeMod.SWIM_SPEED, 1.0D); 
    }

    private static Holder<Attribute> getAttributeHolder(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
            .flatMap(BuiltInRegistries.ATTRIBUTE::getHolder)
            .orElseThrow(() -> new IllegalArgumentException("Attribute not registered: " + attribute));
    }

    /**
     * Required by AbstractVillager. If not trading, just leave empty or return null as needed.
     */
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null; 
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
