package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.BritanniaLockableChestBlock;
import com.seggellion.britannia_mod.block.entity.BritanniaChestBlockEntity;
import com.seggellion.britannia_mod.item.ChestKeyItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class LockpickingEventHandler {
    private static final String LOCKPICKING_SKILL = "lockpicking";
    private static final float FAILURE_SKILL_GAIN = 0.1f;
    private static final float SUCCESS_SKILL_GAIN = 0.2f;
    private static final long LOCKPICK_COOLDOWN_TICKS = 20L * 5L;
    private static final Map<UUID, Long> NEXT_LOCKPICK_ATTEMPT_TICK = new HashMap<>();

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide || event.getLevel().dimension() != Level.OVERWORLD) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BritanniaLockableChestBlock)) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof BritanniaChestBlockEntity chest)) return;

        chest.seedChestKeyIfNeeded();
        UUID lockId = chest.getOrCreateLockId();
        boolean hasMatchingKey = hasMatchingChestKey(player, lockId);
        boolean holdingMatchingKey = isHoldingMatchingChestKey(player, lockId);

        if (!chest.isLocked()) {
            if (holdingMatchingKey) {
                chest.setLocked(true);
                playLockSound(event.getLevel(), event.getPos());
                player.sendSystemMessage(Component.literal("You lock the chest."));
            } else if (isHoldingChestKey(player)) {
                player.sendSystemMessage(Component.literal("This key does not fit this lock."));
            } else {
                openChest(player, chest);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (hasMatchingKey) {
            chest.setLocked(false);
            playLockSound(event.getLevel(), event.getPos());
            player.sendSystemMessage(Component.literal("You unlock the chest."));
            openChest(player, chest);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        ItemStack lockpicks = getHeldLockpicks(player);
        if (lockpicks.isEmpty()) {
            if (isHoldingChestKey(player)) {
                player.sendSystemMessage(Component.literal("This key does not fit this lock."));
            } else {
                player.sendSystemMessage(Component.literal("You need lockpick tools."));
            }
        } else {
            long gameTime = event.getLevel().getGameTime();
            long nextAttemptTick = NEXT_LOCKPICK_ATTEMPT_TICK.getOrDefault(player.getUUID(), 0L);
            if (gameTime < nextAttemptTick) {
                player.sendSystemMessage(Component.literal("You need a moment before trying again."));
            } else {
                NEXT_LOCKPICK_ATTEMPT_TICK.put(player.getUUID(), gameTime + LOCKPICK_COOLDOWN_TICKS);
                attemptLockpick(player, lockpicks, event.getLevel(), event.getPos(), chest);
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static ItemStack getHeldLockpicks(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ItemRegistry.LOCKPICK_TOOLS.get())) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        return offHand.is(ItemRegistry.LOCKPICK_TOOLS.get()) ? offHand : ItemStack.EMPTY;
    }

    private static void attemptLockpick(ServerPlayer player, ItemStack lockpicks, Level level, BlockPos pos, BritanniaChestBlockEntity chest) {
        float skillBefore = SkillManager.getSkill(player, LOCKPICKING_SKILL);
        int lockDifficulty = chest.getLockDifficulty();
        float minimumSkill = minimumSkillForDifficulty(lockDifficulty);
        boolean meetsMinimumSkill = skillBefore >= minimumSkill;
        double successChance = meetsMinimumSkill ? lockpickSuccessChance(skillBefore, lockDifficulty) : 0.0D;
        boolean success = meetsMinimumSkill && ThreadLocalRandom.current().nextDouble() < successChance;
        float gained = SkillManager.awardSkillGain(
                player,
                LOCKPICKING_SKILL,
                success ? SUCCESS_SKILL_GAIN : FAILURE_SKILL_GAIN
        );
        float skillAfter = SkillManager.getSkill(player, LOCKPICKING_SKILL);

        level.playSound(null, pos, ModSounds.LOCKPICK_ATTEMPT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("You attempt to pick the lock."));
        if (!meetsMinimumSkill) {
            player.sendSystemMessage(Component.literal("This lock is beyond your current skill."));
        } else {
            player.sendSystemMessage(Component.literal(success
                    ? "The lock gives way."
                    : "The lock resists your attempt."));
        }
        if (success) {
            chest.setLocked(false);
            openChest(player, chest);
        }
        player.sendSystemMessage(Component.literal(String.format(
                "Lockpicking debug: difficulty %d, min %.1f, skill %.1f -> %.1f, chance %.1f%%, XP gained %.1f (%s).",
                lockDifficulty,
                minimumSkill,
                skillBefore,
                skillAfter,
                successChance * 100.0,
                gained,
                success ? "success" : "failure"
        )));

        if (!player.getAbilities().instabuild) {
            lockpicks.shrink(1);
        }
    }

    private static boolean hasMatchingChestKey(ServerPlayer player, UUID lockId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ChestKeyItem keyItem && keyItem.matches(held, lockId)) {
                return true;
            }
        }

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ChestKeyItem keyItem && keyItem.matches(stack, lockId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isHoldingMatchingChestKey(ServerPlayer player, UUID lockId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ChestKeyItem keyItem && keyItem.matches(held, lockId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isHoldingChestKey(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof ChestKeyItem
                || player.getOffhandItem().getItem() instanceof ChestKeyItem;
    }

    private static void openChest(ServerPlayer player, BritanniaChestBlockEntity chest) {
        player.openMenu(chest);
        chest.startOpen(player);
    }

    private static void playLockSound(Level level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.DOOR_LOCK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static double lockpickSuccessChance(float skill, int lockDifficulty) {
        double normalizedSkillAboveMinimum = (skill - minimumSkillForDifficulty(lockDifficulty)) / 100.0D;
        double chance = 0.12 + normalizedSkillAboveMinimum * 0.70D;
        return Math.clamp(chance, 0.08, 0.75);
    }

    private static float minimumSkillForDifficulty(int lockDifficulty) {
        int clampedDifficulty = Math.clamp(lockDifficulty, 1, 9);
        return clampedDifficulty == 9 ? 95.0f : 5.0f + ((clampedDifficulty - 1) * 10.0f);
    }
}
