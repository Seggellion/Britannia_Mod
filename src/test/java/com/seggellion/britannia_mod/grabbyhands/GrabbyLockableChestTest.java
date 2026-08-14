package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A locked chest may be carried off — owner decision — but it must arrive still locked.
 *
 * <h2>The exploit this closes</h2>
 *
 * <p>{@code LockpickingEventHandler.onRightClickBlock} calls {@code seedChestKeyIfNeeded()} on every
 * right-click of a lockable chest, and that method mints a key whenever {@code ChestKeySeeded} is
 * false. A chest that forgot it had already been keyed would therefore hand out a fresh key after
 * every place-and-pickup cycle: place, right-click, pocket the key, pick up, repeat.
 *
 * <p>Carrying the whole block-entity tag rather than only the contents is what prevents that, and is
 * also what stops a stolen chest silently unlocking itself in the thief's backpack.
 */
class GrabbyLockableChestTest {
    private static final String TAG_LOCK_ID = "LockId";
    private static final String TAG_CHEST_KEY_SEEDED = "ChestKeySeeded";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_LOCK_DIFFICULTY = "LockDifficulty";

    private static final UUID LOCK_ID = UUID.fromString("0fedcba9-8765-4321-0fed-cba987654321");

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
    }

    /** The tag a locked, keyed, filled chest writes through saveAdditional. */
    private static CompoundTag lockedChestTag() {
        CompoundTag tag = new CompoundTag();
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.GOLD_INGOT, 9));
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putUUID(TAG_LOCK_ID, LOCK_ID);
        tag.putBoolean(TAG_CHEST_KEY_SEEDED, true);
        tag.putBoolean(TAG_LOCKED, true);
        tag.putInt(TAG_LOCK_DIFFICULTY, 6);
        // Provenance is stamped fresh on placement and must not travel.
        CompoundTag provenance = new CompoundTag();
        GrabbyInstanceState.playerPlaced(UUID.randomUUID(), 1L).write(provenance);
        tag.put(GrabbyInstanceState.TAG_KEY, provenance.getCompound(GrabbyInstanceState.TAG_KEY));
        return tag;
    }

    /** What writePortableState produces: the whole tag, minus provenance. */
    private static CompoundTag transported(CompoundTag saved) {
        CompoundTag data = saved.copy();
        data.remove(GrabbyInstanceState.TAG_KEY);
        return data;
    }

    @Test
    void theLockStaysLockedAcrossTransport() {
        CompoundTag arrived = transported(lockedChestTag());

        assertTrue(arrived.getBoolean(TAG_LOCKED),
                "a stolen chest must arrive still locked, not helpfully opened in transit");
    }

    @Test
    void theLockIdentitySurvivesSoExistingKeysStillFit() {
        CompoundTag arrived = transported(lockedChestTag());

        assertTrue(arrived.hasUUID(TAG_LOCK_ID));
        assertEquals(LOCK_ID, arrived.getUUID(TAG_LOCK_ID),
                "a new lock id would orphan every key already issued for this chest");
    }

    @Test
    void theKeySeededMarkerSurvivesSoNoSecondKeyIsMinted() {
        CompoundTag arrived = transported(lockedChestTag());

        assertTrue(arrived.getBoolean(TAG_CHEST_KEY_SEEDED),
                "losing this marker turns every place-and-pickup cycle into a key printer");
    }

    @Test
    void theLockDifficultySurvives() {
        assertEquals(6, transported(lockedChestTag()).getInt(TAG_LOCK_DIFFICULTY));
    }

    @Test
    void contentsSurviveAlongsideTheLockState() {
        CompoundTag arrived = transported(lockedChestTag());

        NonNullList<ItemStack> restored = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(arrived, restored, registries);

        assertEquals(Items.GOLD_INGOT, restored.get(0).getItem());
        assertEquals(9, restored.get(0).getCount());
    }

    @Test
    void provenanceDoesNotTravelWithTheChest() {
        // The placement transaction stamps fresh provenance. Carrying the previous placer forward
        // would be misleading at best and would survive being handed to another player at worst.
        assertFalse(transported(lockedChestTag()).contains(GrabbyInstanceState.TAG_KEY));
    }

    @Test
    void anUnkeyedChestIsStillReportedAsUnkeyed() {
        // The marker must reflect reality in both directions, or a legitimately fresh chest would
        // never issue its first key.
        CompoundTag fresh = new CompoundTag();
        fresh.putBoolean(TAG_CHEST_KEY_SEEDED, false);
        fresh.putBoolean(TAG_LOCKED, false);

        CompoundTag arrived = transported(fresh);

        assertFalse(arrived.getBoolean(TAG_CHEST_KEY_SEEDED));
        assertFalse(arrived.getBoolean(TAG_LOCKED));
    }

    @Test
    void repeatedTransportDoesNotDriftTheLockState() {
        CompoundTag carried = lockedChestTag();
        for (int cycle = 0; cycle < 3; cycle++) {
            carried = transported(carried);
        }

        assertTrue(carried.getBoolean(TAG_LOCKED));
        assertTrue(carried.getBoolean(TAG_CHEST_KEY_SEEDED));
        assertEquals(LOCK_ID, carried.getUUID(TAG_LOCK_ID));
        assertEquals(6, carried.getInt(TAG_LOCK_DIFFICULTY));
    }
}
