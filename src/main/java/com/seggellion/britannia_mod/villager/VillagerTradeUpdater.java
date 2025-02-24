package com.seggellion.britannia_mod.villager;

import com.seggellion.britannia_mod.util.BlacksmithTradeHelper;
import com.seggellion.britannia_mod.util.LocalRecipes;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.SendTransactionToAPI;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.component.DataComponentPredicate;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonArray;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;

public class VillagerTradeUpdater {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onPlayerInteractWithVillager(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;


        if (villager.getVillagerData().getProfession() == BlacksmithProfessions.JOURNEYMAN_BLACKSMITH.get()) {

            String cityName = getVillagerCityName(villager);

            JsonObject tradeData = CityDataSync.fetchCityDataWithMarketPrices(serverLevel, cityName);
            if (tradeData == null || tradeData.isEmpty()) {
                LOGGER.warn("No trade data found for city: {}", cityName);
                return;
            }



            int techSupply = tradeData.get("tech_supply").getAsInt();
            JsonArray metalSupply = tradeData.getAsJsonArray("metal_commodities");
            JsonObject marketPrices = tradeData.getAsJsonObject("market_prices");


            MerchantOffers offers = generateDynamicTrades(techSupply, metalSupply, marketPrices);

            villager.setOffers(offers);
            LOGGER.info("Total trades available: {}", offers.size());
        }
    }

    private static MerchantOffers generateDynamicTrades(int techSupply, JsonArray metalSupply, JsonObject marketPrices) {
        MerchantOffers offers = new MerchantOffers();

        List<ItemStack> swordStacks = BlacksmithTradeHelper.getTradesForTechLevel(techSupply, metalSupply);

        for (ItemStack sword : swordStacks) {
            if (sword.isEmpty()) continue;

            String itemKey = BuiltInRegistries.ITEM.getKey(sword.getItem()).toString();
            String shortKey = itemKey.contains(":") ? itemKey.split(":")[1] : itemKey;

            if (!materialAvailableForItem(sword, metalSupply)) {
                LOGGER.info("Skipping {} due to missing materials.", shortKey);
                continue;
            }
            double basePrice = computePriceFromRecipes(sword, marketPrices);
            int finalPrice = (int) Math.ceil(basePrice * 1.4);

            ItemCost cost = new ItemCost(
                ItemRegistry.GOLD_COIN.get().builtInRegistryHolder(),
                finalPrice,
                DataComponentPredicate.EMPTY,
                ItemStack.EMPTY
            );

            offers.add(new MerchantOffer(
                cost,
                Optional.empty(),
                sword,
                Integer.MAX_VALUE,
                0,
                0.05F
            ));
        }
        return offers;
    }

private static boolean materialAvailableForItem(ItemStack sword, JsonArray metalSupply) {
    // Ensure the item is a QualitySwordItem
    if (!(sword.getItem() instanceof QualitySwordItem)) {
        return false;
    }

    // Extract metal type from `custom_model_data`
    int modelData = sword.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
    String metalType = QualitySwordItem.getMaterialFromModelData(modelData);


    if (metalType == null) {
        LOGGER.warn("Unknown material for modelData: {}", modelData);
        return false;
    }

    // Check if the metal is available in the supply
    for (var element : metalSupply) {
        if (element.isJsonArray()) {
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() == 2) {
                String availableMetal = pair.get(0).getAsString().toLowerCase();
                double quantity = pair.get(1).getAsDouble();

                if (metalType.equals(availableMetal) && quantity > 0) {
                    return true;
                }
            } else {
                LOGGER.error("Invalid metal commodity pair: {}", pair);
            }
        } else {
            LOGGER.error("Expected JSON array but found: {}", element);
        }
    }
    return false;
}


  private static double computePriceFromRecipes(ItemStack sword, JsonObject marketPrices) {
    // Get material from sword
    int modelData = sword.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
    String material = QualitySwordItem.getMaterialFromModelData(modelData);

    if (material == null) {
        return 10.0; // Default price
    }

    double basePrice = marketPrices.has(material) ? marketPrices.get(material).getAsDouble() : 10.0;
    int quality = QualitySwordItem.getQuality(sword);

    // Adjust price by quality
    double finalPrice = basePrice * (1.0 + (quality - 1) * 0.2); // Higher quality = more expensive
    return Math.ceil(finalPrice); // Round up
}


 private static String getVillagerCityName(Villager villager) {
        // Read from persistent NBT with fallback
        return villager.getPersistentData().getString("CityName");
    }

    @SubscribeEvent
    public static void onTradeCompleted(TradeWithVillagerEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        AbstractVillager villager = event.getAbstractVillager();

            if (villager instanceof Villager) {
            MerchantOffer offer = event.getMerchantOffer();
            ItemStack result = offer.getResult();
            ItemCost cost = offer.getItemCostA(); // Correct method


            // Extract trade details
            String itemKey = result.getItem().getDescriptionId();
            int quantity = result.getCount();
           int itemPrice = cost.count();

 // Create transaction items list to match expected parameter type
    JsonObject itemDetails = new JsonObject();
    itemDetails.addProperty("item_id", itemKey);
    itemDetails.addProperty("item_name", itemKey);
    itemDetails.addProperty("quantity", quantity);
    itemDetails.addProperty("price", itemPrice);

    List<JsonObject> transactionItems = List.of(itemDetails);

            // Retrieve city name from villager's NBT
            String cityName = villager.getPersistentData().getString("CityName");
            if (cityName.isEmpty()) {
                LOGGER.warn("City name not found in villager NBT");
                return;
            }
            VillagerTradeUpdater updater = new VillagerTradeUpdater();
            updater.initializeTransactionFromTrade(
                serverLevel,
                player,
                villager,
                transactionItems,
                "purchase"
            );

        }
    }

public void initializeTransactionFromTrade(ServerLevel serverLevel, Player player, AbstractVillager villager, List<JsonObject> transactionItems, String transactionType) {
    String cityName = villager.getPersistentData().getString("CityName");
    if (cityName.isEmpty()) {
        LOGGER.warn("City name not found in villager NBT");
        return;
    }

    SendTransactionToAPI.send(
        serverLevel,
        player.getUUID().toString(), 
        cityName,                   
        transactionItems,           
        transactionType,            
        villager.getType().toString(), 
        villager.getUUID().toString(), 
        villager.getName().getString(),
        player                   
    );
}

}