package com.plot.plugin.road.pipeline.facility;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.StationFacilityBuildContext;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;

/**
 * 沿桩号采样放置挡土墙、护栏、排水等附属设施 voxel。
 */
public final class RoadStationFacilityGenerator {

    private static final String DEFAULT_GUARDRAIL_MATERIAL = "minecraft:oak_fence";
    private static final String DEFAULT_RETAINING_WALL_MATERIAL = "minecraft:cobblestone";
    private static final String DEFAULT_DRAINAGE_MATERIAL = "material.plot.gravel";
    private static final double DEFAULT_RETAINING_WALL_HEIGHT = 2.0;

    private RoadStationFacilityGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        StationFacilityBuildContext stationContext = ctx.request().stationFacilities();
        if (!stationContext.isActive()) {
            return;
        }

        generateAlongStations(
            ctx.solids(),
            ctx.segments(),
            ctx.heightInfos(),
            ctx.request().crossSections(),
            stationContext.road(),
            stationContext.oriented(),
            stationContext.roadEndStation(),
            ctx.pathLength(),
            StationFacilityJunctionTrim.forEdge(
                stationContext.network(),
                stationContext.road(),
                stationContext.network().getEdge(stationContext.edgeId()),
                ctx.request().crossSection(),
                host.config(),
                ctx.unitsPerBlock()),
            ctx.unitsPerBlock(),
            ctx.request().designElevation(),
            host::resolveBlockId,
            host::snapEndpointElevation);
    }

    static void generateAlongStations(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            Road road,
            OrientedRoadSegment oriented,
            double roadEndStation,
            double edgeLength,
            StationFacilityJunctionTrim.FacilityEndpointTrim trim,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            MaterialResolver materialResolver,
            ElevationSnapper elevationSnapper) {
        if (trim == null) {
            trim = StationFacilityJunctionTrim.FacilityEndpointTrim.NONE;
        }
        if (solids == null || segments == null || heightInfos == null || crossSections == null || road == null
                || oriented == null) {
            return;
        }

        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    info,
                    geometryLocal,
                    t);
                targetY = elevationSnapper.snap(center, targetY);
                double chainage = oriented.roadStationAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!trim.shouldPlace(geometryLocal, edgeLength)) {
                    continue;
                }
                double drainageOffset = crossSection.outerDrainageOffset() * scale;
                double outerOffset = (RoadDimensionUtils.maxLateralOffset(crossSection.carriagewayWidth)
                    + crossSection.outerBandBlockCount()
                    + 0.5) * scale;
                placeActiveFacilities(
                    solids,
                    center,
                    leftNormal,
                    targetY,
                    chainage,
                    road,
                    roadEndStation,
                    drainageOffset,
                    outerOffset,
                    scale,
                    materialResolver);
            }
            geometryLocalBase += segment.distance;
        }
    }

    private static void placeActiveFacilities(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            double chainage,
            Road road,
            double roadEndStation,
            double drainageOffset,
            double outerOffset,
            double unitsPerBlock,
            MaterialResolver materialResolver) {
        for (StationFacilityRun run : StationFacilityResolver.activeAt(road, chainage, roadEndStation)) {
            switch (run.getKind()) {
                case GUARDRAIL -> placeGuardrail(
                    solids, center, leftNormal, targetY, run, outerOffset, materialResolver);
                case RETAINING_WALL -> placeRetainingWall(
                    solids, center, leftNormal, targetY, run, outerOffset, materialResolver);
                case DRAINAGE -> placeDrainage(
                    solids, center, leftNormal, targetY, run, drainageOffset, unitsPerBlock, materialResolver);
                default -> {
                }
            }
        }
    }

    private static void placeGuardrail(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            StationFacilityRun run,
            double outerOffset,
            MaterialResolver materialResolver) {
        String blockId = resolveMaterial(run, DEFAULT_GUARDRAIL_MATERIAL, materialResolver);
        placeOnSides(
            solids,
            center,
            leftNormal,
            targetY + 1,
            run.getSide(),
            outerOffset,
            RoadSolidLayer.GUARDRAIL,
            blockId);
    }

    private static void placeRetainingWall(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            StationFacilityRun run,
            double outerOffset,
            MaterialResolver materialResolver) {
        String blockId = resolveMaterial(run, DEFAULT_RETAINING_WALL_MATERIAL, materialResolver);
        double height = run.getHeight() != null ? run.getHeight() : DEFAULT_RETAINING_WALL_HEIGHT;
        int blockCount = Math.max(1, (int) Math.ceil(height));
        for (int lift = 0; lift < blockCount; lift++) {
            int elevation = targetY - blockCount + 1 + lift;
            placeOnSides(
                solids,
                center,
                leftNormal,
                elevation,
                run.getSide(),
                outerOffset,
                RoadSolidLayer.RETAINING_WALL,
                blockId);
        }
    }

    private static void placeDrainage(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            StationFacilityRun run,
            double drainageOffset,
            double unitsPerBlock,
            MaterialResolver materialResolver) {
        String blockId = resolveMaterial(run, DEFAULT_DRAINAGE_MATERIAL, materialResolver);
        int drainY = targetY - 1;
        switch (run.getSide()) {
            case LEFT -> {
                Vec2d left = center.add(leftNormal.multiply(drainageOffset));
                solids.addLateralStrip(left, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
            }
            case RIGHT -> {
                Vec2d right = center.subtract(leftNormal.multiply(drainageOffset));
                solids.addLateralStrip(right, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
            }
            case BOTH -> {
                Vec2d left = center.add(leftNormal.multiply(drainageOffset));
                Vec2d right = center.subtract(leftNormal.multiply(drainageOffset));
                solids.addLateralStrip(left, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
                solids.addLateralStrip(right, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
            }
            default -> {
            }
        }
    }

    private static void placeOnSides(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int elevation,
            RoadFacilitySide side,
            double offset,
            RoadSolidLayer layer,
            String blockId) {
        switch (side) {
            case LEFT -> solids.add(center.add(leftNormal.multiply(offset)), elevation, layer, blockId);
            case RIGHT -> solids.add(center.subtract(leftNormal.multiply(offset)), elevation, layer, blockId);
            case BOTH -> {
                solids.add(center.add(leftNormal.multiply(offset)), elevation, layer, blockId);
                solids.add(center.subtract(leftNormal.multiply(offset)), elevation, layer, blockId);
            }
            default -> {
            }
        }
    }

    private static String resolveMaterial(
            StationFacilityRun run,
            String defaultMaterial,
            MaterialResolver materialResolver) {
        String material = run.getMaterial();
        if (material == null || material.isBlank()) {
            material = defaultMaterial;
        }
        return materialResolver.resolve(material);
    }

    @FunctionalInterface
    interface MaterialResolver {
        String resolve(String material);
    }

    @FunctionalInterface
    interface ElevationSnapper {
        int snap(Vec2d center, int targetY);
    }
}
