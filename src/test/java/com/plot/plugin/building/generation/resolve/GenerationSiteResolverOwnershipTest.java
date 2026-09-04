package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenerationSiteResolverOwnershipTest {

    @Test
    void manualRequestIsNotCollapsedIntoPadField() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0), new Vec2d(8, 0), new Vec2d(8, 6), new Vec2d(0, 6)
        ), true);
        fp.setManualBaseElevation(72);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(fp);
        GenerationSiteResolver.ResolvedSiteElevation site =
            BuildingGenerationContextFactory.resolveForTesting(definition, new BuildingGenerationResult()).site();

        assertEquals(72, site.requestedBaseElevation());
        assertNull(site.resolvedPadElevation());
        assertEquals(72, site.actualFoundationElevation());
        assertEquals(FoundationElevationSource.MANUAL, site.source());
        // 三分开：requested 与 pad 不得被写成同一个“神秘整数”
        assertNotEquals(site.requestedBaseElevation(), site.resolvedPadElevation());
    }

    @Test
    void terrainPathLeavesRequestedAndPadNull() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0), new Vec2d(8, 0), new Vec2d(8, 6), new Vec2d(0, 6)
        ), true);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(fp);
        GenerationSiteResolver.ResolvedSiteElevation site =
            BuildingGenerationContextFactory.resolveForTesting(definition, new BuildingGenerationResult()).site();

        assertNull(site.requestedBaseElevation());
        assertNull(site.resolvedPadElevation());
        assertEquals(FoundationElevationSource.TERRAIN, site.source());
        assertEquals(site.terrainSampledElevation(), site.actualFoundationElevation());
    }
}
