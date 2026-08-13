package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host's job is to carry an arbitrary item without understanding it.
 *
 * <p>These tests deliberately use items and components the host has never heard of. If the host ever
 * starts inspecting or rebuilding what it holds, something here stops matching exactly.
 */
class GrabbyPlacedItemHostTest {
    private static final UUID PLACER = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
    }

    /** Save and reload through NBT, the way a chunk unload and reload would. */
    private static ItemStack persistPayload(ItemStack payload, GrabbyInstanceState provenance) {
        CompoundTag tag = new CompoundTag();
        if (!payload.isEmpty()) {
            tag.put("Payload", payload.save(registries));
        }
        provenance.write(tag);

        ItemStack restored = tag.contains("Payload")
                ? ItemStack.parse(registries, tag.getCompound("Payload")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        assertEquals(provenance, GrabbyInstanceState.read(tag), "provenance rides along with the payload");
        return restored;
    }

    @Test
    void anOrdinaryStackSurvivesSaveAndReloadExactly() {
        ItemStack original = new ItemStack(Items.GOLD_INGOT, 3);

        ItemStack restored = persistPayload(original, GrabbyInstanceState.playerPlaced(PLACER, 10L));

        assertTrue(ItemStack.matches(original, restored), "the exact stack must come back");
        assertEquals(3, restored.getCount());
    }

    @Test
    void aCustomNameSurvives() {
        ItemStack original = new ItemStack(Items.BREAD);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Lord British's Loaf"));

        ItemStack restored = persistPayload(original, GrabbyInstanceState.playerPlaced(PLACER, 1L));

        assertTrue(ItemStack.matches(original, restored));
        assertEquals("Lord British's Loaf",
                restored.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void componentsTheHostHasNeverHeardOfSurvive() {
        // The host stores the stack opaquely. Damage, repair cost and custom data are simply along for
        // the ride, which is what stops a future item property becoming a data-loss bug here.
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.setDamageValue(37);
        original.set(DataComponents.REPAIR_COST, 5);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Chipped"));

        ItemStack restored = persistPayload(original, GrabbyInstanceState.playerPlaced(PLACER, 2L));

        assertTrue(ItemStack.matches(original, restored));
        assertEquals(37, restored.getDamageValue());
        assertEquals(5, restored.get(DataComponents.REPAIR_COST));
    }

    @Test
    void repeatedSaveAndReloadCyclesDoNotDrift() {
        ItemStack original = new ItemStack(Items.GOLDEN_APPLE, 2);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Twice Blessed"));

        ItemStack carried = original.copy();
        for (int cycle = 0; cycle < 3; cycle++) {
            carried = persistPayload(carried, GrabbyInstanceState.playerPlaced(PLACER, 3L));
        }

        assertTrue(ItemStack.matches(original, carried));
    }

    @Test
    void aStaticHostIsRepresentableAndStaysProtected() {
        ItemStack payload = new ItemStack(Items.IRON_INGOT);

        ItemStack restored = persistPayload(payload, GrabbyInstanceState.worldPlaced());

        assertTrue(ItemStack.matches(payload, restored), "an admin-placed host still holds its item");
        CompoundTag tag = new CompoundTag();
        GrabbyInstanceState.worldPlaced().write(tag);
        assertFalse(GrabbyInstanceState.read(tag).grabbyManaged(),
                "a host with no player provenance must stay immovable");
    }

    @Test
    void anEmptyHostRoundTripsAsEmptyRatherThanThrowing() {
        assertTrue(persistPayload(ItemStack.EMPTY, GrabbyInstanceState.worldPlaced()).isEmpty());
    }

    // ------------------------------------------------------------------
    // Wiring the host is expected to have
    // ------------------------------------------------------------------

    @Test
    void theHostBlockEntityImplementsBothGrabbyContracts() {
        assertTrue(GrabbyProvenanceHolder.class.isAssignableFrom(GrabbyPlacedItemBlockEntity.class),
                "the host must carry provenance or it could never be picked up");
        assertTrue(GrabbyPayloadHolder.class.isAssignableFrom(GrabbyPlacedItemBlockEntity.class),
                "the host must expose its payload for detach, or pickup would double-drop");
    }

    @Test
    void hostEligibilityAcceptsOnlyPlainItemsInTheTag() {
        // A BlockItem places as its own block; routing it through the host would replace a real block
        // with a generic stand-in and lose its behaviour.
        assertFalse(GrabbyEligibility.hostablePlainItem(new ItemStack(Items.OAK_STAIRS)),
                "block items must never be hosted");
        assertFalse(GrabbyEligibility.hostablePlainItem(ItemStack.EMPTY));
        assertFalse(GrabbyEligibility.hostablePlainItem(null));
        // Untagged plain items are refused too; the tag is the only way in.
        assertFalse(GrabbyEligibility.hostablePlainItem(new ItemStack(Items.STICK)));
    }

    @Test
    void aHostPositionCanBeAskedForItsPayloadWithoutABlockEntity() {
        assertTrue(GrabbyProvenanceAccess.read(null, BlockPos.ZERO).provenance() == GrabbyProvenance.WORLD,
                "anything that cannot answer reads as protected");
    }
}
