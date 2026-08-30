package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.VariantTopOnlySlabBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoneRoofResourceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private StoneRoofResourceGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void canonicalLootAndPickaxeTagsCoverEveryPersistentVariation(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Material> materials = List.of(
                new Material(
                        "slate_roof_flat",
                        BlockRegistry.SLATE_ROOF_FLAT.get(),
                        ItemRegistry.SLATE_ROOF_FLAT_ITEM.get()),
                new Material(
                        "sandstone_roof",
                        BlockRegistry.SANDSTONE_ROOF.get(),
                        ItemRegistry.SANDSTONE_ROOF_ITEM.get()),
                new Material(
                        "limestone_roof",
                        BlockRegistry.LIMESTONE_ROOF.get(),
                        ItemRegistry.LIMESTONE_ROOF_ITEM.get()));

        ItemStack ordinaryPickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStack silkTouchPickaxe = new ItemStack(Items.IRON_PICKAXE);
        silkTouchPickaxe.enchant(level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

        for (int materialIndex = 0; materialIndex < materials.size(); materialIndex++) {
            Material material = materials.get(materialIndex);
            BlockPos relative = new BlockPos(2 + materialIndex * 3, 2, 2);
            BlockPos absolute = helper.absolutePos(relative);

            for (int variation = 0; variation < 6; variation++) {
                BlockState state = material.block().defaultBlockState()
                        .setValue(VariantTopOnlySlabBlock.VARIATION, variation);
                helper.setBlock(relative, state);
                BlockState placed = helper.getBlockState(relative);
                check(placed.is(BlockTags.MINEABLE_WITH_PICKAXE),
                        material.id() + " variation " + variation
                                + " is absent from mineable/pickaxe");

                assertCanonicalDrop(
                        material,
                        variation,
                        "ordinary",
                        Block.getDrops(
                                placed,
                                level,
                                absolute,
                                helper.getBlockEntity(relative),
                                null,
                                ordinaryPickaxe));
                assertCanonicalDrop(
                        material,
                        variation,
                        "Silk Touch",
                        Block.getDrops(
                                placed,
                                level,
                                absolute,
                                helper.getBlockEntity(relative),
                                null,
                                silkTouchPickaxe));
            }
        }

        helper.succeed();
    }

    private static void assertCanonicalDrop(
            Material material,
            int variation,
            String tool,
            List<ItemStack> drops) {
        check(drops.size() == 1,
                material.id() + " variation " + variation + " produced "
                        + drops.size() + " " + tool + " drop stacks");
        ItemStack drop = drops.get(0);
        check(drop.is(material.item()),
                material.id() + " variation " + variation + " produced "
                        + drop.getItem() + " with " + tool);
        check(drop.getCount() == 1,
                material.id() + " variation " + variation + " produced "
                        + drop.getCount() + " items with " + tool);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private record Material(String id, VariantTopOnlySlabBlock block, Item item) {
    }
}
