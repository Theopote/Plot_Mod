package com.plot.plugin.powerline.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainFitAutoPolePolicyTest {

    @Test
    void minimumInsertSpacingScalesWithMaxPoleSpacing() {
        PowerLineFootprint compact = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        compact.setMaxPoleSpacing(20.0);
        assertEquals(10.0, TerrainFitAutoPolePolicy.minimumInsertSpacing(compact), 0.001);

        PowerLineFootprint transmission = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(200, 0)));
        transmission.setMaxPoleSpacing(80.0);
        assertEquals(28.0, TerrainFitAutoPolePolicy.minimumInsertSpacing(transmission), 0.001);
    }
}
