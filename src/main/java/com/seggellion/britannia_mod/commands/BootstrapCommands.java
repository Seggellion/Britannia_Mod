// com/seggellion/britannia_mod/commands/BootstrapCommands.java
package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BootstrapCommands {
    private BootstrapCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bootstrap")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> dumpOverview(ctx.getSource()))
                .then(Commands.literal("fish")
                    .executes(ctx -> dumpFish(ctx.getSource()))
                )
                .then(Commands.literal("regions")
                    .then(Commands.literal("current")
                        .executes(BootstrapCommands::executeCurrentRegion)
                    )
                    .executes(ctx -> dumpRegions(ctx.getSource()))
                )
                .then(Commands.literal("stats")
                    .executes(ctx -> dumpStats(ctx.getSource()))
                )
                .then(Commands.literal("grapes")
                    .executes(ctx -> dumpGrapes(ctx.getSource()))
                )
                .then(Commands.literal("cities")
                    .executes(ctx -> dumpCities(ctx.getSource()))
                )
        );
    }

    private static int dumpOverview(CommandSourceStack source) {
        List<RegionData> regions = RegionCache.all();
        int fishCount = FishCatalog.snapshot().size();
        int cityCount = CityManager.get(source.getLevel()).getCities().size();
        int grapeCount = GrapeVarietyManager.getAllVarieties().size();

        source.sendSuccess(() -> Component.literal("World Bootstrap Debug"), false);
        source.sendSuccess(() -> Component.literal("Status: " + RegionCache.lastStatus()), false);
        source.sendSuccess(() -> Component.literal("Shard: " + RegionCache.lastShard()
                + " | HTTP: " + RegionCache.lastHttpStatus()
                + " | parsed_regions: " + RegionCache.lastParsedRegionCount()), false);
        source.sendSuccess(() -> Component.literal("Fish loaded: " + fishCount), false);
        source.sendSuccess(() -> Component.literal("Regions loaded: " + regions.size()), false);
        source.sendSuccess(() -> Component.literal("Cities loaded: " + cityCount), false);
        source.sendSuccess(() -> Component.literal("Grape varieties loaded: " + grapeCount), false);

        try {
            ServerPlayer player = source.getPlayerOrException();
            BlockPos pos = player.blockPosition();
            source.sendSuccess(() -> Component.literal("Current position: x=" + pos.getX()
                    + " y=" + pos.getY()
                    + " z=" + pos.getZ()), false);
            var currentRegion = FarmingClimateResolver.findRegionAt(player.level(), pos);
            source.sendSuccess(() -> Component.literal("Current region: "
                    + currentRegion.map(region -> region.name).orElse("<none>")), false);
            source.sendSuccess(() -> Component.literal("Current climate: "
                    + FarmingClimateResolver.resolveClimateName(player.level(), pos)), false);
            source.sendSuccess(() -> Component.literal("Resolver: "
                    + FarmingClimateResolver.resolveRegionDebugReason(player.level(), pos)), false);
        } catch (Exception ignored) {
            source.sendSuccess(() -> Component.literal("Current position: <run as a player for region lookup>"), false);
        }

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Warning: no regions are currently cached server-side."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("First regions:"), false);
        regions.stream()
                .sorted(Comparator.comparing(r -> r.name))
                .limit(5)
                .forEach(region -> source.sendSuccess(() -> Component.literal(" - "
                        + region.name
                        + " climate=" + region.climate()
                        + " bounds=(" + region.minX + "," + region.minY + "," + region.minZ + ") -> ("
                        + region.maxX + "," + region.maxY + "," + region.maxZ + ")"
                        + " items=" + itemsOf(region).size()), false));

        return regions.size();
    }

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

                String header = String.format(" - %s", city.getName());
                source.sendSuccess(() -> Component.literal(header), false);

                if (inv == null) {
                    source.sendSuccess(() -> Component.literal("    [!] Inventory is NULL"), false);
                    return;
                }

                String stats = String.format(
                    "    Pop: %d | Starving: %b",
                    inv.getPopulation(),
                    inv.isStarving()
                );
                source.sendSuccess(() -> Component.literal(stats), false);

                String treasury = String.format(
                    "    Treasury: %dg %ds %dc",
                    inv.getCurrencyAmount("gold"),
                    inv.getCurrencyAmount("silver"),
                    inv.getCurrencyAmount("copper")
                );
                source.sendSuccess(() -> Component.literal(treasury), false);

                String supplies1 = String.format(
                    "    Supplies [1/2]: Food:%.1f Wood:%.1f Metal:%.1f Stone:%.1f",
                    inv.getFoodSupply(),
                    inv.getWoodSupply(),
                    inv.getMetalSupply(),
                    inv.getStoneSupply()
                );

                String supplies2 = String.format(
                    "    Supplies [2/2]: Tex:%.1f Alc:%.1f Tech:%.1f",
                    inv.getTextileSupply(),
                    inv.getAlcoholSupply(),
                    inv.getTechnologySupply()
                );

                source.sendSuccess(() -> Component.literal(supplies1), false);
                source.sendSuccess(() -> Component.literal(supplies2), false);

                var allComms = inv.getAllCommodities();
                if (!allComms.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("    Market Categories:"), false);

                    allComms.forEach((cat, subCats) -> {
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

    private static int dumpGrapes(CommandSourceStack source) {
        var varieties = GrapeVarietyManager.getAllVarieties();

        if (varieties.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No grape varieties loaded."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Grape Varieties:"), false);

        varieties.stream()
            .sorted(Comparator.comparing(GrapeVariety::id))
            .forEach(g -> {
                String header = String.format(
                    " - %s (%s) | Color: %s",
                    g.displayName(),
                    g.id(),
                    g.colorType()
                );
                source.sendSuccess(() -> Component.literal(header), false);

                String chem = String.format(
                    "    Chemistry: N:%.1f P:%.1f K:%.1f OM:%.1f | Hydration: %d",
                    g.requiredNitrogen(),
                    g.requiredPhosphorus(),
                    g.requiredPotassium(),
                    g.requiredOrganicMatter(),
                    g.optimalHydration()
                );
                source.sendSuccess(() -> Component.literal(chem), false);

                String env = String.format(
                    "    Climate: %s | Alt: %d-%d | Diff: %d",
                    g.climate(),
                    g.minAltitude(),
                    g.maxAltitude(),
                    g.difficulty()
                );
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

                String line = String.format(
                    " - %s | name=%s | weight=%.2f..%.2f | min_skill=%d | rarity=%d",
                    key,
                    m.name,
                    m.minWeight,
                    m.maxWeight,
                    m.minSkill,
                    m.rarity
                );

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
            source.sendSuccess(() -> Component.literal("[Bootstrap] Last status: " + RegionCache.lastStatus()), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Regions:"), false);

        regs.stream()
            .sorted(Comparator.comparing(r -> r.name))
            .forEach(r -> {
                List<RegionItemData> items = itemsOf(r);

                String header = String.format(
                    " - %s | Climate:%s | X:%d..%d Y:%d..%d Z:%d..%d | items=%d",
                    r.name,
                    r.climate(),
                    r.minX,
                    r.maxX,
                    r.minY,
                    r.maxY,
                    r.minZ,
                    r.maxZ,
                    items.size()
                );
                source.sendSuccess(() -> Component.literal(header), false);

                long fishCount = items.stream()
                    .filter(i -> "fish".equalsIgnoreCase(i.type))
                    .count();

                long otherCount = items.size() - fishCount;

                String breakdown = String.format("    · fish=%d, other=%d", fishCount, otherCount);
                source.sendSuccess(() -> Component.literal(breakdown), false);

                items.stream()
                    .filter(i -> "fish".equalsIgnoreCase(i.type))
                    .limit(5)
                    .forEach(i -> {
                        String fishLine = String.format(
                            "    · %s weight=%d%s",
                            i.key,
                            i.weight(),
                            i.minSkillOverride != null ? " min_skill_override=" + i.minSkillOverride : ""
                        );
                        source.sendSuccess(() -> Component.literal(fishLine), false);
                    });

                if (fishCount > 5) {
                    source.sendSuccess(() -> Component.literal("    · …"), false);
                }
            });

        source.sendSuccess(() -> Component.literal("[Bootstrap] Total regions: " + regs.size()), false);
        return regs.size();
    }

    private static int executeCurrentRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[Bootstrap] This command can only be run by a player."));
            return 0;
        }

        List<RegionData> regions = RegionCache.all();

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "[Bootstrap] No bootstrap region data is currently loaded. Try reconnecting or running world bootstrap first."
            ), false);
            source.sendSuccess(() -> Component.literal("[Bootstrap] Last status: " + RegionCache.lastStatus()), false);
            return 0;
        }

        BlockPos pos = player.blockPosition();

        List<RegionData> matches = new ArrayList<>();
        for (RegionData region : regions) {
            if (region.contains(pos)) {
                matches.add(region);
            }
        }

        source.sendSuccess(() -> Component.literal(
            "[Bootstrap] Region lookup for " + player.getGameProfile().getName()
                + " at x=" + pos.getX()
                + " y=" + pos.getY()
                + " z=" + pos.getZ()
        ), false);

        source.sendSuccess(() -> Component.literal("[Bootstrap] Loaded regions: " + regions.size()), false);

        if (matches.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Bootstrap] No bootstrap region contains this position."), false);
            source.sendSuccess(() -> Component.literal("[Bootstrap] Climate fallback: "
                    + FarmingClimateResolver.resolveClimateName(player.level(), pos)), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("[Bootstrap] Matching regions: " + matches.size()), false);

        for (RegionData region : matches) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal(describeRegion(region)), false);
            source.sendSuccess(() -> Component.literal("    Climate: " + region.climate()), false);

            List<RegionItemData> items = itemsOf(region);

            if (items.isEmpty()) {
                source.sendSuccess(() -> Component.literal("    Items: none"), false);
                continue;
            }

            for (RegionItemData item : items) {
                source.sendSuccess(() -> Component.literal("    - " + describeRegionItem(item)), false);
            }
        }

        return matches.size();
    }

    private static String describeRegion(RegionData region) {
        int minX = Math.min(region.minX, region.maxX);
        int maxX = Math.max(region.minX, region.maxX);
        int minY = Math.min(region.minY, region.maxY);
        int maxY = Math.max(region.minY, region.maxY);
        int minZ = Math.min(region.minZ, region.maxZ);
        int maxZ = Math.max(region.minZ, region.maxZ);

        return "Region: " + region.name
            + " | Bounds: x=" + minX + ".." + maxX
            + ", y=" + minY + ".." + maxY
            + ", z=" + minZ + ".." + maxZ
            + " | Items: " + itemsOf(region).size();
    }

    private static String describeRegionItem(RegionItemData item) {
        return "type=" + item.type
            + " key=" + item.key
            + " weight=" + item.weight()
            + " minSkillOverride=" + item.minSkillOverride
            + " rarity=" + item.rarity;
    }

    private static List<RegionItemData> itemsOf(RegionData region) {
        return region.items == null ? List.of() : region.items;
    }
}
