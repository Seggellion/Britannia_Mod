package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PickaxeMiningRules {
    private PickaxeMiningRules() {}

    public static boolean isAllowedMineableBlock(BlockState state) {
        return isAllowedStoneBlock(state) || isAllowedOreBlock(state);
    }

    public static boolean isAllowedStoneBlock(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.CALCITE)
                || state.is(BlockRegistry.IGNEOUS_ROCK.get())
                || state.is(BlockRegistry.METAMORPHIC_ROCK.get())
                || state.is(BlockRegistry.VOLCANIC_ROCK.get())
                || state.is(BlockRegistry.GLACIAL_ROCK.get());
    }

    public static boolean isAllowedOreBlock(BlockState state) {
        return state.is(Blocks.IRON_ORE)
                || state.is(Blocks.DEEPSLATE_IRON_ORE)
                || state.is(Blocks.GOLD_ORE)
                || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                || state.is(BlockRegistry.COPPER_ORE.get())
                || state.is(BlockRegistry.TIN_ORE.get())
                || state.is(BlockRegistry.SILVER_ORE.get())
                || state.is(BlockRegistry.GOLD_ORE.get())
                || state.is(BlockRegistry.SHADOW_IRON_ORE.get())
                || state.is(BlockRegistry.AGAPITE_ORE.get())
                || state.is(BlockRegistry.VERITE_ORE.get())
                || state.is(BlockRegistry.VALORITE_ORE.get())
                || state.is(BlockRegistry.HIGH_PURITY_SILVER_ORE.get());
    }
}
