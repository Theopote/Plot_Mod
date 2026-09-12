package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionalBlockSpecsTest {

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
}
