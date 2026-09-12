package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
