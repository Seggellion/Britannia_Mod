package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.BlacksmithItemData;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.CraftBlacksmithItemC2SPayload;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import com.seggellion.britannia_mod.skill.BlacksmithSessionManager;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.training.TrainingDummyService;
import com.seggellion.britannia_mod.training.TrainingWeaponClassifier;
import com.seggellion.britannia_mod.training.TrainingWeaponSkill;
import com.seggellion.britannia_mod.wildresource.DaggerTools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18WeaponGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "patch18_weapons";
    private static final List<String> IDS = List.of("dagger", "viking_sword", "katana", "rapier", "halberd", "decorative_shield");

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static ServerPlayer smith(GameTestHelper helper, float skill) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), "weapon-smith");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 1));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.BLACKSMITH_HAMMER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 64));
        SkillManager.applyConfirmedValue(player, "blacksmithy", skill);
        return player;
    }

    private static void payload(ServerPlayer player, CraftBlacksmithItemC2SPayload payload) {
        NetworkHandler.handleCraftBlacksmithItem(payload,
                new ServerPayloadContext(player.connection, CraftBlacksmithItemC2SPayload.TYPE_ID));
    }

    private static void craft(ServerPlayer player, String id) {
        payload(player, new CraftBlacksmithItemC2SPayload(id, BlacksmithSessionManager.open(player)));
    }

    private static ItemStack output(ServerPlayer player, String id) {
        var item = BuiltInRegistries.ITEM.get(CraftableRegistry.get(id).resultItem());
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) return stack;
        return ItemStack.EMPTY;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void everyWeaponCraftsThroughThePacketHandlerAndKeepsItsLifecycle(GameTestHelper helper) {
        List<ServerPlayer> players = new ArrayList<>();
        List<String> identities = new ArrayList<>();
        for (String id : IDS) {
            ServerPlayer player = smith(helper, 100);
            craft(player, id); // All six minima are <50: both rolls succeed deterministically at 100.
            ItemStack stack = output(player, id);
            check(!stack.isEmpty() && stack.getCount() == 1, id + " must create exactly one registered item");
            int cost = BlacksmithCrafting.primaryMaterialCount(CraftableRegistry.get(id));
            check(player.getOffhandItem().getCount() == 64 - cost, id + " consumed the wrong amount");
            check(stack.getMaxStackSize() == 1 && stack.isDamageableItem(), id + " must be nonstacking equipment");
            check(id.equals(BlacksmithItemData.recipeId(stack)), id + " lost its recipe identity");
            check("iron".equals(BlacksmithItemData.materialId(stack)), id + " lost its selected material");
            check(stack.getHoverName().getString().startsWith("Exceptional "), id + " quality roll was not applied");
            check(BlacksmithItemData.isSupportedEquipment(stack), id + " must support repair and smelting");
            if (stack.getItem() instanceof QualitySwordItem sword) {
                check(stack.getMaxDamage() == 250 && stack.getDamageValue() == 0, id + " starts worn");
                sword.postHurtEnemy(stack, player, player);
                check(stack.getDamageValue() == 1, id + " combat must consume one durability");
                check(stack.is(ItemTags.SWORDS), id + " missing sword tag");
            } else {
                check(stack.getMaxDamage() == 65 && stack.getMaxDamage() - stack.getDamageValue() >= 50,
                        "shield must use the existing 50-65 initial durability convention");
            }
            String token = BlacksmithItemData.identityToken(stack);
            identities.add(token);
            stack.setDamageValue(20);
            player.getInventory().removeItemNoUpdate(player.getInventory().findSlotMatchingItem(stack));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            players.add(player);
        }
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < IDS.size(); i++) {
                ServerPlayer player = players.get(i);
                int cost = BlacksmithCrafting.primaryMaterialCount(CraftableRegistry.get(IDS.get(i)));
                payload(player, CraftBlacksmithItemC2SPayload.repair(identities.get(i), BlacksmithSessionManager.open(player)));
                check(player.getMainHandItem().getDamageValue() == 0, IDS.get(i) + " repair did not reset wear");
                check(identities.get(i).equals(BlacksmithItemData.identityToken(player.getMainHandItem())), "repair replaced identity");
                check(player.getOffhandItem().getCount() == 64 - cost - BlacksmithCrafting.repairCost(cost), "wrong repair cost");
            }
            helper.runAfterDelay(2, () -> {
                for (int i = 0; i < IDS.size(); i++) {
                    ServerPlayer player = players.get(i);
                    payload(player, CraftBlacksmithItemC2SPayload.smelt(identities.get(i), BlacksmithSessionManager.open(player)));
                    check(player.getMainHandItem().isEmpty() || player.getMainHandItem().is(Items.IRON_INGOT), "smelt retained equipment");
                    int cost = BlacksmithCrafting.primaryMaterialCount(CraftableRegistry.get(IDS.get(i)));
                    check(player.getInventory().countItem(Items.IRON_INGOT) == 64 - cost
                            - BlacksmithCrafting.repairCost(cost) + BlacksmithCrafting.smeltRecovery(cost), "incorrect recovery");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void everyWeaponRejectsInsufficientResourcesAndSkillBeforeConsumption(GameTestHelper helper) {
        for (String id : IDS) {
            var def = CraftableRegistry.get(id);
            int cost = BlacksmithCrafting.primaryMaterialCount(def);
            ServerPlayer shortMetal = smith(helper, 100);
            shortMetal.getOffhandItem().setCount(cost - 1);
            craft(shortMetal, id);
            check(shortMetal.getOffhandItem().getCount() == cost - 1 && output(shortMetal, id).isEmpty(), id + " accepted short metal");
            if (def.minimumBlacksmithy() > 0) {
                ServerPlayer lowSkill = smith(helper, def.minimumBlacksmithy() - 0.1f);
                craft(lowSkill, id);
                check(lowSkill.getOffhandItem().getCount() == 64 && output(lowSkill, id).isEmpty(), id + " accepted low skill");
            }
            ServerPlayer lowMetalSkill = smith(helper, 50);
            lowMetalSkill.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.VALORITE_INGOT.get(), 64));
            craft(lowMetalSkill, id);
            check(lowMetalSkill.getOffhandItem().getCount() == 64 && output(lowMetalSkill, id).isEmpty(), id + " bypassed metal skill");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void failedRollConsumesOnlyOneRecipeAndCreatesNothing(GameTestHelper helper) {
        for (String id : IDS) {
            ServerPlayer player = smith(helper, CraftableRegistry.get(id).minimumBlacksmithy());
            // Seed the actual player RNG so its next roll fails the 50% threshold.
            long seed = 0;
            do { player.getRandom().setSeed(seed++); } while (player.getRandom().nextDouble() < 0.5);
            player.getRandom().setSeed(seed - 1);
            craft(player, id);
            check(output(player, id).isEmpty(), id + " failed roll unexpectedly created output");
            check(player.getOffhandItem().getCount() == 64 - BlacksmithCrafting.primaryMaterialCount(CraftableRegistry.get(id)),
                    id + " failure consumption differs from the established recipe");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void serverRejectsForgedSessionsMissingToolsAndDuplicateRequests(GameTestHelper helper) {
        for (String id : IDS) {
            ServerPlayer player = smith(helper, 100);
            payload(player, new CraftBlacksmithItemC2SPayload(id, "forged"));
            check(player.getOffhandItem().getCount() == 64 && output(player, id).isEmpty(), "forged session crafted " + id);
            payload(player, new CraftBlacksmithItemC2SPayload("missing_weapon", BlacksmithSessionManager.open(player)));
            check(player.getOffhandItem().getCount() == 64, "unknown recipe consumed material");
            craft(player, id);
            craft(player, id);
            check(player.getOffhandItem().getCount() == 64 - BlacksmithCrafting.primaryMaterialCount(CraftableRegistry.get(id)),
                    "duplicate packet crafted twice: " + id);
            ServerPlayer noHammer = smith(helper, 100);
            noHammer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            craft(noHammer, id);
            check(noHammer.getOffhandItem().getCount() == 64 && output(noHammer, id).isEmpty(), "missing hammer crafted " + id);
            ServerPlayer farAway = smith(helper, 100);
            farAway.teleportTo(0, 250, 0);
            craft(farAway, id);
            check(farAway.getOffhandItem().getCount() == 64 && output(farAway, id).isEmpty(), "remote craft accepted " + id);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void weaponsHaveCombatAttributesAndTrainTheirExistingSkills(GameTestHelper helper) {
        for (String id : IDS.subList(0, 5)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(CraftableRegistry.get(id).resultItem()));
            ServerPlayer player = smith(helper, 100);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            double[] damage = {0};
            double[] speed = {0};
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += modifier.amount();
                if (attribute.equals(Attributes.ATTACK_SPEED)) speed[0] += modifier.amount();
            });
            check(damage[0] == 5 && Math.abs(speed[0] + 2.4) < 0.0001, id + " lacks iron-sword combat attributes");
            QualitySwordItem.setQuality(stack, 2);
            double[] exceptionalDamage = {0};
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) exceptionalDamage[0] += modifier.amount();
            });
            check(exceptionalDamage[0] == 6, "quality must affect attack damage, not wear");
            TrainingWeaponSkill expected = id.equals("dagger") || id.equals("rapier") || id.equals("halberd")
                    ? TrainingWeaponSkill.FENCING : TrainingWeaponSkill.SWORDSMANSHIP;
            check(TrainingWeaponClassifier.classify(stack).orElseThrow() == expected, id + " changed weapon discipline");
            check(TrainingDummyService.attempt(player, stack).accepted(), id + " cannot train");
            check(DaggerTools.isDagger(stack) == id.equals("dagger"), "oyster dagger identity changed");
            ItemStack legacy = WeaponRegistry.createWeapon(stack.getItem(), UOMetalToolMaterial.VALORITE, 4);
            legacy.setDamageValue(73);
            check(legacy.getDamageValue() == 73 && QualitySwordItem.getQuality(legacy) == 4, "legacy quality or wear corrupted");
            ItemStack oldCatalogueStack = new ItemStack(stack.getItem());
            BlacksmithItemData.apply(oldCatalogueStack, UOMetalToolMaterial.COPPER, 2, "old", "Old", id);
            oldCatalogueStack.setDamageValue(83);
            check(QualitySwordItem.getQuality(oldCatalogueStack) == 2
                    && QualitySwordItem.getMaterial(oldCatalogueStack).equals("copper")
                    && oldCatalogueStack.getDamageValue() == 83, "promoted catalogue stack lost material, quality or wear");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void decorativeShieldUsesActualBlockingAndWear(GameTestHelper helper) {
        ServerPlayer player = smith(helper, 100);
        ItemStack shield = new ItemStack(WeaponRegistry.DECORATIVE_SHIELD.get());
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        // Embedded test connections do not run ServerGamePacketListenerImpl.tick(),
        // which normally calls doTick(). Drive the real player ticks explicitly.
        for (int tick = 0; tick < 60; tick++) player.tick(); // end login damage immunity
        shield.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        check(shield.getUseAnimation() == UseAnim.BLOCK && player.isUsingItem(), "shield did not start blocking");
        for (int tick = 0; tick < 6; tick++) player.doTick();
        check(player.isBlocking(), "shield did not become an active block after five player ticks");
        var attacker = EntityType.ZOMBIE.create(helper.getLevel());
        check(attacker != null, "missing test attacker");
        player.setYRot(0);
        attacker.setPos(player.getX(), player.getY(), player.getZ() + 2);
        float health = player.getHealth();
        player.hurt(player.damageSources().mobAttack(attacker), 6);
        check(player.getHealth() == health, "frontal attack bypassed shield");
        check(shield.getDamageValue() == 7, "blocked attack must consume 1 + floor(damage) shield durability");
        check(!shield.getItem().isValidRepairItem(shield, new ItemStack(Items.OAK_PLANKS)), "shield accepts vanilla plank repair");
        helper.succeed();
    }
}
