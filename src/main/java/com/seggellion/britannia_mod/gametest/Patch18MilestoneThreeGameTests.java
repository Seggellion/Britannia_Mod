package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.dirtgathering.DirtGatheringCooldown;
import com.seggellion.britannia_mod.dirtgathering.DirtGatheringPolicy;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18MilestoneThreeGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);
    private static final String NEXT_TICK_TAG = "britannia_mod:dirt_gather_next_tick";
    private static final String FEEDBACK_TICK_TAG = "britannia_mod:dirt_gather_feedback_tick";

    private Patch18MilestoneThreeGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void survivalGestureGrantsOncePreservesDirtAndDoesNotTrainMining(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-dirt-survival", GameType.SURVIVAL);
        BlockPos target = target(helper);
        helper.setBlock(TARGET, Blocks.DIRT);
        float miningBefore = SkillManager.getSkill(player, MiningSkill.SKILL_ID);
        ItemStack shovel = player.getMainHandItem();
        int damageBefore = shovel.getDamageValue();

        PlayerInteractEvent.RightClickBlock first = rightClick(player, InteractionHand.MAIN_HAND, target);
        check(first.isCanceled(), "valid dirt gathering gesture was not consumed");
        check(helper.getLevel().getBlockState(target).is(Blocks.DIRT),
                "dirt gathering changed the target block");
        check(totalDirt(helper.getLevel(), player, target) == 1,
                "one valid gather did not create exactly one custom dirt");
        check(shovel.getDamageValue() == damageBefore + 1,
                "successful gather did not charge exactly one shovel durability");
        check(SkillManager.getSkill(player, MiningSkill.SKILL_ID) == miningBefore,
                "dirt gathering changed Mining progression");

        ItemStack replacement = ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
        player.setItemInHand(InteractionHand.OFF_HAND, shovel);
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        PlayerInteractEvent.RightClickBlock repeated = rightClick(player, InteractionHand.MAIN_HAND, target);
        check(repeated.isCanceled(), "cooldown attempt fell through to vanilla shovel behavior");
        check(totalDirt(helper.getLevel(), player, target) == 1,
                "packet spam or a replacement shovel bypassed the player cooldown");
        check(replacement.getDamageValue() == 0, "cooldown refusal damaged the replacement shovel");
        check(helper.getLevel().getBlockState(target).is(Blocks.DIRT),
                "cooldown attempt flattened dirt into a path");

        expireCooldown(player);
        rightClick(player, InteractionHand.MAIN_HAND, target);
        check(totalDirt(helper.getLevel(), player, target) == 2,
                "gathering did not work again after the cooldown elapsed");
        check(replacement.getDamageValue() == 1,
                "post-cooldown success did not charge one durability");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void adventureWorksWhileCreativeAndFakePlayersCreateNoEconomy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        helper.setBlock(TARGET, Blocks.DIRT);

        ServerPlayer adventure = preparedPlayer(helper, "patch18-dirt-adventure", GameType.ADVENTURE);
        PlayerInteractEvent.RightClickBlock adventureClick =
                rightClick(adventure, InteractionHand.MAIN_HAND, target);
        check(adventureClick.isCanceled() && totalDirt(level, adventure, target) == 1,
                "Adventure player could not gather one dirt through the pre-useOn event");
        check(level.getBlockState(target).is(Blocks.DIRT), "Adventure gather changed terrain");

        ServerPlayer creative = preparedPlayer(helper, "patch18-dirt-creative", GameType.CREATIVE);
        ItemStack creativeShovel = creative.getMainHandItem();
        PlayerInteractEvent.RightClickBlock creativeClick =
                rightClick(creative, InteractionHand.MAIN_HAND, target);
        check(creativeClick.isCanceled(), "Creative exact gesture was allowed to fall through");
        check(totalDirt(level, creative, target) == 0, "Creative minted a custom dirt commodity");
        check(creativeShovel.getDamageValue() == 0, "Creative refusal damaged the shovel");

        FakePlayer machine = FakePlayerFactory.getMinecraft(level);
        machine.getInventory().clearContent();
        machine.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
        machine.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3));
        PlayerInteractEvent.RightClickBlock automated =
                rightClick(machine, InteractionHand.MAIN_HAND, target);
        check(automated.isCanceled(), "fake-player exact gesture fell through to shovel flattening");
        check(machine.getInventory().countItem(ItemRegistry.DIRT.get()) == 0,
                "fake player gathered custom dirt");
        check(level.getBlockState(target).is(Blocks.DIRT), "denied actors changed terrain");
        disconnect(adventure);
        disconnect(creative);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void wrongToolWrongTargetAndOffhandNeverGrantCustomDirt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        helper.setBlock(TARGET, Blocks.DIRT);
        ServerPlayer player = preparedPlayer(helper, "patch18-dirt-invalid", GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        check(!rightClick(player, InteractionHand.MAIN_HAND, target).isCanceled(),
                "vanilla shovel was claimed as the Britannia gather tool");
        check(totalDirt(level, player, target) == 0, "vanilla shovel granted custom dirt");

        player.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3));
        helper.setBlock(TARGET, Blocks.COARSE_DIRT);
        check(!rightClick(player, InteractionHand.MAIN_HAND, target).isCanceled(),
                "coarse dirt was incorrectly claimed as a gather target");
        check(totalDirt(level, player, target) == 0, "coarse dirt granted custom dirt");

        helper.setBlock(TARGET, Blocks.DIRT);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(
                InteractionHand.OFF_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3));
        check(!rightClick(player, InteractionHand.OFF_HAND, target).isCanceled(),
                "unsupported offhand gesture was claimed as a gather");
        check(totalDirt(level, player, target) == 0, "offhand shovel granted custom dirt");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void foreignHouseIsDeniedAndOwnerMayGather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        helper.setBlock(TARGET, Blocks.DIRT);
        ServerPlayer stranger = preparedPlayer(helper, "patch18-dirt-stranger", GameType.SURVIVAL);
        ServerPlayer owner = preparedPlayer(helper, "patch18-dirt-owner", GameType.SURVIVAL);
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 5, 6))));
        StructureRecord house = new StructureRecord(
                owner.getUUID(), box, box, UUID.randomUUID(),
                "small", "SMALL_BRICK", null, 0, level.dimension());
        StructureRegionManager.registerStructure(house);
        try {
            PlayerInteractEvent.RightClickBlock denied =
                    rightClick(stranger, InteractionHand.MAIN_HAND, target);
            check(denied.isCanceled(), "foreign-house gather attempt fell through");
            check(totalDirt(level, stranger, target) == 0,
                    "stranger gathered dirt from another player's house");
            check(stranger.getMainHandItem().getDamageValue() == 0,
                    "protected-house refusal damaged the shovel");

            DirtGatheringPolicy.Assessment ownerPermission =
                    DirtGatheringPolicy.evaluate(level, target, owner);
            check(ownerPermission.allowed(),
                    "house owner failed dirt-gather permission: " + ownerPermission.decision());
            PlayerInteractEvent.RightClickBlock allowed =
                    rightClick(owner, InteractionHand.MAIN_HAND, target);
            check(allowed.isCanceled(), "house owner's exact dirt gesture was not consumed");
            check(totalDirt(level, owner, target) == 1,
                    "house owner dirt output count was " + totalDirt(level, owner, target));
            check(level.getBlockState(target).is(Blocks.DIRT),
                    "house permission path changed the target dirt");
        } finally {
            StructureRegionManager.unregisterStructure(house);
            disconnect(stranger);
            disconnect(owner);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void fullInventoryDropsExactlyOneAndPlayersHaveIndependentCooldowns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        helper.setBlock(TARGET, Blocks.DIRT);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(4.0D))
                .forEach(ItemEntity::discard);

        ServerPlayer full = preparedPlayer(helper, "patch18-dirt-full", GameType.SURVIVAL);
        for (int slot = 0; slot < 36; slot++) {
            full.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        full.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3));
        rightClick(full, InteractionHand.MAIN_HAND, target);
        check(totalDirt(level, full, target) == 1,
                "full inventory deleted or duplicated the gather output");

        ServerPlayer second = preparedPlayer(helper, "patch18-dirt-second", GameType.SURVIVAL);
        rightClick(second, InteractionHand.MAIN_HAND, target);
        check(second.getInventory().countItem(ItemRegistry.DIRT.get()) == 1,
                "one player's cooldown blocked a different player at the same dirt patch");
        check(level.getBlockState(target).is(Blocks.DIRT), "multiplayer gathers changed terrain");
        disconnect(full);
        disconnect(second);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void playerCloneKeepsTheCooldownAndFeedbackThrottle(GameTestHelper helper) {
        ServerPlayer original = preparedPlayer(helper, "patch18-dirt-clone-old", GameType.SURVIVAL);
        long next = original.server.overworld().getGameTime() + DirtGatheringCooldown.COOLDOWN_TICKS;
        original.getPersistentData().putLong(NEXT_TICK_TAG, next);
        original.getPersistentData().putLong(FEEDBACK_TICK_TAG, next - 100L);
        ServerPlayer clone = preparedPlayer(helper, "patch18-dirt-clone-new", GameType.SURVIVAL);
        clone.getPersistentData().remove(NEXT_TICK_TAG);
        clone.getPersistentData().remove(FEEDBACK_TICK_TAG);

        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, original, true));

        check(clone.getPersistentData().getLong(NEXT_TICK_TAG) == next,
                "death clone lost the player-scoped gather cooldown");
        check(clone.getPersistentData().getLong(FEEDBACK_TICK_TAG) == next - 100L,
                "death clone lost the feedback throttle state");
        disconnect(original);
        disconnect(clone);
        helper.succeed();
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper, String name, GameType mode) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.getPersistentData().remove(NEXT_TICK_TAG);
        player.getPersistentData().remove(FEEDBACK_TICK_TAG);
        player.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3));
        player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
        return player;
    }

    private static BlockPos target(GameTestHelper helper) {
        return helper.absolutePos(TARGET);
    }

    private static void expireCooldown(ServerPlayer player) {
        player.getPersistentData().putLong(
                NEXT_TICK_TAG, player.server.overworld().getGameTime());
    }

    private static PlayerInteractEvent.RightClickBlock rightClick(
            ServerPlayer player, InteractionHand hand, BlockPos target
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target).add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                target,
                false);
        return CommonHooks.onRightClickBlock(player, hand, target, hit);
    }

    private static int totalDirt(ServerLevel level, ServerPlayer player, BlockPos target) {
        int inventory = player.getInventory().countItem(ItemRegistry.DIRT.get());
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(4.0D)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.DIRT.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        return inventory + dropped;
    }

    private static void disconnect(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
