package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.event.ManagedVegetationInteractionHandler;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationLifecycle;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationManager;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationNode;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationProfile;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationSavedData;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.UUID;

/** Real server/event-bus coverage for managed vegetation ownership and replacement behavior. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ManagedVegetationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static int playerSequence;

    private ManagedVegetationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void successfulSingleAndMultiPlacementRelinquishesEveryClaimedPosition(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();

        BlockPos postCut = absolute(helper, 2, 3, 2);
        setUpShortGrass(level, postCut);
        placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(ManagedVegetationService.cutNode(placer, level, postCut),
                "creative cut did not enter regrowth");
        check(level.getBlockState(postCut).is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get()),
                "cut did not leave the regrowth controller");
        placeOnSubstrate(placer, postCut.below(), new ItemStack(Items.STONE));
        check(level.getBlockState(postCut).is(Blocks.STONE),
                "stone did not replace the post-cut controller");
        check(ManagedVegetationSavedData.get(level).nodeAt(postCut).isEmpty(),
                "post-cut controller replacement retained ownership");

        BlockPos shortGrass = absolute(helper, 6, 3, 2);
        setUpShortGrass(level, shortGrass);
        placeOnSubstrate(placer, shortGrass.below(), new ItemStack(Items.COBBLESTONE));
        check(level.getBlockState(shortGrass).is(Blocks.COBBLESTONE),
                "placement did not replace occupied managed short grass");
        check(ManagedVegetationSavedData.get(level).nodeAt(shortGrass).isEmpty(),
                "occupied short-grass replacement retained ownership");

        BlockPos tallGrass = absolute(helper, 10, 3, 2);
        setUpTallGrass(level, tallGrass);
        placeOnSubstrate(placer, tallGrass.below(), new ItemStack(Items.OAK_DOOR));
        check(level.getBlockState(tallGrass).is(Blocks.OAK_DOOR)
                        && level.getBlockState(tallGrass.above()).is(Blocks.OAK_DOOR),
                "multi-place door did not preserve both blocks through tall vegetation");
        check(ManagedVegetationSavedData.get(level).nodeAt(tallGrass).isEmpty(),
                "multi-place event retained the tall node");

        CompoundTag saved = ManagedVegetationSavedData.get(level).save(new CompoundTag(), null);
        ManagedVegetationSavedData reloaded = ManagedVegetationSavedData.load(saved, null);
        check(reloaded.nodeAt(postCut).isEmpty() && reloaded.nodeAt(shortGrass).isEmpty()
                        && reloaded.nodeAt(tallGrass).isEmpty(),
                "retired placement ownership returned after save/load");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void cancelledControllerReplacementRestoresTheOriginalNode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = absolute(helper, 3, 3, 3);
        setUpShortGrass(level, base);
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();
        placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(ManagedVegetationService.cutNode(placer, level, base), "fixture cut failed");
        ManagedVegetationNode original = ManagedVegetationSavedData.get(level).nodeAt(base).orElseThrow();

        CancelPlacement listener = new CancelPlacement(base);
        NeoForge.EVENT_BUS.register(listener);
        try {
            InteractionResult result = placeOnSubstrate(placer, base.below(), new ItemStack(Items.STONE));
            check(!result.consumesAction(), "cancelled placement reported success");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        helper.runAfterDelay(2, () -> {
            check(level.getBlockState(base).is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get()),
                    "cancelled placement did not restore the controller");
            check(ManagedVegetationSavedData.get(level).nodeAt(base).filter(original::equals).isPresent(),
                    "cancelled placement changed or retired the original node");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void staleSolidOwnershipDoesNotCancelBreakAndIsRetiredWithoutRetry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = absolute(helper, 4, 3, 4);
        setUpShortGrass(level, base);
        level.setBlock(base, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        check(ManagedVegetationService.resolveOwnedCutNode(level, base).isEmpty(),
                "stale metadata claimed an unrelated stone block");
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                level, base, level.getBlockState(base), helper.makeMockServerPlayerInLevel()
        );
        new ManagedVegetationInteractionHandler().onBreak(breakEvent);
        check(!breakEvent.isCanceled(), "stale metadata cancelled the stone's ordinary break path");
        check(ManagedVegetationService.forceTransition(level, base), "stale node could not be scheduled");

        helper.runAfterDelay(2, () -> {
            ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
            check(data.nodeAt(base).isEmpty(), "obstructed stale node was scheduled forever");
            check(data.pollDue(Long.MAX_VALUE, 8).stream().noneMatch(node -> node.position().equals(base)),
                    "retired stale node remained in the retry index");
            check(level.getBlockState(base).is(Blocks.STONE),
                    "reconciliation overwrote unrelated construction");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void britanniaFernSpawnsCutsRegrowsAndReconcilesAsTheOwnedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = absolute(helper, 5, 3, 5);
        setUpNode(level, base);
        check(ManagedVegetationManager.debugSpawn(level, base, ManagedVegetationProfile.FERN_ID),
                "debug fern spawn failed");
        check(level.getBlockState(base).is(BlockRegistry.FERN.get())
                        && !level.getBlockState(base).is(Blocks.FERN),
                "managed fern used the vanilla block");

        ServerPlayer survivor = player(level, GameType.SURVIVAL, 0);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check(ManagedVegetationService.cutNode(survivor, level, base), "custom fern sword cut failed");
        check(sword.getDamageValue() == 1, "non-admin fern cut did not cost one durability");
        check(level.getBlockState(base).is(BlockRegistry.MANAGED_VEGETATION_CONTROLLER.get()),
                "fern cut did not enter regrowth");

        check(ManagedVegetationManager.debugSpawn(level, base, ManagedVegetationProfile.FERN_ID),
                "custom fern did not regrow through the managed spawn path");
        level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(ManagedVegetationService.forceTransition(level, base), "fern reconciliation was not scheduled");

        BlockPos unmanaged = base.east(3);
        level.setBlock(unmanaged.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unmanaged, Blocks.FERN.defaultBlockState(), Block.UPDATE_ALL);
        check(ManagedVegetationService.resolveOwnedCutNode(level, unmanaged).isEmpty(),
                "unmanaged vanilla fern was claimed");
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            check(level.getBlockState(base).is(BlockRegistry.FERN.get()),
                    "reconciliation did not restore the Britannia fern");
            ManagedVegetationNode node = ManagedVegetationSavedData.get(level).nodeAt(base).orElseThrow();
            check(node.lifecycle() == ManagedVegetationLifecycle.FERN
                            && node.nextTransitionGameTime() == ManagedVegetationNode.NO_TRANSITION,
                    "reconciled fern retained a stale retry");
            placeOnSubstrate(placer, base.below(), new ItemStack(Items.STONE));
            check(level.getBlockState(base).is(Blocks.STONE),
                    "Britannia fern was not replaceable by ordinary construction");
            check(ManagedVegetationSavedData.get(level).nodeAt(base).isEmpty(),
                    "replacing the Britannia fern retained managed ownership");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serverCutTransactionEnforcesTheCompletePermissionAndDurabilityMatrix(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();

        ServerPlayer survival = player(level, GameType.SURVIVAL, 0);
        BlockPos survivalDenied = absolute(helper, 2, 3, 8);
        setUpShortGrass(level, survivalDenied);
        survival.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(!ManagedVegetationService.cutNode(survival, level, survivalDenied),
                "non-admin Survival bare hand was accepted");
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        check(!ManagedVegetationService.cutNode(survival, level, survivalDenied),
                "non-admin Survival arbitrary item was accepted");

        ItemStack survivalSword = new ItemStack(Items.IRON_SWORD);
        survival.setItemInHand(InteractionHand.MAIN_HAND, survivalSword);
        check(ManagedVegetationService.cutNode(survival, level, survivalDenied),
                "non-admin Survival sword was rejected");
        check(survivalSword.getDamageValue() == 1, "Survival sword cut did not cost durability");

        ServerPlayer adventure = player(level, GameType.ADVENTURE, 0);
        BlockPos adventureDenied = absolute(helper, 6, 3, 8);
        setUpShortGrass(level, adventureDenied);
        adventure.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(!ManagedVegetationService.cutNode(adventure, level, adventureDenied),
                "non-admin Adventure bare hand was accepted");
        adventure.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check(!ManagedVegetationService.cutNode(adventure, level, adventureDenied),
                "non-admin Adventure arbitrary item was accepted");
        ItemStack adventureSword = new ItemStack(Items.IRON_SWORD);
        adventure.setItemInHand(InteractionHand.MAIN_HAND, adventureSword);
        check(ManagedVegetationService.cutNode(adventure, level, adventureDenied),
                "non-admin Adventure sword was rejected");
        check(adventureSword.getDamageValue() == 1, "Adventure sword cut did not cost durability");

        ServerPlayer creative = player(level, GameType.CREATIVE, 0);
        BlockPos creativeHand = absolute(helper, 10, 3, 8);
        setUpShortGrass(level, creativeHand);
        creative.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(ManagedVegetationService.cutNode(creative, level, creativeHand),
                "Creative bare-hand cut was rejected");
        BlockPos creativeItem = absolute(helper, 12, 3, 8);
        setUpShortGrass(level, creativeItem);
        ItemStack creativePickaxe = new ItemStack(Items.IRON_PICKAXE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, creativePickaxe);
        check(ManagedVegetationService.cutNode(creative, level, creativeItem),
                "Creative arbitrary-item cut was rejected");
        check(creativePickaxe.getDamageValue() == 0, "Creative bypass damaged the held item");

        ServerPlayer admin = player(level, GameType.SURVIVAL, 2);
        BlockPos adminHand = absolute(helper, 2, 3, 12);
        setUpShortGrass(level, adminHand);
        admin.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(ManagedVegetationService.cutNode(admin, level, adminHand),
                "permission-level-2 Survival bare-hand cut was rejected");
        admin.setGameMode(GameType.ADVENTURE);
        BlockPos adminItem = absolute(helper, 6, 3, 12);
        setUpShortGrass(level, adminItem);
        ItemStack adminSword = new ItemStack(Items.IRON_SWORD);
        admin.setItemInHand(InteractionHand.MAIN_HAND, adminSword);
        check(ManagedVegetationService.cutNode(admin, level, adminItem),
                "permission-level-2 Adventure item cut was rejected");
        check(adminSword.getDamageValue() == 0, "administrator bypass damaged the held item");
        helper.succeed();
    }

    private static void setUpNode(ServerLevel level, BlockPos base) {
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        data.nodeAt(base).ifPresent(node -> data.remove(base));
        level.setBlock(base.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        for (int offset = 0; offset < 3; offset++) {
            level.setBlock(base.above(offset), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        check(ManagedVegetationService.registerNode(level, base), "managed node fixture registration failed");
    }

    private static void setUpShortGrass(ServerLevel level, BlockPos base) {
        setUpNode(level, base);
        check(ManagedVegetationManager.debugSpawn(level, base, ManagedVegetationProfile.GRASS_FAMILY_ID),
                "short-grass fixture spawn failed");
    }

    private static void setUpTallGrass(ServerLevel level, BlockPos base) {
        setUpShortGrass(level, base);
        ManagedVegetationSavedData data = ManagedVegetationSavedData.get(level);
        ManagedVegetationNode shortGrass = data.nodeAt(base).orElseThrow();
        data.update(shortGrass.tallGrass(shortGrass.vegetationEntryId().orElseThrow()));
        DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), base, Block.UPDATE_ALL);
        check(ManagedVegetationService.resolveOwnedCutNode(level, base.above()).isPresent(),
                "tall-grass fixture did not own both halves");
    }

    private static InteractionResult placeOnSubstrate(
            ServerPlayer player,
            BlockPos substrate,
            ItemStack stack
    ) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(substrate), Direction.UP, substrate, false
        );
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static ServerPlayer player(ServerLevel level, GameType gameType, int permissionLevel) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "vegtest" + playerSequence++
        );
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, profile, cookie.clientInformation()
        ) {
            @Override
            protected int getPermissionLevel() {
                return permissionLevel;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(gameType);
        return player;
    }

    private static BlockPos absolute(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class CancelPlacement {
        private final BlockPos position;

        private CancelPlacement(BlockPos position) {
            this.position = position;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void cancel(BlockEvent.EntityPlaceEvent event) {
            if (event.getPos().equals(position)) {
                event.setCanceled(true);
            }
        }
    }
}
