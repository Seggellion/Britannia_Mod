// com/seggellion/britannia_mod/commands/BootstrapCommands.java
package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.util.FishCatalog;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionData;
import com.seggellion.britannia_mod.util.RegionItemData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
        );
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
                String line = String.format(" - %s | name=%s | weight=%.2f..%.2f kg | min_skill=%d",
                        key, m.name, m.minWeight, m.maxWeight, m.minSkill);
                source.sendSuccess(() -> Component.literal(line), false);
            });

        source.sendSuccess(() -> Component.literal("[Bootstrap] Total fish: " + map.size()), false);
        return map.size();
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
