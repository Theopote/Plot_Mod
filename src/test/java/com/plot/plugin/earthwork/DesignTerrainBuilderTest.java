package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.grading.DesignTerrainBuilder;
import com.plot.plugin.earthwork.model.EarthworkSite;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.twoZoneSiteForCompose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DesignTerrainBuilderTest {

    @Test
    void buildMatchesDesignTerrainComposerCompose() {
        EarthworkSite site = twoZoneSiteForCompose();
        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);

        DesignTerrainBuilder.BuildResult fromBuilder = DesignTerrainBuilder.build(site, terrain, null);
        DesignTerrainComposer.ComposeResult fromComposer = DesignTerrainComposer.compose(site, terrain, null);

        assertNotNull(fromBuilder.grid());
        assertEquals(fromComposer.grid().minTargetY(), fromBuilder.grid().minTargetY());
        assertEquals(fromComposer.grid().maxTargetY(), fromBuilder.grid().maxTargetY());
        assertEquals(
            fromComposer.grid().get(5, 5).targetY(),
            fromBuilder.grid().get(5, 5).targetY());
        assertEquals(fromComposer.zoneEvaluators().keySet(), fromBuilder.zoneEvaluators().keySet());
    }
}
