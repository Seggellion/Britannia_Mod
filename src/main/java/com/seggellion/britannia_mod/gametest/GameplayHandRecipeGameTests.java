package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayHandRecipeGameTests {
    private static final String TEMPLATE="service_npc_spawn_test_empty";

    @GameTest(template=TEMPLATE, timeoutTicks=100)
    public static void everyBowlRecipeHandAndCountMergesFinalOutputAndConservesRemainders(GameTestHelper h) {
        ServerPlayer player=player(h);
        Item[][] recipes={{ItemRegistry.EMPTY_BOWL.get(),ItemRegistry.DIRT.get(),ItemRegistry.BOWL_OF_DIRT.get()},
                {ItemRegistry.BOWL_OF_DIRT.get(),ItemRegistry.DUNG.get(),ItemRegistry.BOWL_OF_FERTILE_DIRT.get()},
                {ItemRegistry.BOWL_OF_FERTILE_DIRT.get(),ItemRegistry.BOWL_OF_WATER.get(),ItemRegistry.FERTILIZED_DIRT.get()}};
        for (int recipe=0;recipe<recipes.length;recipe++) for (boolean reverse:new boolean[]{false,true}) for (int count:new int[]{1,2,3,64}) {
            player.getInventory().clearContent();
            Item[] row=recipes[recipe];
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(row[reverse?1:0],count));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(row[reverse?0:1],count));
            player.getInventory().setItem(9,new ItemStack(row[2])); player.getInventory().setItem(10,new ItemStack(row[2]));
            var named=new ItemStack(row[2]); named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep this distinct")); player.getInventory().setItem(11,named);
            for (int craft=1;craft<=count;craft++) {
                use(player,InteractionHand.OFF_HAND); // deliberately reversed callback does not commit
                h.assertTrue(plainCount(player,row[2])==craft+1,"OFF callback committed before MAIN");
                use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
                h.assertTrue(plainCount(player,row[2])==craft+2,"wrong output count for recipe/hand/count "+recipe+"/"+reverse+"/"+count);
                h.assertTrue(player.getInventory().getItem(11).getCount()==1 && player.getInventory().getItem(11).has(DataComponents.CUSTOM_NAME),"incompatible components were merged");
                if (recipe==2) h.assertTrue(player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get())==craft*2,"remainder not conserved");
            }
            h.assertTrue(!player.getMainHandItem().is(row[2]),"exhausting output ignored compatible stack");
            h.assertTrue(player.getInventory().getItem(9).getCount()+player.getInventory().getItem(10).getCount()==count+2,"last output did not merge");
        }
        player.server.getPlayerList().remove(player); h.succeed();
    }

    @GameTest(template=TEMPLATE, timeoutTicks=100)
    public static void sevenPigmentsLoadEitherHandWithoutLosingTubComponentsOrDoubleCharging(GameTestHelper h) {
        ServerPlayer player=player(h);
        Item[] pigments={DyeItemRegistry.MADDER_RED.get(),DyeItemRegistry.WOAD_BLUE.get(),DyeItemRegistry.VERDIGRIS.get(),DyeItemRegistry.WELD_GOLD.get(),DyeItemRegistry.SOOT_BLACK.get(),DyeItemRegistry.CHALK_WHITE.get(),DyeItemRegistry.ICE_BLUE.get()};
        for (GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) for (boolean reverse:new boolean[]{false,true}) for (Item pigment:pigments) {
            player.getInventory().clearContent(); player.setGameMode(mode);
            InteractionHand tubHand=reverse?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
            InteractionHand pigmentHand=reverse?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
            var tub=new ItemStack(DyeItemRegistry.DYE_TUB.get()); tub.set(DataComponents.CUSTOM_NAME,Component.literal("My tub"));
            player.setItemInHand(tubHand,tub); player.setItemInHand(pigmentHand,new ItemStack(pigment,2));
            use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
            h.assertTrue(DyeTubStateAccess.read(tub).pigmentId().equals(DyeItemRegistry.pigmentId(pigment)),"pigment did not load through actual dispatch");
            int remaining=mode==GameType.CREATIVE?2:1;
            h.assertTrue(player.getItemInHand(pigmentHand).getCount()==remaining,"wrong pigment cost/duplicate");
            h.assertTrue(tub.getCount()==1 && Component.literal("My tub").equals(tub.get(DataComponents.CUSTOM_NAME)),"tub metadata lost");
            use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
            h.assertTrue(player.getItemInHand(pigmentHand).getCount()==remaining,"same pigment charged again");
        }
        player.server.getPlayerList().remove(player); h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void creativeBowlCostsMatchSurvivalAndSeparateClicksCanCraftInOneTick(GameTestHelper h) {
        ServerPlayer player=player(h); player.setGameMode(GameType.CREATIVE);
        for (boolean reverse:new boolean[]{false,true}) {
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(reverse?ItemRegistry.DIRT.get():ItemRegistry.EMPTY_BOWL.get(),3));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(reverse?ItemRegistry.EMPTY_BOWL.get():ItemRegistry.DIRT.get(),3));
            use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
            use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
            h.assertTrue(player.getMainHandItem().getCount()==1 && player.getOffhandItem().getCount()==1 && player.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get())==2,"Creative costs or distinct-click handling changed");
        }
        player.server.getPlayerList().remove(player); h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void aimedSourceWaterWinsOverDryRecipeInEitherHand(GameTestHelper h) {
        var player=player(h); var source=h.absolutePos(new BlockPos(3,2,5));
        h.getLevel().setBlock(source,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),3);
        for(boolean reverse:new boolean[]{false,true}) {
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(reverse?ItemRegistry.DIRT.get():ItemRegistry.EMPTY_BOWL.get(),2));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(reverse?ItemRegistry.EMPTY_BOWL.get():ItemRegistry.DIRT.get(),2));
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(source));
            use(player,InteractionHand.OFF_HAND); use(player,InteractionHand.MAIN_HAND); use(player,InteractionHand.OFF_HAND);
            h.assertTrue(player.getInventory().countItem(ItemRegistry.DIRT.get())==2 && player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get())==1
                    && player.getInventory().countItem(ItemRegistry.BOWL_OF_WATER.get())==1 && player.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get())==0,
                    "source precedence/actual bowl hand or duplicate handling failed");
            h.assertTrue(h.getLevel().getFluidState(source).isSource(),"bowl consumed source water");
        }
        player.server.getPlayerList().remove(player); h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=ManagedResourceTestPlayers.survival(h.getLevel(),"hand-recipes"); var pos=h.absolutePos(new BlockPos(3,3,3));
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); p.setXRot(-90); return p;
    }
    private static void use(ServerPlayer p,InteractionHand hand) { p.gameMode.useItem(p,p.level(),p.getItemInHand(hand),hand); }
    private static int plainCount(ServerPlayer player,Item item) {
        int count=0; for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var stack=player.getInventory().getItem(slot); if(ItemStack.isSameItemSameComponents(stack,new ItemStack(item))) count+=stack.getCount();
        } return count;
    }
}
