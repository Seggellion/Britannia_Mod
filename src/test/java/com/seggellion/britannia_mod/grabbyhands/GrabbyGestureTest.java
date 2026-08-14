package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyRegisteredTestContent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrabbyGestureTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    /**
     * Deliberately a method, not a static field. A static initializer touching {@link ItemStack} runs
     * before {@code @BeforeAll}, which initializes {@code BuiltInRegistries} un-bootstrapped and
     * poisons every other test class in the shared JVM.
     */
    private static ItemStack empty() {
        return ItemStack.EMPTY;
    }

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrabbyRegisteredTestContent.ensureRegistered();
    }

    @Test
    void sneakingWithBothHandsEmptyIsThePickupGesture() {
        assertTrue(GrabbyGesture.isPickupGesture(true, empty(), empty()));
    }

    @Test
    void anOrdinaryRightClickIsNotAPickupSoSittingAndOpeningStillWork() {
        assertFalse(GrabbyGesture.isPickupGesture(false, empty(), empty()),
                "without sneaking this must stay a normal use interaction");
    }

    @Test
    void holdingSomethingInEitherHandIsNotAPickup() {
        assertFalse(GrabbyGesture.isPickupGesture(true, new ItemStack(Items.STICK), empty()));
        assertFalse(GrabbyGesture.isPickupGesture(true, empty(), new ItemStack(Items.TORCH)));
    }

    @Test
    void theDecoratorToolAlwaysWinsTheInteraction() {
        // Its right-click language spans nudgeable furniture, walls, signs, carpets and shrine
        // anchors, and ChairBlock already refuses to seat a player holding it. Grabby Hands must not
        // shadow any of that.
        ItemStack decorator = new ItemStack(GrabbyRegisteredTestContent.decoratorTool());
        assertTrue(GrabbyGesture.deferToDecoratorTool(decorator, empty()));
        assertTrue(GrabbyGesture.deferToDecoratorTool(empty(), decorator));
        assertFalse(GrabbyGesture.deferToDecoratorTool(empty(), empty()));
        assertFalse(GrabbyGesture.deferToDecoratorTool(new ItemStack(Items.STICK), empty()));
    }

    @Test
    void holdingARecognisedAxeIsTheDestroyGesture() {
        // R-2.11.1: an axe in the main hand means "destroy this", not "use this".
        assertTrue(GrabbyGesture.isAxeGesture(new ItemStack(Items.IRON_AXE)));
        assertTrue(GrabbyGesture.isAxeGesture(new ItemStack(Items.NETHERITE_AXE)));
    }

    @Test
    void anythingThatIsNotAnAxeIsNotTheDestroyGesture() {
        assertFalse(GrabbyGesture.isAxeGesture(new ItemStack(Items.IRON_SWORD)));
        assertFalse(GrabbyGesture.isAxeGesture(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertFalse(GrabbyGesture.isAxeGesture(empty()));
    }

    @Test
    void theAxeGestureLooksOnlyAtTheMainHand() throws IOException {
        // An axe in the offhand must not put the player in destroy mode, and a second player standing
        // at the same chair empty-handed must still be able to sit on it.
        String gesture = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyGesture.java"),
                StandardCharsets.UTF_8);
        assertTrue(gesture.contains("isAxeGesture(ItemStack mainHand)"),
                "the axe gesture must take only the main hand");
    }

    @Test
    void axeModeIsScopedToEnrolledDestroyableBlocksOnly() throws IOException {
        // Suppressing normal use everywhere would be a far bigger change than R-2.11 asked for. A door,
        // a workbench or an NPC must behave exactly as before when a player is holding an axe.
        String handler = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyInteractionHandler.java"),
                StandardCharsets.UTF_8);
        assertTrue(handler.contains("GrabbyEligibility.axeDestroyableType"),
                "the axe branch must bail out for blocks Grabby Hands has not enrolled");
    }

    @Test
    void theDecoratorToolStillWinsOverAxeMode() throws IOException {
        String handler = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyInteractionHandler.java"),
                StandardCharsets.UTF_8);
        int decorator = handler.indexOf("deferToDecoratorTool");
        int axeBranch = handler.indexOf("isAxeGesture");
        assertTrue(decorator >= 0 && axeBranch > decorator,
                "the decorator tool check must come first, or it would be shadowed");
    }

    @Test
    void theHandlerRunsAtHighestPriorityOnTheRightClickPathOnly() throws IOException {
        // Priority is load-bearing: at anything lower, ChairBlock.useWithoutItem seats the player and
        // a container opens before Grabby Hands is consulted.
        String handler = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyInteractionHandler.java"),
                StandardCharsets.UTF_8);
        assertTrue(handler.contains("EventPriority.HIGHEST"));
        assertTrue(handler.contains("PlayerInteractEvent.RightClickBlock"));
        assertFalse(handler.contains("LeftClickBlock"),
                "Grabby Hands must not enter the break pipeline");
        assertTrue(handler.contains("InteractionHand.MAIN_HAND"),
                "the event fires once per hand; only the main hand may drive a pickup");
    }
}
