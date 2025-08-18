package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.registry.PaintingRegistry;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.util.WeightedPicker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;


import com.seggellion.britannia_mod.util.FishCatalog;
import net.minecraft.core.registries.BuiltInRegistries;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items; // NEW: for leather boots
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.util.Mth;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Locale;

public class FishingEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TextColor GRAY_848484 = TextColor.fromRgb(0x848484);
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");

    // How many rerolls to try if the picked fish exceeds the player's skill
    private static final int REROLL_TRIES = 5;

    // NEW: chance to catch leather boots instead of a fish (e.g., 7%)
   /** Boot chance scales 0 skill -> 0.80, 100 skill -> 0.00 (clamped) */
    private static double bootChanceFor(float skill) {
        float s = Mth.clamp(skill, 0f, 100f);
        return 0.80 * (1.0 - (s / 100.0));
    }

    /** Painting chance scales 0 skill -> 0.00, 100 skill -> 0.50 (clamped) */
    private static double paintingChanceFor(float skill) {
        float s = Mth.clamp(skill, 0f, 100f);
        return 0.50 * (s / 100.0);
    }

    /** Pick a random painting itemstack from the registry, or EMPTY if none registered */
    private static ItemStack randomPaintingStack(RandomSource rng) {
        var values = PaintingRegistry.PAINTING_ITEMS.values(); // Collection<DeferredHolder<Item, Item>>
        if (values.isEmpty()) return ItemStack.EMPTY;

        int target = rng.nextInt(values.size());
        int i = 0;
        for (DeferredHolder<Item, Item> holder : values) {
            if (i++ == target) {
                Item item = holder.get();          // typed: Item
                return new ItemStack(item);        // OK: Item implements ItemLike
            }
        }
        return ItemStack.EMPTY; // should never hit
    }



    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        Level level   = player.level();
        ServerPlayer sp = (ServerPlayer) player;

        // Server side only
        if (level.isClientSide()) return;

        float skill  = SkillManager.getSkill(sp, "fishing");
        double chance = SkillManager.catchChance(skill);

        // Kill vanilla drops and mark handled
        event.getDrops().clear();
        event.setCanceled(true);

        // Attempt skill gain tick
        SkillManager.trySkillGain(sp, "fishing", true);

        // Global catch chance gate
        if (ThreadLocalRandom.current().nextDouble() > chance) {
            sendGray(player, ""); // spacer
            sendGray(player, "You fish for a while, but fail to catch anything.");
            return;
        }

        BlockPos pos = player.blockPosition();
        LOGGER.info("Fishing at pos: {}", pos);

        // Try for a painting first (rarer treasure that scales up with skill)
        // 0% at 0 skill, 50% at 100 skill
        double paintingChance = paintingChanceFor(skill);
        if (ThreadLocalRandom.current().nextDouble() < paintingChance) {
            ItemStack painting = randomPaintingStack(level.getRandom());
            if (!painting.isEmpty()) {
                LOGGER.info("🎣 Caught painting (chance={}): {}", 
                        String.format(Locale.ROOT, "%.2f%%", paintingChance * 100), 
                        painting.getDescriptionId());
                giveAndAnnounce(player, level, pos, painting);
                return;
            }
        }

        // Scaled junk chance (80% at 0 skill -> 0% at 100 skill)
        double bootChance = bootChanceFor(skill);
        if (ThreadLocalRandom.current().nextDouble() < bootChance) {
            ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
            if (ThreadLocalRandom.current().nextBoolean()) {
                boots.setDamageValue(ThreadLocalRandom.current().nextInt(boots.getMaxDamage()));
            }
            LOGGER.info("🎣 Caught junk: Leather Boots (chance={})", String.format(Locale.ROOT, "%.2f%%", bootChance * 100));
            giveAndAnnounce(player, level, pos, boots);
            return;
        }

        // Otherwise, try to catch a fish from the region pool
        ItemStack result = tryCatchFromRegion(player, sp, pos);
        if (result == null || result.isEmpty()) {
            sendGray(player, ""); // spacer
            sendGray(player, "You fish for a while, but fail to catch anything.");
            return;
        }

        LOGGER.info("RESULT: {}", result); // FIXED: proper slf4j formatting
        giveAndAnnounce(player, level, pos, result);
    }

    private void giveAndAnnounce(Player player, Level level, BlockPos pos, ItemStack stack) {
        announceCatch(player, stack);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level.playSound(null, pos, ModSounds.CATCH_FISH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Try to pick a region fish respecting min_skill and overrides. Returns null if none eligible. */
    private ItemStack tryCatchFromRegion(Player player, ServerPlayer sp, BlockPos pos) {
        List<RegionItemData> basePool = RegionCache.itemsFor(pos, "fish");
        LOGGER.info("FishPool size: {}", basePool.size());
        if (basePool.isEmpty()) return null;

        int playerSkill = (int) SkillManager.getSkill(sp, "fishing");

        // Try a few times to find an eligible fish
        for (int tries = 0; tries < REROLL_TRIES; tries++) {
            RegionItemData picked = WeightedPicker.pick(basePool);
            LOGGER.info("Picked: {}", picked);
            if (picked == null) break;

            int required = requiredSkillFor(picked);
            if (playerSkill < required) {
                if (tries == REROLL_TRIES - 1) {
                    sendGray(player, ""); // spacer
                    sendGray(player, "You lack the skill to catch that fish (required " + required + ").");
                }
                continue; // reroll
            }

            // Resolve namespaced item id -> Item
            ResourceLocation id = ResourceLocation.parse(picked.key);
            Item fishItem = BuiltInRegistries.ITEM.get(id);
            LOGGER.info("Resolved id: {}", id);

            if (fishItem == null) {
                LOGGER.info("Region fish item not found in registry: {}", picked.key);
                // try another fish
                continue;
            } else {
                // Require API metadata; if missing, skip
                FishCatalog.FishMeta meta = FishCatalog.get(id);
                if (meta == null) {
                    LOGGER.info("No API metadata for fish id: {}", id);
                    continue;
                }
                return makeWeightedFishStack(fishItem, meta);
            }
        }
        return null; // no eligible pick found
    }

    /** Determine required skill for a region-picked fish. */
    private static int requiredSkillFor(RegionItemData picked) {
        ResourceLocation id = ResourceLocation.parse(picked.key);
        FishCatalog.FishMeta meta = FishCatalog.get(id);
        int base = (meta != null) ? meta.minSkill : 0;
        return (picked.minSkillOverride != null) ? picked.minSkillOverride : base;
    }

    /** Build a weighted fish stack using API metadata only. */
    private ItemStack makeWeightedFishStack(Item fishItem, FishCatalog.FishMeta meta) {
        double minW = meta.minWeight;
        double maxW = meta.maxWeight;
        String  fishType = meta.name.toLowerCase(Locale.ROOT);

        double weight = generateRandomWeight(minW, maxW);
        ItemStack stack = new ItemStack(fishItem);

        if (stack.getItem() instanceof WeightedFishItem weighted) {
            weighted.setWeight(stack, weight);
            weighted.setFishType(stack, fishType);
        }

        LOGGER.info("🎣 Caught {} ({} stones)", fishType, String.format(Locale.ROOT, "%.2f", weight));
        return stack;
    }

    private double generateRandomWeight(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private void announceCatch(Player player, ItemStack stack) {
        // Item name (translated)
        String itemName = Component.translatable(stack.getDescriptionId()).getString();

        // Optional weight (only if this is one of your WeightedFishItem items)
        String weightText = "";
        if (stack.getItem() instanceof WeightedFishItem weighted) {
            double w = weighted.getWeight(stack); // assumes your class exposes this getter
            weightText = " " + String.format(Locale.ROOT, "%.2f", w) + " stones";
        }

        // Spacer + exact format you asked for:
        // "You pull out an item : {item.name} {item.weight}"
        sendGray(player, "");
        sendGray(player, "You pull out an item :  " + itemName + weightText);
    }

    private void sendGray(Player player, String msg) {
        Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(GRAY_848484);
        player.sendSystemMessage(Component.literal(msg).withStyle(style));
    }
}
