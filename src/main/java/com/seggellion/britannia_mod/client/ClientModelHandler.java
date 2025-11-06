package com.seggellion.britannia_mod.client;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.client.model.DecorativeOffsetModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.slf4j.Logger;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = BritanniaMod.MODID)
public class ClientModelHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onModelModify(ModelEvent.ModifyBakingResult event) {
        final String targetPath = "decorative_weapons_1";

        event.getModels().forEach((mrl, model) -> {
            ResourceLocation id = mrl.id();
                if (mrl.variant().equals("inventory")) return;
            if (id.getNamespace().equals(BritanniaMod.MODID)
                    && id.getPath().contains(targetPath)) {

                if (!(model instanceof DecorativeOffsetModel)) {
                    DecorativeOffsetModel wrapped = new DecorativeOffsetModel(model, 1.0F, 1.0F, 0F);
                    event.getModels().replace(mrl, wrapped);
                    LOGGER.info("DecorativeOffsetModel applied to {}", mrl);
                }
            }
        });
    }
}
