package com.seggellion.britannia_mod.structure;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.LockableDoorBlockEntity;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.structure.HouseSignBlock.SignType;
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.world.level.block.state.BlockState;


import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

public class HousePrivacyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Toggle privacy and do side-effects. Called from NetworkHandler */
    public static void handle(ServerPlayer player,
                              HouseLotBlockEntity lot,
                              boolean makePrivate) {

        ServerLevel level = player.serverLevel();

        // Resolve and validate before touching anything. This used to flip the lot's privacy
        // flag and push it to clients first, so a house whose region was missing -- every house
        // between a restart and the region rehydrate finishing -- came away marked private with
        // its doors never locked and no key issued. Half a privacy change is worse than none.
        UUID houseUuid = lot.getHouseUuid();
        @Nullable StructureRecord rec =
            StructureRegionManager.getStructureByUuid(houseUuid);
        if (rec == null) {
            LOGGER.warn("No StructureRecord for house {}; privacy unchanged", houseUuid);
            return;
        }

        lot.setPrivate(makePrivate);
        level.sendBlockUpdated(lot.getBlockPos(), lot.getBlockState(),
                               lot.getBlockState(), Block.UPDATE_CLIENTS);

        if (makePrivate) {
            /* -------- PRIVATE: lock + key + reset sign -------- */
            giveKeyIfMissing(player.getInventory(), houseUuid);
            lockOrUnlockDoors(level, rec.getFullBox(), true);
            resetSigns(level, rec.getFullBox());
            LOGGER.info("House {} set private: doors locked, key issued, sign reset", houseUuid);
        } else {
            /* -------- PUBLIC: unlock + destroy key -------- */
            removeKeyIfPresent(player.getInventory(), houseUuid);
            lockOrUnlockDoors(level, rec.getFullBox(), false);
            LOGGER.info("House {} set public: doors unlocked + key removed", houseUuid);
        }
    }

    /* ─────────────────────────────── helpers ────────────────────────────── */

    private static void giveKeyIfMissing(Inventory inv, UUID houseUuid) {
        for (ItemStack s : inv.items) {
            if (s.getItem() instanceof HouseKeyItem k && k.matches(s, houseUuid))
                return;
        }
        ItemStack key = ((HouseKeyItem) ItemRegistry.HOUSE_KEY.get()).createKey(houseUuid);
        if (!inv.player.addItem(key.copy())) inv.player.drop(key, false);
        inv.player.sendSystemMessage(Component.literal("A new house key has been issued."));
    }

    private static void removeKeyIfPresent(Inventory inv, UUID houseUuid) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.getItem() instanceof HouseKeyItem k && k.matches(s, houseUuid)) {
                inv.items.set(i, ItemStack.EMPTY);
                inv.player.sendSystemMessage(Component.literal("Your house key crumbles to dust."));
                break;
            }
        }
    }

    private static void lockOrUnlockDoors(ServerLevel level, AABB box, boolean lock) {
        BlockPos.betweenClosedStream(
                (int) box.minX, (int) box.minY, (int) box.minZ,
                (int) box.maxX - 1, (int) box.maxY - 1, (int) box.maxZ - 1)
            .forEach(pos -> {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof LockableDoorBlockEntity door) {
                    door.setLocked(lock);
                    level.sendBlockUpdated(pos, door.getBlockState(), door.getBlockState(),
                                           Block.UPDATE_CLIENTS);
                }
            });

        /* play feedback sound */
        SoundEvent snd = lock ? ModSounds.DOOR_LOCK.get()
                              : ModSounds.DOOR_LOCK.get();
        level.playSound(null,
            BlockPos.containing((box.minX + box.maxX) * .5,
                                (box.minY + box.maxY) * .5,
                                (box.minZ + box.maxZ) * .5),
            snd, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** when house becomes private → revert any custom sign to DEFAULT */
    private static void resetSigns(ServerLevel level, AABB box) {
        BlockPos.betweenClosedStream(
                (int) box.minX, (int) box.minY, (int) box.minZ,
                (int) box.maxX - 1, (int) box.maxY - 1, (int) box.maxZ - 1)
            .forEach(pos -> {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof HouseSignBlock
                    && state.getValue(HouseSignBlock.SIGN_TYPE) != SignType.DEFAULT) {

                    level.setBlock(pos,
                        state.setValue(HouseSignBlock.SIGN_TYPE, SignType.DEFAULT),
                        Block.UPDATE_CLIENTS);
                }
            });
    }
}
