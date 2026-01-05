package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.EquipmentSlot; 
import com.seggellion.britannia_mod.client.RegionCache;


import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;


import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class GrapeVineBlock extends CropBlock implements EntityBlock {
    // 0=Bottom, 1=Mid, 2=Top
    public static final IntegerProperty HEIGHT_STAGE = IntegerProperty.create("height_stage", 0, 2);
    public static final EnumProperty<GrapeColor> COLOR = EnumProperty.create("color", GrapeColor.class);
    // NOTE: We do not need to define VINE_AGE. 
    // CropBlock already provides 'AGE' (0-7), which we access via getAgeProperty().
    private static final Logger LOGGER = LogUtils.getLogger();

    public GrapeVineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(this.getAgeProperty(), 0)
            .setValue(HEIGHT_STAGE, 0)
            .setValue(COLOR, GrapeColor.PURPLE)); // Default fallback
    }

    private static final VoxelShape VINE_SHAPE = Block.box(0, 0, 0, 16, 32, 16);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Return the constant shape regardless of age
        // This stops the "Age 7 = Full Block" calculation that causes the shadows
        return VINE_SHAPE;
    }

// --- CRITICAL: Make Unbreakable without an Axe ---
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // If the player is NOT holding an axe, return 0.0f (Infinite hardness / Unbreakable)
        if (!player.getMainHandItem().is(ItemTags.AXES)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // --- Drops Logic ---
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Handle drops manually in code (bypassing Loot Tables for specific logic)
        if (!level.isClientSide && !player.isCreative()) {
            
            // 1. Always drop the Trellis item
            popResource(level, pos, new ItemStack(ItemRegistry.TRELLIS_ITEM.get()));

            // 2. If at Max Age (7), drop the Grapes with NBT data
            if (state.getValue(this.getAgeProperty()) == this.getMaxAge()) {
                String variety = "Unknown";
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof GrapeVineBlockEntity vineBE) {
                    variety = vineBE.getVariety();
                }

                String region = RegionCache.findRegion(pos) 
                    .map(r -> r.name)
                    .orElse("Britannia");

                ItemStack grapes = new ItemStack(ItemRegistry.GRAPES.get(), 10);
                GrapesItem.setVariety(grapes, variety);
                GrapesItem.setRegion(grapes, region);
                popResource(level, pos, grapes);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // --- Data & State ---
@Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEIGHT_STAGE, COLOR); // Register the new property
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrapeVineBlockEntity(pos, state);
    }

    // --- Hardness & Tools ---
    public static Properties getProperties() {
        return Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(2.0F, 3.0F)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops()
            .randomTicks()
            .noOcclusion();
    }

    // --- Ticking Logic ---
    
    // CRITICAL FIX: Vanilla crops stop ticking when they are fully grown (Age 7).
    // We must return true so it keeps ticking to handle Vertical Growth.
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

@Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        String varietyId = "cabernet_sauvignon"; // Default fallback
 LOGGER.info("setPlacedBy detected");
        // --- NEW 1.21+ DATA COMPONENT LOGIC ---
        // Check if the stack has custom data (where NBT now lives)
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null && customData.contains("Variety")) {
            // We must parse the compound tag safely
            varietyId = customData.copyTag().getString("Variety");
        }
        // --------------------------------------

        // 2. Sync Data to BlockEntity
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GrapeVineBlockEntity vineBE) {
            vineBE.setVariety(varietyId);
        }

        // 3. Update the BlockState Color
        GrapeColor correctColor = GrapeVarietyManager.getVariety(varietyId).colorType();
        
        if (state.getValue(COLOR) != correctColor) {
            level.setBlock(pos, state.setValue(COLOR, correctColor), 3);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;

        int age = state.getValue(this.getAgeProperty());
        int height = state.getValue(HEIGHT_STAGE);

        // 1. Grow Age
        if (age < this.getMaxAge()) {
             if (random.nextInt(10) == 0) { 
                 level.setBlock(pos, state.setValue(this.getAgeProperty(), age + 1), 2);
             }
        } 
        
        // 2. Grow Upwards
        if (age > 3 && height < 2) {
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);

            if (aboveState.getBlock() instanceof TrellisBlock) {
                          LOGGER.info("TrellisBlock detected");
                 if (random.nextInt(5) == 0) { 
           
                     propagateUpwards(level, pos, abovePos, height);
                 }
            }
        }
    }

    private void propagateUpwards(ServerLevel level, BlockPos currentPos, BlockPos abovePos, int currentHeight) {
            String varietyId = "cabernet_sauvignon"; // Default
            GrapeColor color = GrapeColor.PURPLE;    // Default
 LOGGER.info("Propogating upwards");
            // 1. Get data from current block
            BlockEntity be = level.getBlockEntity(currentPos);
            if (be instanceof GrapeVineBlockEntity vineBE) {
                varietyId = vineBE.getVariety();
                // Look up the color for this variety
                LOGGER.info("Color: {}", varietyId);
                color = GrapeVarietyManager.getVariety(varietyId).colorType();
            }

            // 2. Set state above (INCLUDE COLOR!)
            BlockState nextStage = this.defaultBlockState()
                .setValue(HEIGHT_STAGE, currentHeight + 1)
                .setValue(this.getAgeProperty(), 0)
                .setValue(COLOR, color); // <--- Apply the color

            level.setBlock(abovePos, nextStage, 3);
            
            // 3. Set data above
            BlockEntity aboveBe = level.getBlockEntity(abovePos);
            if (aboveBe instanceof GrapeVineBlockEntity vineBE) vineBE.setVariety(varietyId);
        }

@Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 1. Check if the player is holding an Axe
        if (stack.is(ItemTags.AXES)) {
            
            if (!level.isClientSide) {
                // Play Break Sound
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

                // --- DROP LOGIC START ---
                // A. Always drop the Trellis item
                popResource(level, pos, new ItemStack(ItemRegistry.TRELLIS_ITEM.get()));

                // B. If at Max Age (7), drop the Grapes too so they aren't lost
                if (state.getValue(this.getAgeProperty()) == this.getMaxAge()) {
                    String variety = "Unknown";
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof GrapeVineBlockEntity vineBE) {
                        variety = vineBE.getVariety();
                    }

                    String region = RegionCache.findRegion(pos)
                        .map(r -> r.name)
                        .orElse("Britannia");

                    ItemStack grapes = new ItemStack(ItemRegistry.GRAPES.get(), 10);
                    GrapesItem.setVariety(grapes, variety);
                    GrapesItem.setRegion(grapes, region);
                    popResource(level, pos, grapes);
                }
                // --- DROP LOGIC END ---

                // Destroy Block
                level.destroyBlock(pos, false);

                // Damage the Axe
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // --- Harvesting ---
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        int currentAge = state.getValue(this.getAgeProperty());
        
        if (currentAge == this.getMaxAge()) {
            ItemStack heldItem = player.getMainHandItem();
            
            // Check for Scissors
            if (heldItem.is(ItemRegistry.SCISSORS.get())) { 
                if (!level.isClientSide) {
                    String variety = "Unknown";
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof GrapeVineBlockEntity vineBE) variety = vineBE.getVariety();

                    // Get Region
                    String region = RegionCache.findRegion(pos)
                        .map(r -> r.name)
                        .orElse("Britannia");

                    // Drop 10 Grapes
                    ItemStack grapes = new ItemStack(ItemRegistry.GRAPES.get(), 24);
                    GrapesItem.setVariety(grapes, variety);
                    GrapesItem.setRegion(grapes, region);
                    popResource(level, pos, grapes);

                    // Damage Scissors
                    heldItem.hurtAndBreak(1, player, Player.getSlotForHand(player.getUsedItemHand()));

                    // Reset to Age 4 (Leaves persist, fruit gone)
                    // Resetting to 4 means it only needs 3 stages to grow back (faster than from scratch)
                    level.setBlock(pos, state.setValue(this.getAgeProperty(), 4), 2);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
    
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND) || 
               state.getBlock() instanceof FarmingBlock;
    }

}