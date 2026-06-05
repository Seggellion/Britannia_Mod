package com.seggellion.britannia_mod.registry;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.block.HorizontalFacingBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FishRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "britannia_mod");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "britannia_mod");

    public static final Map<String, DeferredHolder<Block, Block>> FISH_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Item, WeightedFishItem>> FISH_ITEMS = new LinkedHashMap<>();

    private static final BlockBehaviour.Properties FISH_BLOCK_PROPS =
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_SAND).noOcclusion();

    private static final List<String> FISH_IDS = new ArrayList<>(List.of(
        "mud_puppy",
        "red_herring",
        "amberjack",
        "black_seabass",
        "blue_grouper",
        "bluefish",
        "bluegill_sunfish",
        "bonefish",
        "bonito",
        "brook_trout",
        "cape_cod",
        "captain_snook",
        "cobia",
        "crag_snapper",
        "cutthroat_trout",
        "dark_fish",
        "demon_trout",
        "drake_fish",
        "dungeon_chub",
        "gray_snapper",
        "green_catfish",
        "grim_cisco",
        "haddock",
        "infernal_tuna",
        "kokanee_salmon",
        "lurker_fish",
        "mahi_mahi",
        "orc_bass",
        "pike",
        "pumpkinseed_sunfish",
        "rainbow_trout",
        "red_drum",
        "red_grouper",
        "red_snook",
        "redbelly_bream",
        "shad",
        "smallmouth_bass",
        "snaggletooth_bass",
        "tarpon",
        "tormented_pike",
        "uncommon_shiner",
        "walleye",
        "yellow_perch",
        "yellowfin_tuna",
        "autumn_dragonfish",
        "bull_fish",
        "crystal_fish",
        "fairy_salmon",
        "fire_fish",
        "giant_koi",
        "great_barracuda",
        "holy_mackerel",
        "lava_fish",
        "reaper_fish",
        "summer_dragonfish",
        "unicorn_fish",
        "yellowtail_barracuda",
        "abyssal_dragonfish",
        "black_marlin",
        "blue_marlin",
        "dungeon_pike",
        "giant_samurai_fish",
        "golden_tuna",
        "king_fish",
        "lantern_fish",
        "rainbow_fish",
        "seeker_fish",
        "spring_dragonfish",
        "stone_fish",
        "winter_dragonfish",
        "zombie_fish",
        "atlantic_salmon",
        "mackerel",
        "halibut",
        "sturgeon",
        "cod",
        "flying_squid"
    ));

    private FishRegistry() {}

    public static void register(IEventBus eventBus) {
        for (String id : FISH_IDS) {
            registerFish(id);
        }
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        LOGGER.info("Registered {} fish blocks and items.", FISH_IDS.size());
    }

    public static void addFishIds(String... ids) {
        for (String id : ids) {
            if (!FISH_IDS.contains(id)) FISH_IDS.add(id);
        }
    }

    public static List<String> fishIds() {
        return List.copyOf(FISH_IDS);
    }

    private static void registerFish(String id) {
        DeferredHolder<Block, Block> blockHolder =
            BLOCKS.register(id, () -> new com.seggellion.britannia_mod.block.WeightedFishBlock(FISH_BLOCK_PROPS));

        DeferredHolder<Item, WeightedFishItem> itemHolder =
                ITEMS.register(id, () -> new WeightedFishItem(blockHolder.get(), new Item.Properties()));

        FISH_BLOCKS.put(id, blockHolder);
        FISH_ITEMS.put(id, itemHolder);
    }
}
