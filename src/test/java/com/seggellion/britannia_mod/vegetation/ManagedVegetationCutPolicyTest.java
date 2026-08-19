package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.item.QualitySwordItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationCutPolicyTest {
    private static final ResourceLocation GRASS = ManagedVegetationProfile.GRASS_FAMILY_ID;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eitherTallGrassHalfResolvesToExactlyOneNode() {
        ManagedVegetationSavedData data = new ManagedVegetationSavedData();
        BlockPos base = new BlockPos(4, 65, 4);
        ManagedVegetationNode node = ManagedVegetationNode.regrowing(base, 1L).tallGrass(GRASS);
        data.register(node);

        assertEquals(node, ManagedVegetationService.resolveNode(data, base).orElseThrow());
        assertEquals(node, ManagedVegetationService.resolveNode(data, base.above()).orElseThrow());
        assertTrue(ManagedVegetationService.resolveNode(data, base.above(2)).isEmpty());
    }

    @Test
    void ownershipChecksAreLifecycleSpecificAndDoNotClaimUnmanagedPlants() {
        assertTrue(ManagedVegetationService.isOwnedState(
                ManagedVegetationLifecycle.SHORT_GRASS, Blocks.SHORT_GRASS.defaultBlockState()
        ));
        assertTrue(ManagedVegetationService.isOwnedState(
                ManagedVegetationLifecycle.TALL_GRASS, Blocks.TALL_GRASS.defaultBlockState()
        ));
        assertFalse(ManagedVegetationService.isOwnedState(
                ManagedVegetationLifecycle.SHORT_GRASS, Blocks.FERN.defaultBlockState()
        ));
    }

    @Test
    void everyVanillaOrUltimaCraftSwordIsAcceptedAsACuttingSword() {
        assertTrue(ManagedVegetationCutTools.isSword(new ItemStack(Items.WOODEN_SWORD)));
        assertTrue(ManagedVegetationCutTools.isSword(new ItemStack(Items.NETHERITE_SWORD)));
        assertFalse(ManagedVegetationCutTools.isSword(new ItemStack(Items.WOODEN_AXE)));
        assertTrue(SwordItem.class.isAssignableFrom(QualitySwordItem.class));
    }

    @Test
    void authorizationMatrixKeepsPlayersSwordOnlyAndElevatedUsersToolAgnostic() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack arbitrary = new ItemStack(Items.STICK);

        assertTrue(ManagedVegetationCutTools.isAuthorized(false, false, sword));
        assertFalse(ManagedVegetationCutTools.isAuthorized(false, false, ItemStack.EMPTY));
        assertFalse(ManagedVegetationCutTools.isAuthorized(false, false, arbitrary));
        assertTrue(ManagedVegetationCutTools.isAuthorized(true, false, ItemStack.EMPTY));
        assertTrue(ManagedVegetationCutTools.isAuthorized(true, false, arbitrary));
        assertTrue(ManagedVegetationCutTools.isAuthorized(false, true, ItemStack.EMPTY));
        assertTrue(ManagedVegetationCutTools.isAuthorized(false, true, arbitrary));
    }
}
