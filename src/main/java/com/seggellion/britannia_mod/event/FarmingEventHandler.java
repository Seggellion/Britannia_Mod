package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry; 
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class FarmingEventHandler {

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();

        if (level.getBlockState(pos).getBlock() instanceof FarmingBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FarmingBlockEntity farmBe) {
                
                boolean applied = false;

                // 1. Nitrogen (Bone Meal) -> 0.6
                if (stack.is(Items.BONE_MEAL)) {
                    farmBe.addNutrients(0.6f, 0.0f, 0.0f, 0.0f);
                    applied = true;
                }
                // 2. Phosphorus (Turquoise Powder) -> 0.3
                else if (stack.is(ItemRegistry.TURQUOISE_POWDER.get())) {
                    farmBe.addNutrients(0.0f, 0.3f, 0.0f, 0.0f);
                    applied = true;
                }
                // 3. Potassium (Sulphurous Ash) -> 0.8
                else if (stack.is(ItemRegistry.SULPHUROUS_ASH.get())) {
                    farmBe.addNutrients(0.0f, 0.0f, 0.8f, 0.0f);
                    applied = true;
                }
                // 4. Organic Matter (Rotten Flesh) -> 0.5
                else if (stack.is(Items.ROTTEN_FLESH)) {
                    farmBe.addNutrients(0.0f, 0.0f, 0.0f, 0.5f);
                    applied = true;
                }

                if (applied) {
                    if (!level.isClientSide) {
                        if (!event.getEntity().getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        // Optional: Add particles here to show success
                    }
                    event.setCanceled(true); // Stop standard interaction
                }
            }
        }
    }
}