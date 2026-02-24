package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class FarmingBlock extends Block implements EntityBlock {
    // 0 = Dry, 5 = Fully Hydrated
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final IntegerProperty HYDRATION = IntegerProperty.create("hydration", 0, 5);
    // 0 = None, 1 = Manure, 2 = Chemical
    public static final IntegerProperty FERTILIZER = IntegerProperty.create("fertilizer", 0, 2);
    // True if seeds are planted but waiting for a Trellis
    public static final BooleanProperty HAS_SEEDS = BooleanProperty.create("has_seeds");

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public FarmingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HYDRATION, 0)
            .setValue(FERTILIZER, 0)
            .setValue(HAS_SEEDS, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HYDRATION, FERTILIZER, HAS_SEEDS);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarmingBlockEntity(pos, state);
    }

    // --- Block Interactions ---

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        
        BlockEntity be = level.getBlockEntity(pos);
// 1. TRELLIS PLACEMENT OVERRIDE (Adventure Mode Fix)
        // We check if the player is holding the Trellis Item and clicking the Soil
        if (stack.is(ItemRegistry.TRELLIS_ITEM.get())) {
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            // Check if the space above is empty (Air or replaceable fluid)
            if (aboveState.canBeReplaced()) {
                if (!level.isClientSide) {
                    // Manually place the Trellis Block
                    level.setBlock(abovePos, BlockRegistry.TRELLIS_BLOCK.get().defaultBlockState(), 3);
                    
                    // Play a placement sound
                    level.playSound(null, abovePos, SoundEvents.BAMBOO_WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);

                    // Consume the item
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                // Return success to stop the game from attempting standard placement logic
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 1. PLANTING SEEDS LOGIC
        // We check if it is a GrapeSeed, the soil has no seeds yet, and we are interacting with the top face
        if (stack.getItem() instanceof GrapeSeedsItem) {
                // Case A: Soil is empty -> Plant the seeds
                if (!state.getValue(HAS_SEEDS)) {
                    if (be instanceof FarmingBlockEntity farmBe) {
                        if (!level.isClientSide) {
                            String variety = GrapeSeedsItem.getVariety(stack);
                            farmBe.setStoredSeed(variety);
                            
                            level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
                            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                            
                            if (!player.getAbilities().instabuild) {
                                stack.shrink(1);
                            }
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                } 
                // Case B: Soil already has seeds -> BLOCK the item from doing anything
                else {
                    if (!level.isClientSide) {
                        // Optional: Feedback to player
                        player.displayClientMessage(Component.literal("Seeds are already planted here.").withStyle(ChatFormatting.YELLOW), true);
                    }
                    // CRITICAL: Return SUCCESS so the game stops here and doesn't run the Item's "place block" logic
                    return ItemInteractionResult.SUCCESS; 
                }
            }

        // 2. WATER LOGIC
        if (stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide) {
                setHydration(level, pos, state, 5);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 3. FERTILIZER LOGIC
        if (be instanceof FarmingBlockEntity farmBe) {
            boolean applied = false;
            
            if (stack.is(Items.BONE_MEAL)) {
                if (!level.isClientSide) farmBe.addNutrients(0.6f, 0.0f, 0.0f, 0.0f);
                applied = true;
            }
            else if (stack.is(ItemRegistry.TURQUOISE_POWDER.get())) {
                if (!level.isClientSide) farmBe.addNutrients(0.0f, 0.3f, 0.0f, 0.0f);
                applied = true;
            }
            else if (stack.is(ItemRegistry.SULPHUROUS_ASH.get())) {
                if (!level.isClientSide) farmBe.addNutrients(0.0f, 0.0f, 0.8f, 0.0f);
                applied = true;
            }
            else if (stack.is(Items.ROTTEN_FLESH)) {
                if (!level.isClientSide) farmBe.addNutrients(0.0f, 0.0f, 0.0f, 0.5f);
                applied = true;
            }

            if (applied) {
                if (level.isClientSide) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
                } else {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level.setBlock(pos, state.setValue(FERTILIZER, 1), 3);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private void setHydration(Level level, BlockPos pos, BlockState state, int value) {
        level.setBlock(pos, state.setValue(HYDRATION, value), 3);
        if (level.getBlockEntity(pos) instanceof FarmingBlockEntity be) {
            be.setHydration(value);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int currentHydration = state.getValue(HYDRATION);

        // 1. Hydration Decay
        if (currentHydration > 0 && random.nextFloat() < 0.10f) {
            setHydration(level, pos, state, currentHydration - 1);
        }

        // 2. Growth / Germination Logic
        if (currentHydration > 0) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FarmingBlockEntity farmBe) {

                // CHECK: Do we have seeds waiting?
                if (state.getValue(HAS_SEEDS)) {
                    BlockPos abovePos = pos.above();
                    BlockState aboveState = level.getBlockState(abovePos);

                    // REQUIREMENT: Must have a Trellis above to germinate
                    if (aboveState.getBlock() instanceof TrellisBlock) {

                        // 1. Retrieve the stored seed ID
                        String varietyId = farmBe.getStoredSeed();
                        
                        // 2. Look up the color
                        GrapeColor color = GrapeVarietyManager.getVariety(varietyId).colorType();

                        // 3. Create the Vine State WITH the color
                        BlockState vineState = BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState()
                                .setValue(GrapeVineBlock.COLOR, color); // <--- Set the color

                        level.setBlock(abovePos, vineState, 3);

                        // 4. Move Seed Data
                        BlockEntity vineBe = level.getBlockEntity(abovePos);
                        if (vineBe instanceof GrapeVineBlockEntity gvb) {
                            gvb.setVariety(varietyId);
                        }

                        // Clear Seed from Soil
                        farmBe.clearStoredSeed();
                        level.setBlock(pos, state.setValue(HAS_SEEDS, false), 3);
                    }
                }
            }
        }
    }

    // --- Harvesting Logic ---
    
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FarmingBlockEntity farmBe) {
                int quality = farmBe.calculateQualityScore();

                ItemStack grapes = new ItemStack(ItemRegistry.GRAPES.get());
                CompoundTag dataTag = new CompoundTag();
                dataTag.putInt("QualityScore", quality);
                grapes.set(DataComponents.CUSTOM_DATA, CustomData.of(dataTag));
                popResource(level, pos, grapes);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
         return !this.defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos()) 
             ? com.seggellion.britannia_mod.registry.BlockRegistry.CAVE_FLOOR_BLOCK.get().defaultBlockState() 
             : super.getStateForPlacement(context);
    }
}