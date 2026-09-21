package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerStructureTransformTest {

    @Test
    void fromWorldInvertsToWorldForPoleLocalPoints() {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        TowerStructureTransform transform = new TowerStructureTransform(frame, IdentityCoordinateService.INSTANCE);

        TowerLocalPoint original = TowerLocalPoint.of(-4.2, 12.0, 3.1);
        double[] world = transform.toWorld(original);
        TowerLocalPoint restored = transform.fromWorld(world[0], world[1], world[2]);

        assertEquals(original.lateral(), restored.lateral(), 1e-6);
        assertEquals(original.vertical(), restored.vertical(), 1e-6);
        assertEquals(original.longitudinal(), restored.longitudinal(), 1e-6);
    }
}
