package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18MilestoneOneGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private Patch18MilestoneOneGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void canonicalEntriesResolveAndSerializeAfterServerBootstrap(GameTestHelper helper) {
        Map<String, Item> items = new LinkedHashMap<>();
        items.put("dirt", ItemRegistry.DIRT.get());
        items.put("dung", ItemRegistry.DUNG.get());
        items.put("empty_bowl", ItemRegistry.EMPTY_BOWL.get());
        items.put("bowl_of_dirt", ItemRegistry.BOWL_OF_DIRT.get());
        items.put("bowl_of_fertile_dirt", ItemRegistry.BOWL_OF_FERTILE_DIRT.get());
        items.put("bowl_of_water", ItemRegistry.BOWL_OF_WATER.get());

        for (Map.Entry<String, Item> entry : items.entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, entry.getKey());
            check(BuiltInRegistries.ITEM.get(id) == entry.getValue(), id + " did not resolve to its holder");
            check(entry.getValue().getDefaultMaxStackSize() == 64, id + " is not normally stackable");

            ItemStack original = new ItemStack(entry.getValue(), 7);
            Tag encoded = original.save(helper.getLevel().registryAccess());
            ItemStack decoded = ItemStack.parse(helper.getLevel().registryAccess(), encoded).orElseThrow();
            check(decoded.is(entry.getValue()), id + " changed identity during ItemStack serialization");
            check(decoded.getCount() == 7, id + " changed count during ItemStack serialization");
        }

        ResourceLocation dungId = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "dung");
        check(BuiltInRegistries.BLOCK.get(dungId) == BlockRegistry.DUNG.get(),
                "britannia_mod:dung block did not resolve to its holder");
        check(BlockRegistry.DUNG.get().asItem() == Items.AIR,
                "dung block unexpectedly acquired a BlockItem/player-placement path");
        check(ItemRegistry.DUNG.get() != BlockRegistry.DUNG.get().asItem(),
                "dung commodity must be independent of the non-placeable world block");
        check(ItemRegistry.EMPTY_BOWL.get() != Items.BOWL,
                "UltimaCraft empty bowl resolved to the vanilla bowl item");
        check(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                        BritanniaMod.MODID, "fertilized_dirt")) == ItemRegistry.FERTILIZED_DIRT.get(),
                "existing canonical fertilized dirt was not preserved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dungBlockDropsExactlyOneCanonicalCommodity(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.DUNG.get());
        check(helper.getLevel().destroyBlock(absolute, true), "dung block could not be destroyed");

        helper.runAfterDelay(2, () -> {
            int dung = helper.getLevel().getEntitiesOfClass(
                            ItemEntity.class, new AABB(absolute).inflate(2.0D)).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(ItemRegistry.DUNG.get()))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            check(dung == 1, "dung block dropped " + dung + " canonical dung items instead of exactly one");
            helper.succeed();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
