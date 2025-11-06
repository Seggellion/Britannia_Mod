package com.seggellion.britannia_mod.network;


import com.seggellion.britannia_mod.network.HousePlacementPayload;  // payload

import com.seggellion.britannia_mod.structure.StructurePlacer;              // placer util
import com.seggellion.britannia_mod.client.house.HouseRotationData;
import com.seggellion.britannia_mod.structure.HouseStyle;                        // enum or class
/* ───────────────────────────────────────────────────────────────────────── */

/* ───── Minecraft / Forge classes ───────────────────────────────────────── */
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


public final class HousePlacementHandler {
    public static void handle(HousePlacementPayload p, ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        HouseStyle style = HouseStyle.valueOf(p.styleName());
        boolean placed = StructurePlacer.placeStructure(
                level, p.targetPos(), p.rotationDeg(), style, player);

        if (placed) {
            ItemStack stack = player.getMainHandItem();
            stack.shrink(1);
            HouseRotationData.clear(player);
        }
    }
}
