package com.seggellion.britannia_mod.skill;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.item.BlackSmithsHammerItem;
import com.seggellion.britannia_mod.item.BlacksmithItemData;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.registry.BlacksmithItemRegistry;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.util.LocalRecipes;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.IngredientRequirement;
import com.seggellion.britannia_mod.skill.crafting.ShieldProfileRegistry;
import com.seggellion.britannia_mod.skill.crafting.SkillRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative blacksmith transaction service. */
public final class BlacksmithCrafting {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRIMARY_SKILL_ID = "blacksmithy";
    private static final Map<UUID, Long> LAST_ATTEMPT_TICK = new HashMap<>();

    private BlacksmithCrafting() {}

    public static void processCraftRequest(ServerPlayer player, CraftableDef def) {
        if (!acceptAttempt(player)) return;

        if (!(player.getMainHandItem().getItem() instanceof BlackSmithsHammerItem) || !hasNearbyAnvil(player)) {
            reject(player, "You must use a blacksmith's hammer at a nearby anvil.");
            return;
        }

        float minimum = def.minimumBlacksmithy();
        float current = SkillManager.getSkill(player, PRIMARY_SKILL_ID);
        for (SkillRequirement requirement : def.skillRequirements()) {
            if (SkillManager.getSkill(player, requirement.skillKey()) < requirement.minValue()) {
                reject(player, "Insufficient " + requirement.skillKey() + " skill.");
                return;
            }
        }
        if (def.genderRestriction() != null
                && !def.genderRestriction().equalsIgnoreCase(PlayerDataStore.get(player).getGender())) {
            reject(player, "This item is restricted to " + def.genderRestriction() + " characters.");
            return;
        }
        if (def.raceRestriction() != null && !def.raceRestriction().equalsIgnoreCase(playerRace(player))) {
            reject(player, "This item is restricted to " + def.raceRestriction() + " characters.");
            return;
        }
        if (!LocalRecipes.hasLearned(player, def.learnedRecipeKey())) {
            reject(player, "You have not learned this recipe.");
            return;
        }

        ItemStack metalStack = player.getOffhandItem();
        UOMetalToolMaterial material = UOMetalToolMaterial.getMaterialByIngot(metalStack.getItem());
        if (material == null) {
            reject(player, "Hold one supported metal type in your offhand.");
            return;
        }

        Item output = BuiltInRegistries.ITEM.get(def.resultItem());
        if (output == Items.AIR) {
            LOGGER.error("Blacksmith output is not registered: {}", def.resultItem());
            reject(player, "That blacksmithing output is unavailable.");
            return;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            reject(player, "Make room in your inventory before crafting.");
            return;
        }
        if (!hasIngredients(player, def, metalStack)) {
            reject(player, "You do not have all required resources.");
            return;
        }

        // Existing LocalRecipe failure convention loses the required resources.
        int batches = def.batchCrafting() ? maximumBatches(player, def, metalStack) : 1;
        consumeIngredients(player, def, metalStack, batches);
        double successChance = clamp(0.50 + ((current - minimum) / 100.0), 0.05, 1.0);
        boolean success = player.getRandom().nextDouble() < successChance;
        SkillManager.trySkillGain(player, PRIMARY_SKILL_ID, success); // exactly once per accepted attempt
        if (!success) {
            reject(player, "You failed to create the item and lost the resources.");
            return;
        }

        double exceptionalChance = clamp((current - minimum) / 50.0, 0.01, 1.0);
        boolean exceptional = player.getRandom().nextDouble() < exceptionalChance;
        if (def.exceptionalOnly() && !exceptional) {
            reject(player, "The result was not precise enough for this recipe.");
            return;
        }

        int quality = exceptional ? 2 : 1;
        ItemStack result = WeaponRegistry.createWeapon(output, material, quality);
        result.setCount(Math.min(result.getMaxStackSize(), def.outputCount() * batches));
        RegionCache.findRegion(player.blockPosition()).ifPresentOrElse(
                region -> BlacksmithItemData.apply(result, material, quality,
                        stableRegionId(region.name), region.name, def.id()),
                () -> BlacksmithItemData.apply(result, material, quality, "unknown", "Unknown", def.id()));

        ShieldProfileRegistry.ShieldProfile shield = ShieldProfileRegistry.get(def.shieldProfileId());
        if (shield != null && result.isDamageableItem()) {
            int initial = shield.minimumDurability()
                    + player.getRandom().nextInt(shield.maximumDurability() - shield.minimumDurability() + 1);
            result.setDamageValue(result.getMaxDamage() - initial);
        }
        if (exceptional) {
            result.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Exceptional " + def.displayName()).withStyle(ChatFormatting.AQUA));
        }
        // Maker data is deliberately not client-authored. A future prompt may call applyMaker on this owned result.
        ItemHandlerHelper.giveItemToPlayer(player, result);
        player.sendSystemMessage(Component.literal("You successfully crafted " + def.displayName() + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    public static void processRepairRequest(ServerPlayer player, String targetToken) {
        if (!acceptAttempt(player)) return;
        ItemStack target = player.getMainHandItem();
        CraftableDef definition = validateTarget(player, target, targetToken, true);
        if (definition == null) return;
        if (!target.isDamageableItem() || !target.isDamaged()) {
            reject(player, "That item does not need repair.");
            return;
        }
        int original = primaryMaterialCount(definition);
        if (original <= 0) {
            reject(player, "This item's original material cannot be safely resolved.");
            return;
        }
        UOMetalToolMaterial material = storedMaterial(target);
        if (material == null) {
            reject(player, "This item's crafted material cannot be safely resolved.");
            return;
        }
        int cost = repairCost(original);
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() != material.getIngotSupplier().get() || offhand.getCount() < cost) {
            reject(player, "Repair requires " + cost + " matching ingots in your offhand.");
            return;
        }
        if (SkillManager.getSkill(player, PRIMARY_SKILL_ID) < definition.minimumBlacksmithy()) {
            reject(player, "Insufficient Blacksmithy skill to repair this item.");
            return;
        }
        offhand.shrink(cost);
        target.setDamageValue(0); // preserves the same stack and every permanent data component
        SkillManager.trySkillGain(player, PRIMARY_SKILL_ID, true);
        player.sendSystemMessage(Component.literal("The item has been repaired.").withStyle(ChatFormatting.GREEN));
    }

    public static void processSmeltRequest(ServerPlayer player, String targetToken) {
        if (!acceptAttempt(player)) return;
        ItemStack target = player.getMainHandItem();
        CraftableDef definition = validateTarget(player, target, targetToken, false);
        if (definition == null) return;
        int original = primaryMaterialCount(definition);
        int recovered = smeltRecovery(original);
        UOMetalToolMaterial material = storedMaterial(target);
        if (original <= 0 || recovered <= 0 || material == null) {
            reject(player, "This item's original material cannot be safely recovered.");
            return;
        }
        // Consume the exact validated target before creating recovery output.
        target.shrink(1);
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(material.getIngotSupplier().get(), recovered));
        player.sendSystemMessage(Component.literal("Recovered " + recovered + " " + material.getMetalName() + " ingots.")
                .withStyle(ChatFormatting.GREEN));
    }

    public static int repairCost(int originalPrimaryMaterial) {
        return (originalPrimaryMaterial + 1) / 2;
    }

    public static int smeltRecovery(int originalPrimaryMaterial) {
        return originalPrimaryMaterial / 2;
    }

    public static int primaryMaterialCount(CraftableDef definition) {
        return definition.ingredients().stream().filter(IngredientRequirement::selectableMetal)
                .mapToInt(IngredientRequirement::amount).findFirst().orElse(0);
    }

    private static CraftableDef validateTarget(ServerPlayer player, ItemStack target, String targetToken, boolean repair) {
        if (!hasNearbyAnvil(player)) {
            reject(player, "You must be at an anvil.");
            return null;
        }
        if (target.isEmpty() || targetToken == null || targetToken.isBlank()
                || !targetToken.equals(BlacksmithItemData.identityToken(target))) {
            reject(player, "The selected item changed; the action was cancelled.");
            return null;
        }
        String recipeId = BlacksmithItemData.recipeId(target);
        CraftableDef definition = recipeId == null ? null : com.seggellion.britannia_mod.skill.crafting.CraftableRegistry.get(recipeId);
        if (definition == null || !definition.recyclable() || !BlacksmithItemData.isSupportedEquipment(target)) {
            reject(player, repair ? "That item cannot be repaired." : "That item cannot be smelted.");
            return null;
        }
        return definition;
    }

    private static UOMetalToolMaterial storedMaterial(ItemStack stack) {
        String id = BlacksmithItemData.materialId(stack);
        return id == null ? null : UOMetalToolMaterial.getMaterialByName(id.replace('_', ' '));
    }

    private static boolean acceptAttempt(ServerPlayer player) {
        long tick = player.serverLevel().getGameTime();
        Long previous = LAST_ATTEMPT_TICK.put(player.getUUID(), tick);
        return previous == null || previous != tick;
    }

    private static String playerRace(ServerPlayer player) {
        var stats = PlayerDataStore.get(player).getStats();
        return stats.has("race") ? stats.get("race").getAsString() : "human";
    }

    private static boolean hasNearbyAnvil(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (player.serverLevel().getBlockState(pos).is(Blocks.ANVIL)
                    || player.serverLevel().getBlockState(pos).is(Blocks.CHIPPED_ANVIL)
                    || player.serverLevel().getBlockState(pos).is(Blocks.DAMAGED_ANVIL)) return true;
        }
        return false;
    }

    private static boolean hasIngredients(ServerPlayer player, CraftableDef def, ItemStack metalStack) {
        for (IngredientRequirement requirement : def.ingredients()) {
            if (requirement.selectableMetal()) {
                if (metalStack.getCount() < requirement.amount()) return false;
            } else if (count(player, BlacksmithItemRegistry.ingredientItem(requirement.materialKey())) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeIngredients(ServerPlayer player, CraftableDef def, ItemStack metalStack, int batches) {
        for (IngredientRequirement requirement : def.ingredients()) {
            int amount = requirement.amount() * batches;
            if (requirement.selectableMetal()) metalStack.shrink(amount);
            else consume(player, BlacksmithItemRegistry.ingredientItem(requirement.materialKey()), amount);
        }
    }

    private static int maximumBatches(ServerPlayer player, CraftableDef def, ItemStack metalStack) {
        int batches = 64;
        for (IngredientRequirement requirement : def.ingredients()) {
            int available = requirement.selectableMetal() ? metalStack.getCount()
                    : count(player, BlacksmithItemRegistry.ingredientItem(requirement.materialKey()));
            batches = Math.min(batches, available / requirement.amount());
        }
        return Math.max(1, batches);
    }

    private static int count(ServerPlayer player, Item item) {
        if (item == Items.AIR) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void consume(ServerPlayer player, Item item, int amount) {
        for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
    }

    private static String stableRegionId(String name) {
        return name == null || name.isBlank() ? "unknown"
                : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void reject(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
