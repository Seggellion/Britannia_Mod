package com.seggellion.britannia_mod.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallPairGeometryTest {
    static MirrorableWallBlock wall;
    static final BlockPos BASE = new BlockPos(0,80,0);
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); GameData.unfreezeData();
        wall = new MirrorableWallBlock(BlockBehaviour.Properties.of().noOcclusion());
    }
    static BlockState corner(Direction facing, boolean branch, boolean mirror) {
        return wall.defaultBlockState().setValue(DoubleWallBlock.SHAPE, WallShape.CORNER)
            .setValue(DoubleWallBlock.FACING, facing).setValue(DoubleWallBlock.BRANCH_RIGHT, branch)
            .setValue(MirrorableWallBlock.MIRRORED, mirror);
    }
    static VoxelShape shape(BlockState state, BlockScene scene, BlockPos pos) {
        return wall.getCollisionShape(state,scene,pos,CollisionContext.empty());
    }
    static void equal(VoxelShape a, VoxelShape b, String message) {
        assertFalse(Shapes.joinIsNotEmpty(a,b,BooleanOp.NOT_SAME),message);
    }
    @Test void legacyUpperCollisionUsesLowerRenderedGeometryWithoutWritingTheSave() {
        for(Direction d:Direction.Plane.HORIZONTAL) for(boolean branch:new boolean[]{false,true})
            for(boolean mirror:new boolean[]{false,true}) {
                var lower=corner(d,branch,mirror);
                var upper=corner(d.getClockWise(),!branch,!mirror).setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER);
                var scene=new BlockScene().put(BASE,lower).put(BASE.above(),upper);
                equal(shape(lower,scene,BASE),shape(upper,scene,BASE.above()),"invisible upper barrier "+lower);
                assertSame(upper,scene.getBlockState(BASE.above()),"shape query must not mutate saved state");
            }
    }
    @Test void structureMirrorsReflectBothPhysicalEdgesExactlyOnce() {
        for(Direction d:Direction.Plane.HORIZONTAL) for(boolean branch:new boolean[]{false,true})
            for(boolean mirrored:new boolean[]{false,true}) for(Mirror mirror:Mirror.values()) {
                var state=corner(d,branch,mirrored);
                VoxelShape expected=Shapes.empty();
                for(var box:shape(state,new BlockScene(),BASE).toAabbs()) {
                    expected=Shapes.or(expected,Shapes.box(
                        mirror==Mirror.FRONT_BACK?1-box.maxX:box.minX,box.minY,
                        mirror==Mirror.LEFT_RIGHT?1-box.maxZ:box.minZ,
                        mirror==Mirror.FRONT_BACK?1-box.minX:box.maxX,box.maxY,
                        mirror==Mirror.LEFT_RIGHT?1-box.minZ:box.maxZ));
                }
                equal(expected,shape(state.mirror(mirror),new BlockScene(),BASE),"double reflection "+state+mirror);
                assertSame(state,state.mirror(mirror).mirror(mirror));
            }
    }
    @Test void rotatedAndMirroredOwnerCornersRemainConnectedWithoutChangingPostVariant() {
        var owner=corner(Direction.SOUTH,false,false);
        var west=wall.defaultBlockState().setValue(DoubleWallBlock.FACING,Direction.SOUTH);
        var north=wall.defaultBlockState().setValue(DoubleWallBlock.FACING,Direction.EAST);
        for(Mirror m:Mirror.values()) for(Rotation r:Rotation.values()) {
            var transformed=owner.mirror(m).rotate(r);
            var scene=new BlockScene().put(BASE,transformed)
                .put(BASE.relative(r.rotate(m.mirror(Direction.WEST))),west.mirror(m).rotate(r))
                .put(BASE.relative(r.rotate(m.mirror(Direction.NORTH))),north.mirror(m).rotate(r));
            var actual=wall.deriveConnections(transformed,scene,BASE);
            equal(shape(transformed,scene,BASE),shape(actual,scene,BASE),"transform disconnected "+m+r);
            assertEquals(false,actual.getValue(DoubleWallBlock.BRANCH_RIGHT),"one-post style changed "+m+r);
            assertSame(actual,wall.deriveConnections(actual,scene,BASE),"oscillating representation");
        }
    }
}
