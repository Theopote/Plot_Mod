package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalBlockSpecsTest {

    @Test
    void verticalLightningRodFacesUp() {
        assertEquals(
            "minecraft:lightning_rod[facing=up]",
            DirectionalBlockSpecs.verticalLightningRod().toSetBlockArgument());
    }

    @Test
    void memberLightningRodUsesWorldDelta() {
        assertEquals(
            "minecraft:lightning_rod[facing=up]",
            DirectionalBlockSpecs.lightningRodAlongMember(0, 3, 0).toSetBlockArgument());
        assertEquals(
            "minecraft:lightning_rod[facing=east]",
            DirectionalBlockSpecs.lightningRodAlongMember(4, 0, 0).toSetBlockArgument());
        assertEquals(
            "minecraft:lightning_rod[facing=south]",
            DirectionalBlockSpecs.lightningRodAlongMember(0, 0, 5).toSetBlockArgument());
    }

    @Test
    void lightningRodMapsPlanXToEastWest() {
        assertEquals(
            "minecraft:lightning_rod[facing=east]",
            DirectionalBlockSpecs.lightningRodAlong(new Vec2d(1, 0)).toSetBlockArgument());
        assertEquals(
            "minecraft:lightning_rod[facing=west]",
            DirectionalBlockSpecs.lightningRodAlong(new Vec2d(-1, 0)).toSetBlockArgument());
    }

    @Test
    void lightningRodMapsPlanZToSouthNorth() {
        assertEquals(
            "minecraft:lightning_rod[facing=south]",
            DirectionalBlockSpecs.lightningRodAlong(new Vec2d(0, 1)).toSetBlockArgument());
        assertEquals(
            "minecraft:lightning_rod[facing=north]",
            DirectionalBlockSpecs.lightningRodAlong(new Vec2d(0, -1)).toSetBlockArgument());
    }

    @Test
    void verticalChainUsesYAxis() {
        assertEquals(
            "minecraft:chain[axis=y]",
            DirectionalBlockSpecs.verticalChain().toSetBlockArgument());
    }

    @Test
    void horizontalChainUsesPlanAxis() {
        assertEquals(
            "minecraft:chain[axis=x]",
            DirectionalBlockSpecs.chainAlong(new Vec2d(1, 0)).toSetBlockArgument());
        assertEquals(
            "minecraft:chain[axis=z]",
            DirectionalBlockSpecs.chainAlong(new Vec2d(0, 1)).toSetBlockArgument());
    }

    @Test
    void hangingSoulLanternUsesHangingProperty() {
        assertEquals(
            "minecraft:soul_lantern[hanging=true]",
            DirectionalBlockSpecs.hangingSoulLantern().toSetBlockArgument());
    }

    @Test
    void crossarmSlabUsesBottomType() {
        assertEquals(
            "minecraft:oak_slab[type=bottom]",
            DirectionalBlockSpecs.crossarmSlab("minecraft:oak_slab").toSetBlockArgument());
    }

    @Test
    void resolveMemberPlacementHandlesVerticalChain() {
        assertEquals(
            "minecraft:chain[axis=y]",
            DirectionalBlockSpecs.resolveMemberPlacement("minecraft:chain", 0.0, 1.0, 0.0)
                .toSetBlockArgument());
    }

    @Test
    void chainAlongVoxelPathFollowsStraightHorizontalRun() {
        List<BlockPos> path = VoxelLineRasterizer.rasterizeLine3D(0, 4, 0, 5, 4, 0);
        for (int i = 0; i < path.size(); i++) {
            assertEquals("x", DirectionalBlockSpecs.chainAlongVoxelPath(path, i).property("axis"));
        }
    }

    @Test
    void chainAlongVoxelPathTurnsAtCorner() {
        List<BlockPos> path = List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(1, 0, 1));
        assertEquals("x", DirectionalBlockSpecs.chainAlongVoxelPath(path, 0).property("axis"));
        assertEquals("x", DirectionalBlockSpecs.chainAlongVoxelPath(path, 1).property("axis"));
        assertEquals("z", DirectionalBlockSpecs.chainAlongVoxelPath(path, 2).property("axis"));
    }

    @Test
    void usesAxisChainPlacementMatchesChainLikeIds() {
        assertTrue(DirectionalBlockSpecs.usesAxisChainPlacement("minecraft:chain"));
        assertTrue(DirectionalBlockSpecs.usesAxisChainPlacement("examplemod:gold_chain"));
        assertTrue(!DirectionalBlockSpecs.usesAxisChainPlacement("minecraft:iron_bars"));
    }

    @Test
    void ironBarsAlongVoxelPathConnectsDiagonalNeighbors() {
        List<BlockPos> path = VoxelLineRasterizer.rasterizeLine3D(0, 0, 0, 2, 0, 2);
        boolean hasTurn = false;
        for (int i = 1; i < path.size() - 1; i++) {
            BlockSpec spec = DirectionalBlockSpecs.ironBarsAlongVoxelPath(path, i);
            if (spec.properties().size() >= 2) {
                hasTurn = true;
                break;
            }
        }
        assertTrue(hasTurn, "diagonal path should include a corner voxel with two iron_bars connections");
    }

    @Test
    void ironBarsTowardCoreLinksThicknessOffset() {
        BlockSpec spec = DirectionalBlockSpecs.ironBarsTowardCore(
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 0));
        assertEquals("true", spec.property("west"));
    }

    @Test
    void mergeIronBarsPlacementsCombinesHorizontalAxesOnly() {
        String merged = DirectionalBlockSpecs.mergeIronBarsPlacements(
            "minecraft:iron_bars[east=true]",
            "minecraft:iron_bars[north=true]");
        BlockSpec spec = BlockSpec.parse(merged);
        assertEquals("true", spec.property("east"));
        assertEquals("true", spec.property("north"));
    }

    @Test
    void mergeIronBarsIgnoresDifferentBlockTypes() {
        assertEquals(
            "minecraft:iron_block",
            DirectionalBlockSpecs.mergeIronBarsPlacements(
                "minecraft:iron_bars[east=true]",
                "minecraft:iron_block"));
    }

    @Test
    void ironBarsAlongMemberUsesHorizontalConnectionAxis() {
        assertEquals(
            "minecraft:iron_bars",
            DirectionalBlockSpecs.ironBarsAlongMember(0.0, 3.0, 0.0).toSetBlockArgument());
        assertEquals(
            "minecraft:iron_bars[east=true]",
            DirectionalBlockSpecs.ironBarsAlongMember(4.0, 0.0, 0.0).toSetBlockArgument());
        assertEquals(
            "minecraft:iron_bars[south=true]",
            DirectionalBlockSpecs.ironBarsAlongMember(0.0, 0.0, 5.0).toSetBlockArgument());
    }

    @Test
    void resolveMemberPlacementDefaultsIronBarsToPlainBlock() {
        assertEquals(
            "minecraft:iron_bars",
            DirectionalBlockSpecs.resolveMemberPlacement("minecraft:iron_bars", null, null, null)
                .toSetBlockArgument());
    }

    @Test
    void poleTopLanternIsNotHanging() {
        assertEquals(
            "minecraft:lantern[hanging=false]",
            DirectionalBlockSpecs.poleTopLantern().toSetBlockArgument());
    }

    @Test
    void rusticVineCapDrapesOnFourSides() {
        BlockSpec spec = DirectionalBlockSpecs.rusticVineCap();
        assertEquals("minecraft:vine", spec.blockId());
        assertEquals("true", spec.property("north"));
        assertEquals("true", spec.property("south"));
        assertEquals("true", spec.property("east"));
        assertEquals("true", spec.property("west"));
        assertEquals("false", spec.property("up"));
    }

    @Test
    void ironTrapdoorHubIsHorizontalWithFacing() {
        BlockSpec spec = DirectionalBlockSpecs.ironTrapdoorHorizontalHub(new Vec2d(1, 0));
        assertEquals("minecraft:iron_trapdoor", spec.blockId());
        assertEquals("east", spec.property("facing"));
        assertEquals("bottom", spec.property("half"));
        assertEquals("false", spec.property("open"));
        assertEquals(
            "minecraft:iron_trapdoor[facing=east,half=bottom,open=false]",
            spec.toSetBlockArgument());
    }
}
