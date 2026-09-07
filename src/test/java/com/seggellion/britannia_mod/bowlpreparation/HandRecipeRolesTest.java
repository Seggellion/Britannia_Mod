package com.seggellion.britannia_mod.bowlpreparation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HandRecipeRolesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void orientationAndSnapshotsPreserveActualRoleSlots() {
        var main=new ItemStack(Items.DIRT,3); var off=new ItemStack(Items.GLASS_BOTTLE,2);
        var roles=HandRecipeRoles.match(main,off,Items.GLASS_BOTTLE,Items.DIRT).orElseThrow();
        assertEquals(InteractionHand.OFF_HAND,roles.driverHand()); assertEquals(InteractionHand.MAIN_HAND,roles.ingredientHand());
        main.set(DataComponents.CUSTOM_NAME,Component.literal("changed")); roles.expectedOff().shrink(1);
        assertNull(roles.expectedMain().get(DataComponents.CUSTOM_NAME)); assertEquals(2,roles.expectedOff().getCount());
    }
    @Test void equalItemRolesAreDeterministicButAnAliasedPhysicalStackIsRejected() {
        var main=new ItemStack(Items.DIRT,2); var off=main.copy();
        assertEquals(InteractionHand.MAIN_HAND,HandRecipeRoles.match(main,off,Items.DIRT,Items.DIRT).orElseThrow().driverHand());
        assertTrue(HandRecipeRoles.match(main,main,Items.DIRT,Items.DIRT).isEmpty());
    }
    @Test void conflictingRecipeDefinitionsRefuseWithoutMutating() {
        var main=new ItemStack(Items.GLASS_BOTTLE); var off=new ItemStack(Items.DIRT);
        var recipes=new BowlPreparationService.RecipeSet(Items.GLASS_BOTTLE,Items.DIRT,Items.DIRT,Items.GLASS_BOTTLE,Items.BRICK);
        assertTrue(BowlPreparationService.plan(main,off,recipes).isEmpty()); assertEquals(1,main.getCount()); assertEquals(1,off.getCount());
    }
    @Test void reversedFinalMixChargesActualSnapshotsAndRejectsStaleComponents() {
        var main=new ItemStack(Items.POTION,3); var off=new ItemStack(Items.CLAY_BALL,2);
        var recipes=new FertileDirtMixingService.RecipeSet(Items.CLAY_BALL,Items.POTION,Items.DIRT,Items.GLASS_BOTTLE);
        var plan=FertileDirtMixingService.plan(main,off,recipes).orElseThrow();
        assertEquals(InteractionHand.OFF_HAND,plan.driverHand());
        off.set(DataComponents.CUSTOM_NAME,Component.literal("changed"));
        assertFalse(FertileDirtMixingService.commit(plan,main,off).applied()); assertEquals(3,main.getCount()); assertEquals(2,off.getCount());
        var fresh=FertileDirtMixingService.plan(main,off,recipes).orElseThrow(); var committed=FertileDirtMixingService.commit(fresh,main,off);
        assertTrue(committed.applied()); assertEquals(2,main.getCount()); assertEquals(1,off.getCount());
        assertEquals(2,committed.returnedBowls().getCount()); assertNull(committed.fertilizedDirt().get(DataComponents.CUSTOM_NAME));
    }
}
