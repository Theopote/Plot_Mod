package com.plot.plugin.road.pipeline.furniture;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Street furniture such as streetlights along the road corridor.
 */
public final class RoadFurnitureGenerator {
    private RoadFurnitureGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        ResolvedCrossSection crossSection = ctx.request().crossSection();
        Integer spacing = crossSection.streetlightSpacing;
        if (spacing == null || spacing <= 0) {
            return;
        }

        generateStreetlights(
            ctx.solids(),
            ctx.pathPoints(),
            ctx.terrain(),
            crossSection,
            ctx.unitsPerBlock());
    }

    private static void generateStreetlights(
            RoadSolidModel solids,
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        int spacing = crossSection.streetlightSpacing;
        double skipDistance = crossSection.carriagewayWidth * unitsPerBlock;
        double offset = (RoadDimensionUtils.maxLateralOffset(crossSection.carriagewayWidth)
            + crossSection.outerBandBlockCount()
            + 0.5) * unitsPerBlock;

        List<Vec2d> samples = RoadGeometryUtils.sampleAlongPath(pathPoints, spacing, skipDistance);
        boolean placeLeft = true;

        for (Vec2d sample : samples) {
            int index = Math.max(0, Math.min(pathPoints.size() - 2,
                RoadGeometryUtils.findNearestSegmentIndex(pathPoints, sample)));
            Vec2d direction = pathPoints.get(index + 1).subtract(pathPoints.get(index)).normalize();
            Vec2d normal = new Vec2d(-direction.y, direction.x);
            double side = placeLeft ? offset : -offset;
            Vec2d lightPos = sample.add(normal.multiply(side));
            int groundY = terrain.sampleSurfaceY(lightPos);
            solids.add(lightPos, groundY + 1, RoadSolidLayer.STREETLIGHT, "minecraft:lantern");
            placeLeft = !placeLeft;
        }
    }
}
