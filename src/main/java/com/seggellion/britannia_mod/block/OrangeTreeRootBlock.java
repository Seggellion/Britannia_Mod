package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.FruitTreeRegistry;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class OrangeTreeRootBlock extends Block implements EntityBlock {
    private final String treeTypeId;

    public OrangeTreeRootBlock(Properties properties) {
        this(FruitTreeRegistry.DEFAULT_TREE_ID, properties);
    }

    public OrangeTreeRootBlock(String treeTypeId, Properties properties) {
        super(properties);
        this.treeTypeId = treeTypeId;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        OrangeTreeRootBlockEntity root = new OrangeTreeRootBlockEntity(pos, state);
        root.setTreeTypeIdIfUnset(treeTypeId);
        return root;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof OrangeTreeRootBlockEntity root) {
            root.setTreeTypeIdIfUnset(treeTypeId);
            root.tickGrowth(level, random);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof OrangeTreeRootBlockEntity root)) {
            return ItemInteractionResult.FAIL;
        }

        if (stack.is(ItemRegistry.WATERING_CAN.get())) {
            return waterWithCan(level, pos, player, stack, root);
        }

        if (stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide) {
                root.setHydrationLevel(root.getMaxHydration());
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                awardTend(player, 1.0f);
            }
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        Fertilizer fertilizer = fertilizerFor(stack);
        if (fertilizer != null) {
            if (level.isClientSide) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
            } else {
                boolean changed = root.addNutrients(fertilizer.boneMeal, fertilizer.turquoise, fertilizer.ash, fertilizer.flesh);
                if (changed) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(Component.literal(fertilizerMessage(fertilizer.name, root)).withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    awardTend(player, 0.75f);
                } else {
                    player.displayClientMessage(Component.literal("The " + treeDisplayName(root).toLowerCase() + " tree roots cannot absorb more of that nutrient.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private ItemInteractionResult waterWithCan(Level level, BlockPos pos, Player player, ItemStack stack, OrangeTreeRootBlockEntity root) {
        if (!level.isClientSide) {
            if (root.getHydrationLevel() >= root.getMaxHydration()) {
                player.displayClientMessage(Component.literal("The " + treeDisplayName(root).toLowerCase() + " tree is already fully watered.").withStyle(ChatFormatting.YELLOW), true);
                return ItemInteractionResult.SUCCESS;
            }

            int charges = WateringCanItem.getWaterCharges(stack);
            if (charges <= 0) {
                player.displayClientMessage(Component.literal("The watering can is empty.").withStyle(ChatFormatting.YELLOW), true);
                return ItemInteractionResult.SUCCESS;
            }

            boolean improved = root.water(1);
            if (improved) {
                level.playSound(null, pos, ModSounds.WATERING_CAN_DISPENSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
                if (!player.getAbilities().instabuild) {
                    WateringCanItem.setWaterCharges(stack, charges - 1);
                }
                awardTend(player, 1.0f);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof OrangeTreeRootBlockEntity root) {
            root.cleanupTree(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void awardTend(Player player, float modifier) {
        CropDefinition crop = CropRegistry.byId(treeTypeId).orElse(null);
        if (crop != null && player instanceof ServerPlayer serverPlayer) {
            FarmingSkill.award(serverPlayer, FarmingActionType.TEND, crop.tier(), crop.farmingSkillModifier() * modifier);
        }
    }

    @Nullable
    private Fertilizer fertilizerFor(ItemStack stack) {
        if (stack.is(Items.BONE_MEAL)) {
            return new Fertilizer("bone meal", 0.6f, 0.0f, 0.0f, 0.0f);
        }
        if (stack.is(ItemRegistry.TURQUOISE_POWDER.get())) {
            return new Fertilizer("turquoise", 0.0f, 0.3f, 0.0f, 0.0f);
        }
        if (stack.is(ItemRegistry.SULPHUROUS_ASH.get())) {
            return new Fertilizer("sulphurous ash", 0.0f, 0.0f, 0.8f, 0.0f);
        }
        if (stack.is(Items.ROTTEN_FLESH)) {
            return new Fertilizer("rotten flesh", 0.0f, 0.0f, 0.0f, 0.5f);
        }
        return null;
    }

    private String fertilizerMessage(String nutrient, OrangeTreeRootBlockEntity root) {
        return String.format(
                "%s tree roots absorbed %s. Nutrients: bone %.2f, turquoise %.2f, ash %.2f, flesh %.2f",
                treeDisplayName(root),
                nutrient,
                root.getBoneMealNutrient(),
                root.getTurquoiseNutrient(),
                root.getSulphurousAshNutrient(),
                root.getRottenFleshNutrient()
        );
    }

    private String treeDisplayName(OrangeTreeRootBlockEntity root) {
        FruitTreeDefinition definition = FruitTreeRegistry.byIdOrDefault(root.getTreeTypeId());
        return definition.displayName();
    }

    private record Fertilizer(String name, float boneMeal, float turquoise, float ash, float flesh) {
    }
}
