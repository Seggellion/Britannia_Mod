package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.structure.StoreSignBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;


public class SignBlockRegistry {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, "britannia_mod");
  private static final Logger LOGGER = LogUtils.getLogger();

    public static final Map<HouseSignBlock.SignType, DeferredHolder<Block, ? extends Block>> STORE_SIGN_BLOCKS = new HashMap<>();

    public static void register(IEventBus eventBus) {

        for (HouseSignBlock.SignType type : HouseSignBlock.SignType.values()) {
            if (!isStoreSign(type)) continue;

            String id = "store_sign_" + type.name().toLowerCase();
            var holder = BLOCKS.register(id, () -> new StoreSignBlock(type));
            STORE_SIGN_BLOCKS.put(type, holder);
        }

        BLOCKS.register(eventBus);
    }

    public static boolean isStoreSign(HouseSignBlock.SignType type) {
        return switch (type) {
            case BANK, GUILD_BARDIC, TAILOR, LIBRARY, BAKER, HEALER, INN, BARD, TAVERN,
                 PROVISIONER, MAGE, SHIPWRIGHT, FLETCHER, REAGENTS, BOWYER, TINKER,
                 CARPENTER, STABLES, ARMORER, BLACKSMITH, BLANK, BUTCHER, CUSTOMS,
                 BARBER, JEWELER, ARTIST, THEATRE, BEEKEEPER, MERCHANT,
                 GUILD_ARCHERS, GUILD_ARMAMENTS, GUILD_ARMORERS, GUILD_ASSASSINS,
                 GUILD_BARTERS, GUILD_BLACKSMITHS, GUILD_CALVARY, GUILD_COOKS,
                 GUILD_FIGHTERS, GUILD_FISHERMEN, GUILD_HEALERS, GUILD_ILLUSIONIST,
                 GUILD_MAGES, GUILD_MINERS, GUILD_PROVISIONERS, GUILD_ROGUES,
                 GUILD_SAILORS, GUILD_SEAMENS, GUILD_SHIPWRIGHT, GUILD_SORCERERS,
                 GUILD_TAILORS, GUILD_THIEVES, GUILD_TINKERS, GUILD_TRADERS,
                 GUILD_WARRIORS, GUILD_WEAPONS, HOBANGER, DEFAULT -> true;
            default -> false;
        };
    }
}
