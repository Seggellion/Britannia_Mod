// HorseSellerNPC.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModAttributes;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;

public class HorseSellerNPC extends AbstractVillager {

    public HorseSellerNPC(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            // Collect all gold coins from player's inventory, including held item and off-hand
            int totalCoins = 0;
            List<ItemStack> coinStacks = new ArrayList<>();

            // Main inventory
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(ItemRegistry.GOLD_COIN.get())) {
                    totalCoins += stack.getCount();
                    coinStacks.add(stack);
                }
            }

            // Armor slots
            for (ItemStack stack : player.getInventory().armor) {
                if (stack.is(ItemRegistry.GOLD_COIN.get())) {
                    totalCoins += stack.getCount();
                    coinStacks.add(stack);
                }
            }

            // Off-hand
            ItemStack offHandStack = player.getOffhandItem();
            if (offHandStack.is(ItemRegistry.GOLD_COIN.get())) {
                totalCoins += offHandStack.getCount();
                coinStacks.add(offHandStack);
            }

            // Held item
            ItemStack heldItem = player.getItemInHand(hand);
            if (heldItem.is(ItemRegistry.GOLD_COIN.get()) && !coinStacks.contains(heldItem)) {
                totalCoins += heldItem.getCount();
                coinStacks.add(heldItem);
            }

            if (totalCoins >= 600) {
                // Remove coins from inventory
                int coinsToRemove = 600;
                for (ItemStack stack : coinStacks) {
                    int count = stack.getCount();
                    if (count >= coinsToRemove) {
                        stack.shrink(coinsToRemove);
                        break;
                    } else {
                        coinsToRemove -= count;
                        stack.shrink(count);
                    }
                }

                // Spawn horse
                Horse horse = EntityType.HORSE.create(this.level());
                if (horse != null) {
                    horse.setPos(this.getX(), this.getY(), this.getZ());
                    horse.setTamed(true);
                    horse.setOwnerUUID(player.getUUID());
                    horse.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.SADDLE));
                    this.level().addFreshEntity(horse);                    
                    player.displayClientMessage(Component.literal("You have purchased a tamed horse!"), true);
                }
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(Component.literal("You need 600 gold coins to purchase a horse."), true);
            }
        }
        return InteractionResult.PASS;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MAX_ABSORPTION, 0.0D)
                .add(Attributes.MOVEMENT_EFFICIENCY, 1.0D)
                .add(Attributes.BURNING_TIME, 1.0D)
                .add(Attributes.JUMP_STRENGTH, 1.0D)
                .add(Attributes.SAFE_FALL_DISTANCE, 2.0D)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 2.0D)
              //  .add(getAttributeHolder(ModAttributes.FALL_DAMAGE_MULTIPLIER.get()), 1.0D)
                .add(getAttributeHolder(ModAttributes.SCALE.get()), 1.0D)
                .add(getAttributeHolder(ModAttributes.GRAVITY.get()), 0.08D)
                .add(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()), 0.6D);
                //.add(getAttributeHolder(ModAttributes.MOVEMENT_EFFICIENCY.get()), 1.0D)
                //.add(getAttributeHolder(ModAttributes.BURNING_TIME.get()), 1.0D);
    }

    private static Holder<Attribute> getAttributeHolder(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute)
                .flatMap(BuiltInRegistries.ATTRIBUTE::getHolder)
                .orElseThrow(() -> new IllegalArgumentException("Attribute not registered: " + attribute));
    }

    @Override
    public float getScale() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.SCALE.get()));
        return instance != null ? (float) instance.getValue() : 1.0F;
    }

    @Override
    public double getDefaultGravity() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.GRAVITY.get()));
        return instance != null ? instance.getValue() : 0.08D;
    }

    @Override
    public float maxUpStep() {
        AttributeInstance instance = this.getAttribute(getAttributeHolder(ModAttributes.STEP_HEIGHT.get()));
        return instance != null ? (float) instance.getValue() : super.maxUpStep();
    }

    @Override
    protected void updateTrades() {
        // This NPC doesn't use typical trades.
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        // This NPC doesn't use typical trades.
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null; // This NPC is not meant to breed.
    }
}