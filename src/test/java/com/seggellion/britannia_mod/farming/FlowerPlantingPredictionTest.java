package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.util.ModTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public block-routing boundary, not the watering item's direct useOn shortcut. */
class FlowerPlantingPredictionTest {
    private static Item wateringCan;
    private static Item unknownTaggedSeed;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        wateringCan = register("britannia_mod:flower_prediction_watering_can",
                () -> new WateringCanItem(new Item.Properties()));
        unknownTaggedSeed = register("britannia_mod:flower_prediction_unknown_seed",
                () -> new Item(new Item.Properties()));
        for (var flower : FlowerRegistry.initial().definitions().values()) {
            register(flower.seedItemId().toString(), () -> new Item(new Item.Properties()));
        }
        // Preserve every existing tag while adding a deliberately unknown tagged seed.
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair ->
                tags.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())));
        tags.computeIfAbsent(ModTags.Items.FLOWER_SEEDS, ignored -> new ArrayList<>())
                .add(unknownTaggedSeed.builtInRegistryHolder());
        BuiltInRegistries.ITEM.bindTags(tags);
    }

    @Test
    void nonFlowerPredictionPassesBeforeRequiringServerContext() {
        for (ItemStack stack : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK),
                new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(wateringCan))) {
            var before = stack.copy();
            // No world/player is needed to classify a nonmatch. The old entry point returned
            // REJECTED here, which FarmingBlock turned into client SUCCESS for an empty main
            // hand; Minecraft then stopped before sending any offhand use packet.
            assertEquals(FlowerPlantingService.Outcome.NOT_A_FLOWER_SEED, predict(stack),
                    "a nonflower must leave the block route free to reach the offhand: " + stack);
            assertTrue(ItemStack.matches(before, stack), "classification mutated the held stack");
        }
    }

    @Test
    void genuineFlowerSeedsStillClaimPredictionWithoutPlantingOrConsuming() {
        for (var flower : FlowerRegistry.initial().definitions().values()) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(flower.seedItemId()), 2);
            assertEquals(FlowerPlantingService.Outcome.REJECTED, predict(stack),
                    "flower handling must remain server authoritative: " + flower.id());
            assertEquals(2, stack.getCount(), "prediction must not consume a flower seed");
        }
    }

    @Test
    void unknownTaggedFlowerSeedsStillRefuseInsteadOfFallingThrough() {
        var stack = new ItemStack(unknownTaggedSeed, 2);
        assertTrue(stack.is(ModTags.Items.FLOWER_SEEDS));
        assertEquals(FlowerPlantingService.Outcome.REJECTED, predict(stack));
        assertEquals(2, stack.getCount());
    }

    private static FlowerPlantingService.Outcome predict(ItemStack stack) {
        // Null context intentionally exercises the same non-ServerLevel boundary as a client,
        // and makes any accidental world/player access before classification fail immediately.
        return FlowerPlantingService.tryPlant(null, null, null, null, stack);
    }

    private static Item register(String id, Supplier<Item> factory) {
        var key = ResourceLocation.parse(id);
        return BuiltInRegistries.ITEM.getOptional(key)
                .orElseGet(() -> Registry.register(BuiltInRegistries.ITEM, key, factory.get()));
    }
}
