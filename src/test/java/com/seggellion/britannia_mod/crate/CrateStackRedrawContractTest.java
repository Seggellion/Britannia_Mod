package com.seggellion.britannia_mod.crate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.lang.reflect.Method;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The column must redraw itself when a client hears it changed.
 *
 * <h2>Why this is a reflection test and not a behavioural one</h2>
 *
 * <p>What actually needs proving is that a chunk section gets rebuilt, and a section only exists
 * inside a running client. There is no client here, so the strongest available guard is over the seam
 * itself: {@link CrateStackBlockEntity} must own both client-side load paths rather than inheriting
 * them.
 *
 * <p>That matters because inheriting them is precisely the bug this pins. NeoForge's defaults read
 * the tag and stop, which is right for a block entity drawn every frame by a renderer and wrong for
 * one baked into terrain — a crate appended inside the cells a column already occupies changes no
 * block, so nothing else in the pipeline will ever mark the section dirty and the crate is simply not
 * drawn. Deleting either override reintroduces an invisible crate that is otherwise completely
 * functional, which is a hard defect to find twice.
 */
class CrateStackRedrawContractTest {

    @Test
    @DisplayName("the column handles its own data packet so it can ask for a redraw")
    void declaresOnDataPacket() throws NoSuchMethodException {
        Method method = CrateStackBlockEntity.class.getMethod(
                "onDataPacket",
                Connection.class,
                ClientboundBlockEntityDataPacket.class,
                HolderLookup.Provider.class);

        assertEquals(CrateStackBlockEntity.class, method.getDeclaringClass(),
                "CrateStackBlockEntity must override onDataPacket; inheriting NeoForge's default "
                        + "loads the new column without ever marking the chunk section dirty, and a "
                        + "same-cell append then stays invisible");
    }

    @Test
    @DisplayName("the column handles its own update tag so a fresh chunk draws what it holds")
    void declaresHandleUpdateTag() throws NoSuchMethodException {
        Method method = CrateStackBlockEntity.class.getMethod(
                "handleUpdateTag", CompoundTag.class, HolderLookup.Provider.class);

        assertEquals(CrateStackBlockEntity.class, method.getDeclaringClass(),
                "CrateStackBlockEntity must override handleUpdateTag for the same reason as "
                        + "onDataPacket: the column arrives with the chunk and has to ask to be drawn");
    }
}
