package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.design.structure.TowerStation;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerFoundationResolverTest {

    @Test
    void usesHighestCornerBuildBaseAsReference() {
        TowerStation base = new TowerStation("base", 0.0, 2.0, 2.0);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(100, 200), new Vec2d(1, 0), 64);
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint.x > 100.5) {
                    return 62;
                }
                if (planPoint.x < 99.5) {
                    return 66;
                }
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return sampleSurfaceY(planPoint);
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return OptionalInt.empty();
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };

        TowerFoundationPlan plan = TowerFoundationResolver.resolve(base, frame, terrain);

        assertEquals(66, plan.referenceBuildBaseY());
        assertEquals(4, plan.unevenDeltaBlocks());
        assertTrue(TowerFoundationResolver.exceedsUnevenWarningThreshold(plan));
        assertTrue(plan.requiresLegFill(2));
    }
}
