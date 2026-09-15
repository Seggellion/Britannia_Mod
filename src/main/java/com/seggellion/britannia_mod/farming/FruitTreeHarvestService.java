package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Fruit picking and tree felling share the soil root's skill, lock and finite fertilizer budget. */
public final class FruitTreeHarvestService {
    private FruitTreeHarvestService() {}

    public static boolean pick(ServerLevel level, BlockPos pos, OrangeTreeRootBlockEntity root,
                               Player player, InteractionHand hand, ItemStack tool, boolean scissors) {
        var soil=root.getSoilBlockEntity(level).orElse(null);
        var crop=CropRegistry.byId(root.getTreeTypeId()).orElse(null);
        if(soil==null || crop==null) return false;
        BlockState original=level.getBlockState(pos);
        var result=FarmingHarvestService.execute(level,soil.getBlockPos(),player,crop.seedItem().get(),
                ()->level.getBlockEntity(root.getBlockPos())==root && !root.isRemoved()
                        && root.getSoilBlockEntity(level).orElse(null)==soil && crop.id().equals(soil.getPlantedCropId())
                        && FarmingBlock.mayHarvestSoil(level,soil,player) && level.mayInteract(player,pos)
                        && level.getBlockState(pos).equals(original) && original.is(root.definition().fruitBlock().get())
                        && original.getValue(OrangeFruitBlock.RIPE) && OrangeTreeUtils.findRoot(level,pos).orElse(null)==root
                        && player.getItemInHand(hand)==tool
                        && tool.is(scissors?ItemRegistry.SCISSORS.get():ItemRegistry.TWO_HANDED_AXE.get())
                        && (scissors || ManagedBreakAuthorization.evaluate(level,pos,player).allowed()),
                outcome->{
                    ItemStack harvest=outcome.successful()?root.createHarvestStack(player,root.definition().randomFruitYield(level.getRandom())):ItemStack.EMPTY;
                    BlockState replacement=scissors?root.definition().leafBlock().get().defaultBlockState():level.getFluidState(pos).createLegacyBlock();
                    if(!level.setBlock(pos,replacement,3)) return false;
                    root.onFruitHarvested(pos);
                    if(!outcome.free()) {
                        tool.hurtAndBreak(1,player,Player.getSlotForHand(hand));
                        if(soil.consumeSuccessfulFertileHarvest()==0) {
                            root.cleanupTree(level,player,false);
                            FarmingBlock.exhaustFertileSoil(level,soil.getBlockPos(),soil);
                        }
                    }
                    if(!harvest.isEmpty()) {
                        if(scissors) com.seggellion.britannia_mod.bowlpreparation.BowlPreparationOutput.giveOrDrop((ServerPlayer)player,harvest);
                        else Block.popResource(level,pos,harvest);
                    }
                    level.playSound(null,pos,scissors?ModSounds.SCISSORS_CUT.get():ModSounds.CHOP_TREE.get(),SoundSource.BLOCKS,.8f,1f);
                    return true;
                },()->FarmingSkill.award((ServerPlayer)player,FarmingActionType.HARVEST,crop.tier(),crop.farmingSkillModifier()));
        return result!=FarmingHarvestService.Outcome.REFUSED;
    }

    public static boolean chop(ServerLevel level, BlockPos clicked, OrangeTreeRootBlockEntity root, Player player) {
        var soil=root.getSoilBlockEntity(level).orElse(null);
        var crop=CropRegistry.byId(root.getTreeTypeId()).orElse(null);
        if(soil==null || crop==null || player==null) return false;
        ItemStack tool=player.getMainHandItem();
        BlockState original=level.getBlockState(clicked);
        var result=FarmingHarvestService.execute(level,soil.getBlockPos(),player,crop.seedItem().get(),
                ()->level.getBlockEntity(root.getBlockPos())==root && !root.isRemoved()
                        && root.getSoilBlockEntity(level).orElse(null)==soil && crop.id().equals(soil.getPlantedCropId())
                        && FarmingBlock.mayHarvestSoil(level,soil,player) && level.getBlockState(clicked).equals(original)
                        && player.getMainHandItem()==tool && tool.is(ItemRegistry.TWO_HANDED_AXE.get())
                        && hasRipeFruit(level,root)
                        && ManagedBreakAuthorization.evaluate(level,clicked,player).allowed(),
                outcome->{
                    // A qualified attempt removes the ripe tree on either outcome; failure emits no byproducts.
                    root.cleanupTreeAfterHarvest(level,player,outcome.successful());
                    if(!outcome.free()) {
                        tool.hurtAndBreak(1,player,Player.getSlotForHand(InteractionHand.MAIN_HAND));
                        if(soil.consumeSuccessfulFertileHarvest()==0) FarmingBlock.exhaustFertileSoil(level,soil.getBlockPos(),soil);
                    }
                    if(level.getBlockEntity(soil.getBlockPos())==soil && !soil.isFertilityExhausted()) soil.restartEmptySeedWindow(level.getGameTime());
                    level.playSound(null,clicked,ModSounds.CHOP_TREE.get(),SoundSource.BLOCKS,2f,2f);
                    return true;
                },()->FarmingSkill.award((ServerPlayer)player,FarmingActionType.HARVEST,crop.tier(),crop.farmingSkillModifier()));
        return result!=FarmingHarvestService.Outcome.REFUSED;
    }
    private static boolean hasRipeFruit(ServerLevel level, OrangeTreeRootBlockEntity root) {
        var plan = OrangeTreeStructurePlanner.plan(root.definition(), root.getTreeSeed(), root.definition().maxGrowthStep(), root.getBlockPos());
        for (BlockPos pos : plan.fruit()) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(root.definition().fruitBlock().get()) && state.getValue(OrangeFruitBlock.RIPE)) return true;
        }
        return false;
    }

}
