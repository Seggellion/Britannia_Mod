package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CropSeedItem extends Item {
    private final String cropId;

    public CropSeedItem(String cropId, Properties properties) {
        super(properties);
        this.cropId = cropId;
    }

    public String getCropId() {
        return cropId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof FarmingBlock) || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        CropDefinition crop = CropRegistry.byId(cropId).orElse(null);
        if (crop == null) {
            return InteractionResult.FAIL;
        }

        ItemInteractionResult result = FarmingBlock.tryPlantSeed(level, pos, state, context.getPlayer(), context.getItemInHand(), false, "crop_seed_item");
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide);
    }
}
