package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.farming.LegacyGrapeVineMigration;
import com.seggellion.britannia_mod.winery.GrapeColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Migration-only remnant of the retired standalone grape vine.
 *
 * <p>Grapes are grown as a crop in a Britannia farming plot and nowhere else. This block is not
 * obtainable, not placeable, and grows nothing; it exists purely so Minecraft can deserialize saves
 * written before the change, and so those vines can be converted by
 * {@link LegacyGrapeVineMigration}. It renders nothing, so a world still holding one shows an empty
 * space rather than the retired trellis artwork until the conversion runs.
 *
 * <p>Its state properties are kept because the conversion reads them: {@code color} is what makes
 * all eight grape varieties recoverable from an old save. Do not add gameplay here — once no
 * supported world can still contain one, delete the block and its registration outright.
 */
public class GrapeVineBlock extends CropBlock implements EntityBlock {
    /** Retained only so legacy blockstates deserialize; the stack shape is otherwise unused. */
    public static final IntegerProperty HEIGHT_STAGE = IntegerProperty.create("height_stage", 0, 2);

    /** The only reliable record of which of the eight varieties a legacy vine carried. */
    public static final EnumProperty<GrapeColor> COLOR = EnumProperty.create("color", GrapeColor.class);

    private static final VoxelShape MIGRATION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public GrapeVineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(this.getAgeProperty(), 0)
            .setValue(HEIGHT_STAGE, 0)
            .setValue(COLOR, GrapeColor.PURPLE));
    }

    public static Properties getProperties() {
        return Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(2.0F, 3.0F)
            .sound(SoundType.WOOD)
            .randomTicks()
            .noOcclusion();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEIGHT_STAGE, COLOR);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrapeVineBlockEntity(pos, state);
    }

    /**
     * The random tick is the conversion's only unprompted trigger. It costs nothing until a chunk
     * holding a legacy vine is actually loaded, and it cannot run twice because the first pass
     * removes the block.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LegacyGrapeVineMigration.migrate(level, pos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Touching an unconverted vine converts it immediately rather than waiting for a random tick. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        LegacyGrapeVineMigration.migrate(level, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        LegacyGrapeVineMigration.migrate(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Never pops off on its own. {@code CropBlock} would otherwise break a vine whose light or soil
     * no longer qualifies, destroying state the conversion still needs to read.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MIGRATION_SHAPE;
    }
}
