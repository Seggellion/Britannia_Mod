package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.structure.interaction.DisplayCaseDecoratorService;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayDisplayCaseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(template=TEMPLATE, timeoutTicks=200)
    public static void allNeighborhoodsCellsSneakingAndMainItemsConserveExactMerchandise(GameTestHelper h) {
        var level=h.getLevel(); var root=h.absolutePos(new BlockPos(3,2,3)); var block=BlockRegistry.DISPLAY_CASE.get();
        ServerPlayer player=ManagedResourceTestPlayers.survival(level,"CaseGestures");
        player.setPos(root.getX()+.5,root.getY(),root.getZ()-2);
        try {
            Item[] mains={Items.AIR, Items.STICK, Items.APPLE, Items.STONE, ItemRegistry.INTERIOR_DECORATOR_TOOL.get()};
            for(int mask=0;mask<16;mask++) {
                prepare(h,root,mask);
                for(boolean upper:new boolean[]{false,true}) for(boolean sneak:new boolean[]{false,true}) for(Item main:mains) {
                    player.getInventory().clearContent();
                    for(int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(main));
                    player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
                    player.setShiftKeyDown(sneak);
                    var display=(DisplayCaseBlockEntity)level.getBlockEntity(root);
                    ItemStack expected=merchandise(); load(h,display,expected);
                    var replicas = new DisplayCaseBlockEntity[]{
                            new DisplayCaseBlockEntity(root, level.getBlockState(root)),
                            new DisplayCaseBlockEntity(root, level.getBlockState(root))};
                    syncPacket(h, display, replicas);
                    h.assertTrue(level.getBlockEntity(root.above()) == null, "upper cell acquired independent storage");
                    var before=level.getBlockState(root); var above=level.getBlockState(root.above());
                    BlockHitResult hit=hit(upper?root.above():root);
                    use(player,InteractionHand.OFF_HAND,hit); use(player,InteractionHand.MAIN_HAND,hit); use(player,InteractionHand.OFF_HAND,hit);
                    syncPacket(h, display, replicas);
                    var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(2.5));
                    if(main==ItemRegistry.INTERIOR_DECORATOR_TOOL.get()) {
                        h.assertTrue(drops.isEmpty() && ItemStack.matches(expected,display.displayedItem()),"both tools ejected or lost merchandise");
                        h.assertTrue(level.getBlockState(root).equals(before.setValue(DisplayCaseBlock.FACING,before.getValue(DisplayCaseBlock.FACING).getClockWise()))
                            && level.getBlockState(root.above()).equals(above.setValue(DisplayCaseBlock.FACING,before.getValue(DisplayCaseBlock.FACING).getClockWise())),"rotation was not atomic");
                    } else {
                        h.assertTrue(drops.size()==1 && ItemStack.matches(expected,drops.getFirst().getItem()),"ejection not exactly one full-component legacy stack: "+mask+"/"+upper+"/"+sneak+"/"+main);
                        h.assertTrue(!display.hasDisplayedItem() && level.getBlockState(root).equals(before) && level.getBlockState(root.above()).equals(above),"offhand action changed structure");
                        h.assertTrue(level.noCollision(drops.getFirst(),drops.getFirst().getBoundingBox()),"drop collides with case");
                        use(player,InteractionHand.MAIN_HAND,hit);
                        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(2.5)).size()==1,"empty repeat duplicated merchandise");
                        drops.forEach(ItemEntity::discard);
                        h.assertTrue(display.storeOne(new ItemStack(Items.EMERALD)), "empty case refused replacement");
                        syncPacket(h, display, replicas);
                        h.assertTrue(replicas[0].displayedItem().is(Items.EMERALD)
                                && replicas[1].displayedItem().is(Items.EMERALD), "old item survived replacement packet");
                        display.takeDisplayedItem();
                    }
                    h.assertTrue(player.getMainHandItem().getItem()==main && player.getOffhandItem().is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()),"decorator action mutated hands");
                    for(int slot=1;slot<36;slot++) h.assertTrue(player.getInventory().getItem(slot).getCount()==64,"full inventory was used for ejection");
                }
            }
        } finally { player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void rejectedSpawnsAndReentryRetainStorageThenTwoPlayersCanEjectOnlyOnce(GameTestHelper h) {
        var level=h.getLevel(); var root=h.absolutePos(new BlockPos(3,2,3)); prepare(h,root,0);
        var first=ManagedResourceTestPlayers.survival(level,"CaseFirst"); var second=ManagedResourceTestPlayers.survival(level,"CaseSecond");
        for(var player:new ServerPlayer[]{first,second}) { player.setPos(root.getX(),root.getY(),root.getZ()-2); player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())); }
        var display=(DisplayCaseBlockEntity)level.getBlockEntity(root); var expected=merchandise(); load(h,display,expected);
        java.util.function.Consumer<EntityJoinLevelEvent> reject=event->{
            if(event.getLevel()==level && event.getEntity() instanceof ItemEntity item && ItemStack.matches(item.getItem(),expected)) {
                use(second,InteractionHand.MAIN_HAND,hit(root.above()));
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(reject);
        try {
            use(first,InteractionHand.MAIN_HAND,hit(root));
            h.assertTrue(ItemStack.matches(expected,display.displayedItem()),"spawn rejection consumed storage");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(2.5)).isEmpty(),"reentrant spawn duplicated merchandise");
            var copy=new DisplayCaseBlockEntity(root,level.getBlockState(root));
            copy.loadWithComponents(display.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
            h.assertTrue(ItemStack.matches(expected,copy.displayedItem()),"failure did not persist");
            syncPacket(h, display, copy);
        } finally { NeoForge.EVENT_BUS.unregister(reject); }
        try {
            use(first,InteractionHand.MAIN_HAND,hit(root)); use(second,InteractionHand.MAIN_HAND,hit(root.above()));
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(2.5));
            h.assertTrue(drops.size()==1 && ItemStack.matches(expected,drops.getFirst().getItem()) && !display.hasDisplayedItem(),"two players committed more than once");
            var copy=new DisplayCaseBlockEntity(root,level.getBlockState(root));
            copy.loadWithComponents(display.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
            h.assertTrue(!copy.hasDisplayedItem(),"success did not persist");
        } finally { first.server.getPlayerList().remove(first); second.server.getPlayerList().remove(second); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void wholeCaseRotationRollsBackAndMalformedOrDeniedCasesStayIntact(GameTestHelper h) {
        var level=h.getLevel(); var root=h.absolutePos(new BlockPos(3,2,3)); prepare(h,root,15);
        var player=ManagedResourceTestPlayers.survival(level,"CaseRollback"); var block=BlockRegistry.DISPLAY_CASE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
        var display=(DisplayCaseBlockEntity)level.getBlockEntity(root); var expected=merchandise(); load(h,display,expected);
        var before=level.getBlockState(root); var above=level.getBlockState(root.above());
        try {
            for(boolean throwing:new boolean[]{false,true}) {
                boolean result=DisplayCaseDecoratorService.rotate(level,player,block,root,player.getMainHandItem(),Direction.UP,(pos,state)->{
                    if(pos.equals(root.above())) { if(throwing) throw new IllegalStateException("injected upper failure"); return false; }
                    return level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                });
                h.assertTrue(!result && level.getBlockState(root).equals(before) && level.getBlockState(root.above()).equals(above),"partial rotation not rolled back");
                h.assertTrue(level.getBlockEntity(root)==display && ItemStack.matches(expected,display.displayedItem()),"rollback lost root contents");
            }
            for(GameType mode:new GameType[]{GameType.ADVENTURE,GameType.SPECTATOR}) {
                player.setGameMode(mode);
                DisplayCaseDecoratorService.interact(level,player,InteractionHand.MAIN_HAND,hit(root.above()));
                h.assertTrue(level.getBlockState(root).equals(before) && ItemStack.matches(expected,display.displayedItem()),"permission denial mutated case");
            }
            player.setGameMode(GameType.SURVIVAL);
            block.duringMutation(()->level.setBlock(root.above(),above.setValue(DisplayCaseBlock.FACING,Direction.EAST),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE));
            var malformed=level.getBlockState(root.above());
            DisplayCaseDecoratorService.interact(level,player,InteractionHand.MAIN_HAND,hit(root));
            h.assertTrue(level.getBlockState(root).equals(before) && level.getBlockState(root.above()).equals(malformed) && ItemStack.matches(expected,display.displayedItem()),"malformed case modified");
            block.duringMutation(()->level.setBlock(root.above(),above,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE));
        } finally { player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    private static void prepare(GameTestHelper h, BlockPos root, int mask) {
        var level=h.getLevel(); var block=BlockRegistry.DISPLAY_CASE.get();
        block.duringMutation(()->{
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int y=0;y<4;y++) level.setBlock(root.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            place(h,root);
            for(int i=0;i<4;i++) if((mask&(1<<i))!=0) place(h,root.relative(SIDES[i]));
            return true;
        });
        level.updateNeighborsAt(root,block);
        for(Direction side:SIDES) level.updateNeighborsAt(root.relative(side),block);
    }
    private static void place(GameTestHelper h,BlockPos root) {
        var block=BlockRegistry.DISPLAY_CASE.get();
        for(var cell:block.cells()) h.getLevel().setBlock(block.worldPosition(root,Direction.NORTH,cell),block.stateFor(Direction.NORTH,cell),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static ItemStack merchandise() {
        var stack=new ItemStack(Items.DIAMOND_SWORD,3); stack.setDamageValue(27);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Legacy case specimen"));
        var tag=new CompoundTag(); tag.putInt("quality",73); tag.putString("owner","case-owner"); tag.putString("origin","Jhelom");
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return stack;
    }
    private static void load(GameTestHelper h,DisplayCaseBlockEntity display,ItemStack stack) {
        var tag=new CompoundTag(); tag.put("DisplayedItem",stack.save(h.getLevel().registryAccess())); display.loadWithComponents(tag,h.getLevel().registryAccess());
    }
    private static void syncPacket(GameTestHelper h, DisplayCaseBlockEntity source, DisplayCaseBlockEntity... replicas) {
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer,
                    (ClientboundBlockEntityDataPacket) source.getUpdatePacket());
            var decoded = ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.getPos().equals(source.getBlockPos()) && decoded.getType() == source.getType(), "wrong packet root/type");
            for (var replica : replicas) {
                replica.onDataPacket(null, decoded, h.getLevel().registryAccess());
                h.assertTrue(ItemStack.matches(source.displayedItem(), replica.displayedItem()),
                        "wire update left renderer-facing state stale");
            }
        } finally { buffer.release(); }
    }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false); }
    private static void use(ServerPlayer player,InteractionHand hand,BlockHitResult hit) { player.gameMode.useItemOn(player,player.level(),player.getItemInHand(hand),hand,hit); }
}
