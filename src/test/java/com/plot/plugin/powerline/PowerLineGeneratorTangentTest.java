package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineGeneratorTangentTest {

    @Test
    void cornerUsesAngleBisector() {
        List<Vec2d> poles = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        Vec2d tangent = PowerLineGenerator.computePoleTangent(poles, 1, false);

        Vec2d forward = PoleFrame.fromPole(poles.get(1), tangent, 64).forward();
        assertEquals(Math.sqrt(0.5), forward.x, 1e-6);
        assertEquals(Math.sqrt(0.5), forward.y, 1e-6);
    }

    @Test
    void uTurnUsesIncomingDirectionInsteadOfWorldXFallback() {
        List<Vec2d> poles = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 10),
            new Vec2d(0, 0));
        Vec2d tangent = PowerLineGenerator.computePoleTangent(poles, 1, false);

        Vec2d forward = PoleFrame.fromPole(poles.get(1), tangent, 64).forward();
        assertEquals(0.0, forward.x, 1e-6);
        assertEquals(1.0, forward.y, 1e-6);
    }
}
