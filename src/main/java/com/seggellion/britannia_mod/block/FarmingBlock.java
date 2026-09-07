package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropGrowthContext;
import com.seggellion.britannia_mod.farming.CropGrowthHabit;
import com.seggellion.britannia_mod.farming.CropHarvestTool;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.CropSupportRequirement;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.farming.FarmingCultivationGate;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.farming.FarmingSoilCare;
import com.seggellion.britannia_mod.farming.FlowerPlantingService;
import com.seggellion.britannia_mod.farming.FruitProvenance;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.FruitTreeRegistry;
import com.seggellion.britannia_mod.farming.GrainHarvestTools;
import com.seggellion.britannia_mod.farming.RootCropShovelTools;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    public static final float HYDRATION_DECAY_CHANCE = 0.10f;
    public static final int RAIN_HYDRATION_MINIMUM = 2;

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

        // The interior decorator turns the planted crop's model a quarter turn clockwise. Handled
        // here rather than in the tool because the block sees the interaction first, and a mature
        // plot would otherwise answer with its harvest-tool refusal before the tool ever ran.
        if (stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())) {
            ItemInteractionResult rotated = rotateCropVisual(level, pos);
            if (rotated != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return rotated;
            }
        }

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

        ItemInteractionResult careResult = applyCareItem(level, pos, state, player, hand, stack);
        if (careResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return careResult;
        }

        FlowerPlantingService.Outcome flowerPlanting = FlowerPlantingService.tryPlant(
                level, pos, state, player, stack
        );
        if (flowerPlanting == FlowerPlantingService.Outcome.PLANTED) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (flowerPlanting == FlowerPlantingService.Outcome.REJECTED) {
            return ItemInteractionResult.SUCCESS;
        }

        // 1. PLANTING SEEDS LOGIC
        // We check if it is a GrapeSeed, the soil has no seeds yet, and we are interacting with the top face
        if (stack.getItem() instanceof GrapeSeedsItem) {
                // Case A: Soil is empty -> Plant the seeds
                if (!state.getValue(HAS_SEEDS) && be instanceof FarmingBlockEntity farmBe && !farmBe.hasCrop()) {
                    CropDefinition grapeCrop = CropRegistry.byId("grapes").orElse(null);
                    if (grapeCrop == null) {
                        return ItemInteractionResult.FAIL;
                    }
                        if (!mayPlantHere(level, farmBe, player)) {
                            return ItemInteractionResult.SUCCESS;
                        }
                        if (!level.isClientSide) {
                            String variety = GrapeSeedsItem.getVariety(stack);
                            farmBe.plant(grapeCrop, variety);
                            
                            level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
                            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                            
                            if (!player.getAbilities().instabuild) {
                                stack.shrink(1);
                            }
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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

        if (be instanceof FarmingBlockEntity farmBe && CropRegistry.bySeed(stack.getItem()).isPresent() && !(stack.getItem() instanceof GrapeSeedsItem)) {
            return tryPlantSeed(level, pos, state, player, stack, false, "farming_block");
        }

        if (be instanceof FarmingBlockEntity farmBe) {
            if (farmBe.isMature()) {
                return tryHarvestCrop(farmBe, state, level, pos, player, stack, hand, "farming_block");
            }
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static ItemInteractionResult applyCareItem(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand, ItemStack stack) {
        if (!(state.getBlock() instanceof FarmingBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(ItemRegistry.WATERING_CAN.get())) {
            return WateringCanItem.waterFarmingBlock(level, pos, state, player, stack);
        }

        if (stack.is(Items.WATER_BUCKET)) {
            return waterWithBucket(level, pos, state, player, hand, stack);
        }

        return applyNutrients(level, pos, state, player, stack);
    }

    private static ItemInteractionResult waterWithBucket(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FarmingBlockEntity farmBe)) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            boolean improved = farmBe.getHydration() < FarmingBlockEntity.MAX_HYDRATION;
            setHydration(level, pos, state, FarmingBlockEntity.MAX_HYDRATION);
            if (improved && player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.TEND, farmBe.cropTier(), 1.0f);
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (player != null && !player.getAbilities().instabuild) {
            ItemStack emptyBucket = new ItemStack(Items.BUCKET);
            player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, emptyBucket));
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult applyNutrients(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        FarmingSoilCare.Fertilizer fertilizer = FarmingSoilCare.fertilizerFor(stack).orElse(null);
        if (fertilizer == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        CropDefinition plantedCrop = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
        float beforeFit = plantedCrop == null ? 0.0f : farmBe.nutrientFit(plantedCrop);
        boolean changed = false;
        if (!level.isClientSide) {
            changed = farmBe.addNutrients(fertilizer.nitrogen(), fertilizer.phosphorus(), fertilizer.potassium(), fertilizer.organicMatter());
        }

        if (level.isClientSide) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
        } else if (changed) {
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.setBlock(pos, state.setValue(FERTILIZER, 1), 3);
            boolean meaningful = plantedCrop == null || farmBe.nutrientFit(plantedCrop) > beforeFit + 0.001f;
            if (player != null) {
                player.displayClientMessage(Component.literal(fertilizerMessage(fertilizer.name(), farmBe)).withStyle(ChatFormatting.GREEN), true);
            }
            level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (meaningful && player instanceof ServerPlayer serverPlayer) {
                float modifier = plantedCrop == null ? 0.25f : plantedCrop.farmingSkillModifier();
                FarmingSkill.award(serverPlayer, FarmingActionType.TEND, farmBe.cropTier(), modifier);
            }
        } else if (player != null) {
            player.displayClientMessage(Component.literal("The soil cannot absorb more of that nutrient.").withStyle(ChatFormatting.YELLOW), true);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void setHydration(Level level, BlockPos pos, BlockState state, int value) {
        int clamped = Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, value));
        level.setBlock(pos, state.setValue(HYDRATION, clamped), 3);
        if (level.getBlockEntity(pos) instanceof FarmingBlockEntity be) {
            be.setHydration(clamped);
        }
    }

    public static int getSyncedHydration(Level level, BlockPos pos, BlockState state) {
        int stateHydration = state.hasProperty(HYDRATION) ? state.getValue(HYDRATION) : 0;
        if (level.getBlockEntity(pos) instanceof FarmingBlockEntity be) {
            return Math.max(0, Math.min(FarmingBlockEntity.MAX_HYDRATION, be.getHydration()));
        }
        return stateHydration;
    }

    public static boolean isRainHydrating(Level level, BlockPos pos) {
        return level.isRainingAt(pos.above());
    }

    public static boolean isOutdoorsForRain(Level level, BlockPos pos) {
        return level.canSeeSky(pos.above());
    }

    public static boolean shouldDecayHydration(int currentHydration, RandomSource random) {
        return currentHydration > 0 && hydrationDecays(currentHydration, random.nextFloat());
    }

    public static boolean hydrationDecays(int currentHydration, float randomRoll) {
        return currentHydration > 0 && randomRoll < HYDRATION_DECAY_CHANCE;
    }

    public static int hydrationAfterRain(Level level, BlockPos pos, int currentHydration) {
        return hydrationAfterRain(currentHydration, isRainHydrating(level, pos));
    }

    public static int hydrationAfterRain(int currentHydration, boolean rainingAtFlower) {
        return currentHydration < RAIN_HYDRATION_MINIMUM && rainingAtFlower
                ? RAIN_HYDRATION_MINIMUM
                : currentHydration;
    }

    private int applyRainHydrationIfOutdoors(ServerLevel level, BlockPos pos, BlockState state, int currentHydration) {
        int hydrated = hydrationAfterRain(level, pos, currentHydration);
        if (hydrated == currentHydration) {
            return currentHydration;
        }

        setHydration(level, pos, state, hydrated);
        return hydrated;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FarmingBlockEntity farmBe && farmBe.shouldReclaimCommunityPlot(level)) {
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            return;
        }

        int currentHydration = getSyncedHydration(level, pos, state);
        if (state.getValue(HYDRATION) != currentHydration) {
            setHydration(level, pos, state, currentHydration);
            state = level.getBlockState(pos);
        }

        // 1. Hydration Decay
        if (shouldDecayHydration(currentHydration, random)) {
            setHydration(level, pos, state, currentHydration - 1);
            state = level.getBlockState(pos);
            currentHydration = getSyncedHydration(level, pos, state);
        }

        currentHydration = applyRainHydrationIfOutdoors(level, pos, state, currentHydration);
        state = level.getBlockState(pos);
        currentHydration = getSyncedHydration(level, pos, state);

        // 2. Growth / Germination Logic
        if (currentHydration > 0) {
            if (be instanceof FarmingBlockEntity farmBe) {

                // Migrate legacy grape seeds that were waiting for the old vine/trellis germination path.
                if (state.getValue(HAS_SEEDS) && !farmBe.hasCrop() && farmBe.getStoredSeed() != null && !farmBe.getStoredSeed().isBlank()) {
                    CropDefinition grapeCrop = CropRegistry.byId("grapes").orElse(null);
                    if (grapeCrop != null) {
                        String varietyId = farmBe.getStoredSeed();
                        farmBe.plant(grapeCrop, varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId);
                        level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
                    }
                }

                farmBe.tickGrowth(level, pos, state, random);
            }
        }

        // A grown plant keeps the blocks it occupies even while the soil is dry, so this sits
        // outside the hydration gate above. Without it a parched arbor whose occupancy was disturbed
        // would stay hollow - walk-through and unharvestable - until somebody watered it.
        if (be instanceof FarmingBlockEntity farmBe && farmBe.hasCrop()) {
            CropDefinition planted = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
            if (planted != null && planted.tallCrop()) {
                TallCropSupport.repairStructureIfPossible(level, pos, planted, farmBe.getGrowthStage());
            }
        }
    }

    private static String fertilizerMessage(String nutrient, FarmingBlockEntity farmBe) {
        return String.format(
                "Soil absorbed %s. Nutrients: bone %.2f, turquoise %.2f, ash %.2f, flesh %.2f",
                nutrient,
                farmBe.getBoneMealNutrient(),
                farmBe.getTurquoiseNutrient(),
                farmBe.getSulphurousAshNutrient(),
                farmBe.getRottenFleshNutrient()
        );
    }

    /**
     * Private plots only accept seed from the player who tilled them. Community plots and plots that
     * predate ownership carry no owner and stay open to everyone, so this can never lock an existing
     * farm. Server-authoritative: the client is never consulted and never told who the owner is.
     */
    /**
     * Turns the crop growing in {@code plotPos} a quarter turn clockwise.
     *
     * <p>Shared with the tall parts of a plant, because the block a player can actually see and
     * click is usually not the plot: a grape arbor is clicked on its canopy, several blocks up.
     */
    public static ItemInteractionResult rotateCropVisual(Level level, BlockPos plotPos) {
        if (!(level.getBlockEntity(plotPos) instanceof FarmingBlockEntity farmBe) || !farmBe.hasCrop()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            farmBe.rotateVisualClockwise();
            level.playSound(null, plotPos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean mayPlantHere(Level level, FarmingBlockEntity farmBe, @Nullable Player player) {
        if (farmBe.mayPlant(player)) {
            return true;
        }
        if (!level.isClientSide && player != null) {
            player.displayClientMessage(
                Component.literal("This farm plot belongs to someone else.").withStyle(ChatFormatting.YELLOW), true);
        }
        return false;
    }

    public static ItemInteractionResult tryPlantSeed(Level level, BlockPos pos, BlockState state, @Nullable Player player, ItemStack stack, boolean requireTrellisCrop, String interactionSource) {
        if (!(state.getBlock() instanceof FarmingBlock)
                || !(level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        CropDefinition crop = CropRegistry.bySeed(stack.getItem()).orElse(null);
        if (crop == null || stack.getItem() instanceof GrapeSeedsItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!mayPlantHere(level, farmBe, player)) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            FarmingCultivationGate.Evaluation eligibility = FarmingCultivationGate.evaluate(player, stack.getItem());
            if (!eligibility.permitsPlanting()) {
                FarmingCultivationGate.sendDenialFeedback(player, eligibility);
                FarmingCultivationGate.synchronizeDeniedInteraction(player, level, pos);
                logPlantingFlow(interactionSource, level, pos, stack, crop, false, false,
                        "farming_gate_" + eligibility.type().name().toLowerCase(java.util.Locale.ROOT));
                return ItemInteractionResult.SUCCESS;
            }
        }

        boolean trellisCrop = isTrellisCrop(crop);
        boolean supportSatisfied = canPlantWithCurrentSupport(level, pos, crop);
        if (requireTrellisCrop && !trellisCrop) {
            if (!level.isClientSide) {
                logPlantingFlow(interactionSource, level, pos, stack, crop, supportSatisfied, false, "not_trellis_crop");
                if (player != null) {
                    player.displayClientMessage(Component.literal(crop.displayName() + " cannot be planted through a trellis.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (state.getValue(HAS_SEEDS) || farmBe.hasCrop()) {
            if (!level.isClientSide) {
                logPlantingFlow(interactionSource, level, pos, stack, crop, supportSatisfied, false, "already_planted");
                if (player != null) {
                    player.displayClientMessage(Component.literal("A crop is already planted here.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            if (!supportSatisfied) {
                logPlantingFlow(interactionSource, level, pos, stack, crop, false, false, "missing_support");
                if (player != null) {
                    player.displayClientMessage(Component.literal(requiredSupportMessage(crop)).withStyle(ChatFormatting.YELLOW), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
            FruitTreeDefinition tree = crop.treeCrop() && FruitTreeRegistry.byId(crop.id()).isPresent()
                    ? FruitTreeRegistry.byIdOrDefault(crop.id())
                    : null;
            BlockPos rootPos = tree == null ? null : pos.above();
            if (tree != null) {
                if (!level.getBlockState(rootPos).canBeReplaced()) {
                    logPlantingFlow(interactionSource, level, pos, stack, crop, true, false, "tree_space_blocked");
                    if (player != null) {
                        player.displayClientMessage(Component.literal("The " + tree.displayName().toLowerCase() + " tree needs open space above the soil.").withStyle(ChatFormatting.YELLOW), true);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }

            // Rowan questline M5: the planter is recorded with the crop, not with the plot. A
            // public plot has no owner and still has a planter, and quest credit turns on the
            // second (protocol section 2.1).
            java.util.UUID planterUuid = player == null ? null : player.getUUID();
            if (tree != null) {
                farmBe.plant(crop);
                farmBe.attributeCurrentCycleTo(planterUuid);
                level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
                level.setBlock(rootPos, tree.rootBlock().get().defaultBlockState(), 3);
                BlockEntity newBlockEntity = level.getBlockEntity(rootPos);
                if (newBlockEntity instanceof OrangeTreeRootBlockEntity orangeRoot) {
                    orangeRoot.initializeFromFarm(farmBe, level.getRandom(), tree.id());
                }
            } else {
                farmBe.plant(crop);
                farmBe.attributeCurrentCycleTo(planterUuid);
                level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
            }

            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.PLANT, crop.tier(), crop.farmingSkillModifier());
            }
            logPlantingFlow(interactionSource, level, pos, stack, crop, true, true, "planted");
            // The authoritative success point: the crop is in the block entity, the block says it
            // has seeds, and the seed has been paid for. Every refusal above -- a locked plot, the
            // cultivation gate, missing support, an occupied plot, a blocked tree -- returned
            // before this line, so none of them reports a planting.
            QuestActionEvents.cropPlant(player, level, pos, crop.id(), farmBe.cropCycleAtCurrentPlot(),
                    planterUuid, farmBe.isCommunityPlot());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static ItemInteractionResult tryHarvestCrop(FarmingBlockEntity farmBe, BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, InteractionHand hand, String interactionSource) {
        CropDefinition crop = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
        if (crop == null || !farmBe.isMature()) {
            if (!level.isClientSide) {
                logHarvestFlow(interactionSource, level, pos, farmBe, crop, toolStack, false, "not_mature_or_no_crop");
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (crop.treeCrop()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Harvest ripe fruit from the tree.").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (crop.requiresSupport() && !farmBe.hasRequiredSupport(level, pos, crop)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(crop.displayName() + " needs its support before it can be harvested.").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (!canHarvestWith(crop, toolStack)) {
            if (!level.isClientSide) {
                logHarvestFlow(interactionSource, level, pos, farmBe, crop, toolStack, false, "wrong_tool");
                if (crop.harvestTool() == CropHarvestTool.SCISSORS) {
                    player.displayClientMessage(Component.literal(crop.displayName() + " must be harvested with scissors.").withStyle(ChatFormatting.YELLOW), true);
                } else if (crop.harvestTool() == CropHarvestTool.BARE_HAND) {
                    player.displayClientMessage(Component.literal(crop.displayName() + " must be harvested with an empty hand.").withStyle(ChatFormatting.YELLOW), true);
                } else if (crop.harvestTool() == CropHarvestTool.GRAIN_BLADE) {
                    player.displayClientMessage(Component.literal(crop.displayName() + " must be harvested with a blade.").withStyle(ChatFormatting.YELLOW), true);
                } else if (crop.harvestTool() == CropHarvestTool.ROOT_SHOVEL) {
                    player.displayClientMessage(Component.literal(crop.displayName() + " must be harvested with a Britannia shovel.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (crop.tallCrop()
                && !TallCropSupport.repairStructureIfPossible(level, pos, crop, crop.maxGrowthAge())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(crop.displayName() + " needs full vertical growth before harvest.").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            int rootAgeBefore = farmBe.getRootAgeDays();
            // Rowan questline M5: read the cycle BEFORE the harvest resets or rotates it. What is
            // reported is the cycle that produced this crop, not whatever stands here afterwards.
            java.util.UUID harvestedCycle = farmBe.cropCycleAtCurrentPlot();
            java.util.UUID harvestedPlanter = farmBe.getPlanterId();
            boolean harvestedCommunityPlot = farmBe.isCommunityPlot();
            RandomSource random = level.getRandom();
            int yield = crop.minYield() + random.nextInt(Math.max(1, crop.maxYield() - crop.minYield() + 1));
            CropGrowthContext context = farmBe.createGrowthContext(level, pos, crop, player);
            int quality = CropQualityCalculator.calculateQuality(crop, context, player, farmBe.getRootAgeDays());
            ItemStack harvest = new ItemStack(crop.harvestItem().get(), yield);
            CropQualityCalculator.applyQuality(harvest, crop, quality);
            FruitProvenance.setRegionName(harvest, FarmingClimateResolver.findRegionAt(level, pos)
                    .map(region -> region.name)
                    .orElse("Unknown"));
            if ("grapes".equals(crop.id())) {
                String variety = farmBe.getStoredSeed();
                GrapesItem.setVariety(harvest, variety == null || variety.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : variety);
                GrapesItem.setRegion(harvest, FarmingClimateResolver.findRegionAt(level, pos)
                        .map(region -> region.name)
                        .orElse("Britannia"));
            }
            popResource(level, pos, harvest);
            // The authoritative success point (protocol section 2.1): the produce exists in the
            // world. Everything that could refuse this harvest -- an immature crop, the wrong
            // tool, a missing support, a tree crop, an incomplete tall crop -- returned above.
            QuestActionEvents.cropHarvest(player, level, pos, crop.id(), harvestedCycle,
                    harvestedPlanter, harvestedCommunityPlot, yield);
            if (crop.harvestTool() == CropHarvestTool.GRAIN_BLADE) {
                popResource(level, pos, new ItemStack(ItemRegistry.STRAW.get()));
            }
            if (crop.seedReturnChance() > 0.0f && random.nextFloat() < crop.seedReturnChance()) {
                popResource(level, pos, new ItemStack(crop.seedItem().get()));
            }

            if ((crop.harvestTool() == CropHarvestTool.SCISSORS
                    || crop.harvestTool() == CropHarvestTool.GRAIN_BLADE
                    || crop.harvestTool() == CropHarvestTool.ROOT_SHOVEL)
                    && !player.getAbilities().instabuild && !toolStack.isEmpty()) {
                toolStack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
            }

            int remainingFertileHarvests = farmBe.consumeSuccessfulFertileHarvest();
            if (remainingFertileHarvests == 0) {
                exhaustFertileSoil(level, pos, farmBe);
            } else {
                boolean persistentHouseAssignment = farmBe instanceof HouseFarmPlotBlockEntity housePlot
                        && housePlot.assignmentMatches(crop);
                if (crop.persistsAfterHarvest() || persistentHouseAssignment) {
                    farmBe.regrowAfterHarvest(crop);
                    level.setBlock(pos, state.setValue(HAS_SEEDS, true), 3);
                } else {
                    resetAnnualCropState(level, pos, state, farmBe);
                }
            }

            level.playSound(null, pos,
                    toolStack.is(ItemRegistry.SCISSORS.get())
                            ? ModSounds.SCISSORS_CUT.get()
                            : SoundEvents.CROP_BREAK,
                    SoundSource.BLOCKS, 0.8f, 1.0f);
            if (player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.HARVEST, crop.tier(), crop.farmingSkillModifier());
            }
            LOGGER.debug(
                    "[farming harvest] source={} target={} farm_pos={} crop_id={} age={} mature=true required_tool={} held_item={} held_tool_accepted=true perennial={} post_harvest_regrowth_age={} root_age_before={} root_age_after={} harvested=true",
                    interactionSource,
                    "trellis_block".equals(interactionSource) ? "trellis" : "farming_block",
                    pos,
                    crop.id(),
                    farmBe.getGrowthStage(),
                    crop.harvestTool(),
                    toolStack.getItem(),
                    crop.persistsAfterHarvest(),
                    crop.clampedPostHarvestRegrowthAge(),
                    rootAgeBefore,
                    farmBe.getRootAgeDays()
            );
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static ItemInteractionResult harvestTallCropFromSegment(Level level, BlockPos segmentPos, Player player, InteractionHand hand, ItemStack toolStack) {
        BlockPos anchor = TallCropSupport.findAnchor(level, segmentPos);
        if (anchor == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof FarmingBlock farmingBlock)
                || !(level.getBlockEntity(anchor) instanceof FarmingBlockEntity farmBe)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (anchorState.getBlock() instanceof HouseFarmPlotBlock
                && level instanceof ServerLevel serverLevel
                && !HouseFarmPlotBlock.mayManagePlot(serverLevel, anchor, player)) {
            player.displayClientMessage(Component.literal("You may only harvest a house farm plot you own."), true);
            return ItemInteractionResult.SUCCESS;
        }

        return tryHarvestCrop(farmBe, anchorState, level, anchor, player, toolStack, hand, "tall_crop_segment");
    }

    private static boolean canHarvestWith(CropDefinition crop, ItemStack stack) {
        return switch (crop.harvestTool()) {
            case HAND -> true;
            case BARE_HAND -> stack.isEmpty();
            case SCISSORS -> stack.is(ItemRegistry.SCISSORS.get());
            case GRAIN_BLADE -> GrainHarvestTools.isGrainHarvestBlade(stack);
            case ROOT_SHOVEL -> RootCropShovelTools.isRootCropShovel(stack);
        };
    }

    private static boolean canPlantWithCurrentSupport(Level level, BlockPos pos, CropDefinition crop) {
        return switch (crop.supportRequirement()) {
            case NONE -> true;
            case TRELLIS, LATTICE -> level.getBlockState(pos.above()).getBlock() instanceof TrellisBlock;
            case TREE_STRUCTURE -> true;
        };
    }

    private static String requiredSupportMessage(CropDefinition crop) {
        if (crop.supportRequirement() == CropSupportRequirement.TRELLIS || crop.supportRequirement() == CropSupportRequirement.LATTICE) {
            return "A trellis must be placed here for this crop to grow.";
        }
        return crop.displayName() + " cannot be planted here.";
    }

    private static boolean isTrellisCrop(CropDefinition crop) {
        return crop.growthHabit() == CropGrowthHabit.TRELLIS_CROP
                || crop.supportRequirement() == CropSupportRequirement.TRELLIS;
    }

    private static void logPlantingFlow(String interactionSource, Level level, BlockPos pos, ItemStack stack, CropDefinition crop, boolean supportSatisfied, boolean planted, String reason) {
        LOGGER.debug(
                "[farming planting] source={} target={} farm_pos={} held_item={} crop_id={} growth_habit={} required_support={} support_satisfied={} planted={} reason={}",
                interactionSource,
                "trellis_block".equals(interactionSource) ? "trellis" : "farming_block",
                pos,
                stack.getItem(),
                crop.id(),
                crop.growthHabit(),
                crop.supportRequirement(),
                supportSatisfied,
                planted,
                reason
        );
    }

    private static void logHarvestFlow(String interactionSource, Level level, BlockPos pos, FarmingBlockEntity farmBe, @Nullable CropDefinition crop, ItemStack toolStack, boolean harvested, String reason) {
        LOGGER.debug(
                "[farming harvest] source={} target={} farm_pos={} crop_id={} age={} mature={} required_tool={} held_item={} held_tool_accepted={} perennial={} post_harvest_regrowth_age={} root_age={} harvested={} reason={}",
                interactionSource,
                "trellis_block".equals(interactionSource) ? "trellis" : "farming_block",
                pos,
                crop == null ? "<none>" : crop.id(),
                farmBe.getGrowthStage(),
                farmBe.isMature(),
                crop == null ? "<none>" : crop.harvestTool(),
                toolStack.getItem(),
                crop != null && canHarvestWith(crop, toolStack),
                crop != null && crop.persistsAfterHarvest(),
                crop == null ? 0 : crop.clampedPostHarvestRegrowthAge(),
                farmBe.getRootAgeDays(),
                harvested,
                reason
        );
    }

    public static void resetAnnualCropState(Level level, BlockPos pos, BlockState state, FarmingBlockEntity farmBe) {
        farmBe.clearStoredSeed();
        farmBe.clearCrop();
        if (farmBe.isCommunityPlot()) {
            if (farmBe.hasRemainingFertility()) {
                farmBe.startCommunitySeedWindow(level.getGameTime() + FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS);
                level.setBlock(pos, state.setValue(HAS_SEEDS, false), 3);
                return;
            }
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            return;
        }
        if (state.getBlock() instanceof FarmingBlock) {
            level.setBlock(pos, state.setValue(HAS_SEEDS, false), 3);
        }
    }

    /** Reuses the established non-fertile states after the fifth paid harvest. */
    public static boolean exhaustFertileSoil(Level level, BlockPos pos, FarmingBlockEntity farmBe) {
        if (!farmBe.isFertilityExhausted() || farmBe instanceof HouseFarmPlotBlockEntity) {
            return false;
        }
        boolean communityPlot = farmBe.isCommunityPlot();
        farmBe.clearStoredSeed();
        farmBe.clearCrop();
        BlockState exhaustedState = communityPlot
                ? BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState()
                : Blocks.DIRT.defaultBlockState();
        return level.setBlock(pos, exhaustedState, 3);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FarmingBlockEntity farmBe && farmBe.isMature()) {
            ItemInteractionResult result = tryHarvestCrop(farmBe, state, level, pos, player, ItemStack.EMPTY, InteractionHand.MAIN_HAND, "farming_block");
            return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    ? InteractionResult.PASS
                    : InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    // --- Harvesting Logic ---
    
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FarmingBlockEntity farmBe) {
                if (farmBe.hasCrop()) {
                    if (farmBe.isCommunityPlot()) {
                        farmBe.clearStoredSeed();
                        farmBe.clearCrop();
                    } else {
                        resetAnnualCropState(level, pos, state, farmBe);
                    }
                } else if (farmBe.getStoredSeed() != null && !farmBe.getStoredSeed().isBlank()) {
                    farmBe.clearStoredSeed();
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        boolean reclaimCommunityPlot = blockEntity instanceof FarmingBlockEntity farmBe && farmBe.isCommunityPlot();
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && reclaimCommunityPlot) {
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
        }
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player player)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe && !farmBe.isCommunityPlot()) {
            farmBe.setOwner(player.getUUID());
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
         return !this.defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos()) 
             ? com.seggellion.britannia_mod.registry.BlockRegistry.CAVE_FLOOR_BLOCK.get().defaultBlockState() 
             : super.getStateForPlacement(context);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof FarmingBlockEntity farmBe) {
            if (farmBe.hasCrop()) {
                farmBe.clearCrop();
            }
            if (!farmBe.getStoredSeed().isBlank()) {
                farmBe.clearStoredSeed();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
