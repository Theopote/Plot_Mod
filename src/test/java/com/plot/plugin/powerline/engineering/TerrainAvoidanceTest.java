package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainAvoidanceTest {

    @Test
    void detectsWireBelowTerrainSurface() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.setStartPoleSiteId("a");
        span.setEndPoleSiteId("b");
        span.addSample(new ConductorSample(10.0, 66.0, 0.0, new Vec2d(10, 0)));
        span.setSpanLength(20.0);

        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);

        TerrainSampler terrain = flatTerrain(68);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(geometry, terrain);
        assertTrue(TerrainAvoidance.hasTerrainIssues(report));
    }

    @Test
    void raisesSimplePoleHeightWhenNoTowerFamily() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleHeight(10.0);

        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.setStartPoleSiteId("a");
        span.setEndPoleSiteId("b");
        span.addSample(new ConductorSample(10.0, 66.0, 0.0, new Vec2d(10, 0)));
        span.setSpanLength(20.0);

        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);
        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(geometry, flatTerrain(68));

        assertTrue(TerrainAvoidance.applyOneFix(line, report, null, null));
        assertTrue(line.getPoleHeight() > 10.0);
    }

    @Test
    void passesWhenWireIsAboveTerrain() {
        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("a->b:legacy");
        span.addSample(new ConductorSample(10.0, 72.0, 0.0, new Vec2d(10, 0)));
        span.setSpanLength(20.0);

        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.addConductorSpan(span);

        LineEngineeringReport report = TerrainAvoidance.analyzeCollisions(geometry, flatTerrain(64));
        assertFalse(TerrainAvoidance.hasTerrainIssues(report));
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= y;
            }
        };
    }
}
