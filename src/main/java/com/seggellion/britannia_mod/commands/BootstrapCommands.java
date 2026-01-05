// com/seggellion/britannia_mod/commands/BootstrapCommands.java
package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.inventory.CityInventory;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonParser;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BootstrapCommands {
    private BootstrapCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bootstrap")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("fish").executes(ctx -> dumpFish(ctx.getSource())))
                .then(Commands.literal("regions").executes(ctx -> dumpRegions(ctx.getSource())))
                .then(Commands.literal("stats").executes(ctx -> dumpStats(ctx.getSource())))
                .then(Commands.literal("grapes").executes(ctx -> dumpGrapes(ctx.getSource())))
                .then(Commands.literal("cities").executes(ctx -> dumpCities(ctx.getSource())))
        );
    }

// === NEW METHOD: dumpCities ===
  private static int dumpCities(CommandSourceStack source) {
        CityManager manager = CityManager.get(source.getLevel());
        Map<String, City> cities = manager.getCities();

        if (cities.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No cities found in CityManager."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] City Registry (" + cities.size() + "):"), false);

        cities.values().stream()
            .sorted(Comparator.comparing(City::getName))
            .forEach(city -> {
                CityInventory inv = city.getInventory();

                // 1. Header
                String header = String.format(" - %s", city.getName());
                source.sendSuccess(() -> Component.literal(header), false);

                if (inv == null) {
                    source.sendSuccess(() -> Component.literal("    [!] Inventory is NULL"), false);
                    return;
                }

                // 2. Population & Status
                String stats = String.format("    Pop: %d | Starving: %b", 
                    inv.getPopulation(), inv.isStarving());
                source.sendSuccess(() -> Component.literal(stats), false);

                // 3. Treasury
                String treasury = String.format("    Treasury: %dg %ds %dc", 
                    inv.getCurrencyAmount("gold"),
                    inv.getCurrencyAmount("silver"),
                    inv.getCurrencyAmount("copper"));
                source.sendSuccess(() -> Component.literal(treasury), false);

                // 4. Supplies
                // Grouping them to save chat lines
                String supplies1 = String.format("    Supplies [1/2]: Food:%.1f Wood:%.1f Metal:%.1f Stone:%.1f",
                    inv.getFoodSupply(), inv.getWoodSupply(), inv.getMetalSupply(), inv.getStoneSupply());
                String supplies2 = String.format("    Supplies [2/2]: Tex:%.1f Alc:%.1f Tech:%.1f",
                    inv.getTextileSupply(), inv.getAlcoholSupply(), inv.getTechnologySupply());
                
                source.sendSuccess(() -> Component.literal(supplies1), false);
                source.sendSuccess(() -> Component.literal(supplies2), false);

                // 5. Commodities / Weights Summary
                // We don't want to list every single item, but we can list categories
                var allComms = inv.getAllCommodities();
                if (!allComms.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("    Market Categories:"), false);
                    allComms.forEach((cat, subCats) -> {
                        // Calculate total items in this category for a quick summary
                        int totalItems = subCats.values().stream()
                            .flatMap(m -> m.values().stream())
                            .mapToInt(Integer::intValue)
                            .sum();
                        
                        double totalWeight = inv.getCategoryTotalWeight(cat);

                        source.sendSuccess(() -> Component.literal(
                            String.format("      > %s: %d items (Total Wgt: %.1f)", cat, totalItems, totalWeight)
                        ), false);
                    });
                } else {
                    source.sendSuccess(() -> Component.literal("    Market: Empty"), false);
                }
            });

        return cities.size();
    }

    // === NEW METHOD: dumpGrapes ===
    private static int dumpGrapes(CommandSourceStack source) {
        // Fetch all varieties from the Manager
        var varieties = GrapeVarietyManager.getAllVarieties();

        if (varieties.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No grape varieties loaded."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Grape Varieties:"), false);

        varieties.stream()
            .sorted(Comparator.comparing(GrapeVariety::id)) // Sort alphabetically by ID
            .forEach(g -> {
                // Header Line
                String header = String.format(" - %s (%s) | Color: %s", 
                    g.displayName(), g.id(), g.colorType());
                source.sendSuccess(() -> Component.literal(header), false);

                // Requirements Line 1: Chemistry
                String chem = String.format("    Chemistry: N:%.1f P:%.1f K:%.1f OM:%.1f | Hydration: %d", 
                    g.requiredNitrogen(), g.requiredPhosphorus(), g.requiredPotassium(), g.requiredOrganicMatter(), g.optimalHydration());
                source.sendSuccess(() -> Component.literal(chem), false);

                // Requirements Line 2: Environment
                String env = String.format("    Climate: %s | Alt: %d-%d | Diff: %d", 
                    g.climate(), g.minAltitude(), g.maxAltitude(), g.difficulty());
                source.sendSuccess(() -> Component.literal(env), false);
            });

        source.sendSuccess(() -> Component.literal("[Bootstrap] Total varieties: " + varieties.size()), false);
        return varieties.size();
    }

    private static int dumpFish(CommandSourceStack source) {
        Map<ResourceLocation, FishCatalog.FishMeta> map = FishCatalog.snapshot();

        if (map.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No fish loaded."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Fish catalog:"), false);

        map.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().toString()))
            .forEach(e -> {
                ResourceLocation key = e.getKey();
                FishCatalog.FishMeta m = e.getValue();
                String line = String.format(" - %s | name=%s | weight=%.2f..%.2f | min_skill=%d | rarity=%d",
                        key, m.name, m.minWeight, m.maxWeight, m.minSkill, m.rarity);
                source.sendSuccess(() -> Component.literal(line), false);
            });

        source.sendSuccess(() -> Component.literal("[Bootstrap] Total fish: " + map.size()), false);
        return map.size();
    }

 public static int dumpStats(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[Bootstrap] Run this as a player."));
            return 0;
        }

        PlayerData pd = PlayerDataStore.get(player);

        source.sendSuccess(() -> Component.literal("[Bootstrap] ShardUser:"), false);
        source.sendSuccess(() -> Component.literal(" - gender: " + pd.getGender()), false);
        source.sendSuccess(() -> Component.literal(" - fame: " + pd.getFame()), false);
        source.sendSuccess(() -> Component.literal(" - karma: " + pd.getKarma()), false);
        source.sendSuccess(() -> Component.literal(" - murder_count: " + pd.getMurderCount()), false);
        source.sendSuccess(() -> Component.literal(" - inventory: " + pd.inventoryOneLine()), false);
        source.sendSuccess(() -> Component.literal(" - stats: " + pd.statsOneLine()), false);

        return 1;
    }

    private static int dumpRegions(CommandSourceStack source) {
        List<RegionData> regs = RegionCache.all();

        if (regs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No regions loaded."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Regions:"), false);

        regs.stream()
            .sorted(Comparator.comparing(r -> r.name))
            .forEach(r -> {
                String header = String.format(" - %s | X:%d..%d Y:%d..%d Z:%d..%d | items=%d",
                        r.name, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ, r.items.size());
                source.sendSuccess(() -> Component.literal(header), false);

                // Show a quick breakdown of item types per region (fish count etc.)
                long fishCount = r.items.stream().filter(i -> "fish".equalsIgnoreCase(i.type)).count();
                long otherCount = r.items.size() - fishCount;
                String breakdown = String.format("    · fish=%d, other=%d", fishCount, otherCount);
                source.sendSuccess(() -> Component.literal(breakdown), false);

                // Optionally list a few fish keys with weight
                r.items.stream()
                    .filter(i -> "fish".equalsIgnoreCase(i.type))
                    .limit(5)
                    .forEach(i -> {
                        String fishLine = String.format("    · %s weight=%d%s",
                                i.key, i.weight(),
                                (i.minSkillOverride != null ? " min_skill_override=" + i.minSkillOverride : ""));
                        source.sendSuccess(() -> Component.literal(fishLine), false);
                    });

                if (fishCount > 5) {
                    source.sendSuccess(() -> Component.literal("    · …"), false);
                }
            });

        source.sendSuccess(() -> Component.literal("[Bootstrap] Total regions: " + regs.size()), false);
        return regs.size();
    }
}
