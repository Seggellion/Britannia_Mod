package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.Random;

public class CreateFoodSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Item[] FOOD_ITEMS = {
        Items.BREAD,
        Items.COOKED_BEEF,
        Items.COOKED_CHICKEN,
        Items.COOKED_PORKCHOP,
        Items.COOKED_SALMON,
        Items.COOKED_COD,
        Items.APPLE,
        Items.GOLDEN_APPLE
    };
    private static final Random RANDOM = new Random();

    @Override
    protected int getManaCost() {
        return 4;
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(BritanniaMod.GARLIC.get()),
            new ItemStack(BritanniaMod.GINSENG.get()),
            new ItemStack(BritanniaMod.MANDRAKE_ROOT.get())
        };
    }

    @Override
    protected int getCooldownTime() {
        return 1000; // 1-second cooldown
    }

    @Override
    protected void applyEffect(ServerPlayer caster, LivingEntity target) {
        if (caster == null) {
            LOGGER.warn("Caster is null, cannot proceed with applyEffect.");
            return;
        }

        LOGGER.info("applySelfEffect caster: {}", caster.getName().getString());
        applySelfEffect(caster);
    }

    @Override
    protected void applySelfEffect(ServerPlayer caster) {
        if (caster == null || caster.getServer() == null) {
            return; // If caster is null, we shouldn't proceed
        }

        // Freeze the player during casting, then create food after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Create a random piece of food and add it to the caster's inventory
            ItemStack foodItem = new ItemStack(FOOD_ITEMS[RANDOM.nextInt(FOOD_ITEMS.length)]);
            boolean added = caster.getInventory().add(foodItem);

            if (added) {
                LOGGER.info("Food item {} added to caster's inventory", foodItem.getItem().getDescriptionId());
            } else {
                LOGGER.warn("Could not add food item to caster's inventory");
                caster.sendSystemMessage(Component.literal("Your inventory is full!"));
            }

            // Play create food spell sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent createFoodSound = ModSounds.CREATE_FOOD_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    createFoodSound, // Sound event for create food spell
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }
        });
    }

    @Override
    protected void applyTargetEffect(ServerPlayer caster, LivingEntity target) {
        // This spell does not have a target effect, only self-cast.
    }

    // Check if the item is the spell item for Create Food
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.CREATE_FOOD_ITEM.get();
    }
}