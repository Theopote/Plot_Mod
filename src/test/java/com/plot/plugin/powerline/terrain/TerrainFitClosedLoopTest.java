package com.plot.plugin.powerline.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TerrainTestFixtures;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainFitClosedLoopTest {

    @Test
    void insertPoleUsesWrappedMidpointOnClosedLoop() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(50, 0),
            new Vec2d(50, 50),
            new Vec2d(0, 50)));
        line.setClosedPath(true);
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);

        PowerLineGenerationResult result = new PowerLineGenerationResult(line);
        PowerPoleSite start = new PowerPoleSite("start", new Vec2d(0, 50));
        start.setStationing(185.0);
        PowerPoleSite end = new PowerPoleSite("end", new Vec2d(0, 0));
        end.setStationing(0.0);
        result.poleSites.add(start);
        result.poleSites.add(end);

        ConductorSpanGeometry span = new ConductorSpanGeometry();
        span.setSpanId("start->end:legacy");
        span.setStartPoleSiteId("start");
        span.setEndPoleSiteId("end");
        span.addSample(new ConductorSample(0, 60, 0, new Vec2d(0, 50)));
        span.addSample(new ConductorSample(7.5, 55, 0, new Vec2d(0, 25)));
        span.addSample(new ConductorSample(15, 60, 0, new Vec2d(0, 0)));
        span.setSpanLength(15.0);
        result.conductorSpans.add(span);

        TerrainCollisionAnalysis report = TerrainFitService.analyze(
            result.toGeometryModel(),
            TerrainTestFixtures.flatTerrain(68));

        assertTrue(TerrainFitService.applyOneFix(
            line, report, result, IdentityCoordinateService.INSTANCE));
        assertEquals(1, line.getLayoutConstraints().size());
        assertEquals(192.5, line.getLayoutConstraints().getFirst().getRequiredStationing(), 1.0);
    }
}
