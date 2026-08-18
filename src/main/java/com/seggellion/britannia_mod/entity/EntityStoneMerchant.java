package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.market.MarketManager;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.block.entity.StoneSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.SendTransactionToAPI;
import com.seggellion.britannia_mod.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import  net.neoforged.neoforge.common.NeoForgeMod;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;




/**
 * Stone merchant that buys GradeStoneItem from players and updates city inventory accordingly.
 */
public class EntityStoneMerchant extends AbstractVillager  implements ICityEntity {
    private static final Logger LOGGER = LogManager.getLogger();

    private String cityName;

    public EntityStoneMerchant(EntityType<? extends AbstractVillager> entityType, Level level) {
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
        // StoneMerchant does not use typical trades. All logic is in mobInteract.
    }

    @Override
    protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer offer) {
        // No XP to reward
    }

    private BlockPos spawnBlockPos; // Store the spawn block position when the NPC is spawned

    public void setSpawnBlockPos(BlockPos pos) {
        this.spawnBlockPos = pos;
    }

public BlockPos getSpawnBlockPos() {
    // Example implementation to ensure the spawn position is valid
    if (this.spawnBlockPos != null && this.level().isInWorldBounds(this.spawnBlockPos)) {
        return this.spawnBlockPos;
    }
    LOGGER.warn("Spawn block position is invalid for NPC {}", this.getUUID());
    return null;
}


    /**
     * The mobInteract method handles the logic of exchanging GradeStoneItem for gold coins.
     */
 @Override
public InteractionResult mobInteract(Player player, InteractionHand hand) {
    // If this is the client side, short-circuit immediately so we don't spam the action bar.
    if (this.level().isClientSide) {
        // This tells the client the interaction was “successful” so it stops further checks,
        // but it won't run the stone-check logic or display "You don't have any stone."
        return InteractionResult.sidedSuccess(true);
    }

    // Now we’re on the server side. Run the real logic only once.
    if (cityName == null || cityName.isEmpty()) {
        player.displayClientMessage(Component.literal("This merchant is not associated with any city."), true);
        return InteractionResult.CONSUME; 
    }

    Map<ItemStack, Double> stoneStacks = new HashMap<>();
    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem() instanceof GradeStoneItem wwi) {
            double grade = wwi.getGradeValue(stack);
            if (grade > 0.0) {
                stoneStacks.put(stack, grade);
            }
        }
    }

    // If we do have stone, build transaction data. If not, show “You don’t have any stone.”
    if (!stoneStacks.isEmpty()) {
        String playerUuid = player.getUUID().toString();
        List<JsonObject> transactionItems = new ArrayList<>();

        for (Map.Entry<ItemStack, Double> entry : stoneStacks.entrySet()) {
            ItemStack stoneStack = entry.getKey();
            double weight = entry.getValue();
            GradeStoneItem wwi = (GradeStoneItem) stoneStack.getItem();
            String stoneType = wwi.getStoneType(stoneStack);

            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item_id", "britannia_mod:grade_stone_item");
            itemJson.addProperty("item_name", stoneType);

            if (useWeight(stoneStack.getItem())) {
                itemJson.addProperty("weight", weight);
            } else {
                itemJson.addProperty("quantity", (int) weight);
            }
            transactionItems.add(itemJson);
        }

        if (!transactionItems.isEmpty()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                SendTransactionToAPI.send(
                    serverLevel,
                    playerUuid,
                    cityName,
                    transactionItems,
                    "sell",
                    "StoneMerchant",
                    this.getUUID().toString(),
                    this.getName().getString(),
                    player
                );
            }
        } else {
            player.displayClientMessage(Component.literal("No valid items to sell."), true);
        }
    } else {
        player.displayClientMessage(Component.literal("You don't have any stone to sell."), true);
    }

    // Returning CONSUME means the server handled the interaction fully and
    // we’re not passing it on for another round of logic.
    return InteractionResult.CONSUME; 
}


public static boolean useWeight(Item item) {
    // Check if the item is an instance of a class that uses weight
    if (item instanceof GradeStoneItem) {
        return true;
    }
    // Add more conditions for other item types that use weight if needed
    // For example: if (item instanceof AnotherWeightedItemType) { return true; }

    // Default to using quantity for all other items
    return false;
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
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide) {
            // Ensure this NPC is removed from city data and spawn block if it’s a server-side removal.
            removeNpcFromCity();
            removeNpcFromSpawnBlock();
        }
    }

    private void removeNpcFromCity() {
        // Example: calling an API or manager method to remove from city data
        // The cityName or cityId must be stored on this entity, e.g., getCityName()
            if (!(this.level() instanceof ServerLevel serverLevel)) {
        return;
    }
        CityDataSync.removeNpcAsync(serverLevel, this.getUUID());
    }

private void removeNpcFromSpawnBlock() {
    if (!(this.level() instanceof ServerLevel serverLevel)) {
        return;
    }

    BlockPos spawnPos = this.getSpawnBlockPos();

    // Ensure spawnPos is not null and within valid world height
    if (spawnPos == null || !serverLevel.isInWorldBounds(spawnPos)) {
        LOGGER.warn("Invalid spawn position for NPC {}. Cannot remove NPC from spawn block.", this.getUUID());
        return;
    }

    BlockEntity blockEntity = serverLevel.getBlockEntity(spawnPos);
    if (blockEntity instanceof StoneSpawnBlockEntity stoneSpawnBE) {
        stoneSpawnBE.removeAssociatedNpc(this.getUUID());
        LOGGER.info("Removed NPC {} from spawn block at {}", this.getUUID(), spawnPos);
    } else {
        LOGGER.warn("No valid StoneSpawnBlockEntity found at {}", spawnPos);
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

}
