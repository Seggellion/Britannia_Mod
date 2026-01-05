package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.skill.SkillManager; // Your existing SkillManager
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class InstrumentItem extends Item {

    // UO Style Formatting
    private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final TextColor GRAY_848484 = TextColor.fromRgb(0x848484);
    
    private final DeferredHolder<SoundEvent, SoundEvent> successSound;
    private final DeferredHolder<SoundEvent, SoundEvent> failSound;

    public InstrumentItem(Properties properties, 
                          DeferredHolder<SoundEvent, SoundEvent> successSound, 
                          DeferredHolder<SoundEvent, SoundEvent> failSound) {
        super(properties);
        this.successSound = successSound;
        this.failSound = failSound;
    }

    /**
     * This method acts as the "Event Handler" for right-clicking this item.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Logic only runs on the Logical Server
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            
            // 1. Get Skill
            float skill = SkillManager.getSkill(serverPlayer, "musicianship");
            
            // 2. Calculate Success (Logistic curve from SkillManager)
            double chance = SkillManager.catchChance(skill);
            boolean success = level.random.nextDouble() < chance;

            // 3. Trigger Gain (Your manager handles the difficulty/caps)
            SkillManager.trySkillGain(serverPlayer, "musicianship", success);

            // 4. Play Sound & Feedback
            if (success) {
                level.playSound(null, player.blockPosition(), successSound.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                sendGray(serverPlayer, "You play a lovely melody.");
            } else {
                level.playSound(null, player.blockPosition(), failSound.get(), SoundSource.PLAYERS, 1.0F, 0.8F); // Lower pitch for fail?
                sendGray(serverPlayer, "You play poorly and disturb the peace.");
            }

            // 5. Apply Cooldown (Prevent macro spamming)
            // 40 ticks = 2 seconds
            player.getCooldowns().addCooldown(this, 40);
        }

        // Returns success to trigger the arm-swing animation on the client
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void sendGray(Player player, String msg) {
        Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(GRAY_848484);
        player.sendSystemMessage(Component.literal(msg).withStyle(style));
    }
}