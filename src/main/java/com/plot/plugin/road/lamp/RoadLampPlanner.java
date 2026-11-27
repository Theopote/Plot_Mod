package com.plot.plugin.road.lamp;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.corridor.RoadCorridorConstructionMode;
import com.plot.plugin.road.pipeline.corridor.SurfaceCell;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.station.OrientedRoadSegment;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Places complete compiled lamps against the paved columns owned by the corridor. */
public final class RoadLampPlanner {
    private RoadLampPlanner() {
    }

    public record Candidate(BlockPos support, RoadSolidLayer supportLayer, List<RoadLampCompiler.Voxel> voxels) {
    }

    private record Slot(double station, int side) {
    }

    private record Sample(Vec2d center, Vec2d left, double error, ResolvedCrossSection section) {
    }

    public static void plan(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        var crossSections = ctx.request().crossSections();
        var placement = crossSections.lightingPlacement();
        if (placement == null || !placement.enabled()) return;
        double scale = Math.max(1e-9, ctx.unitsPerBlock());
        var trim = ctx.request().junctionCorridor().endpointTrim();
        double length = ctx.segments().stream().mapToDouble(segment -> segment.distance).sum();
        double setback = placement.junctionSetback() * scale;
        double start = trim.skipStart() + (crossSections.setbackStart() ? setback : 0.0);
        double end = length - trim.skipEnd() - (crossSections.setbackEnd() ? setback : 0.0);
        var orientations = crossSections.lightingOrientations();
        var slots = new LinkedHashMap<Slot, Sample>();
        // Sampler chainage arg is geometry-local so lamp phase uses physical and VCS uses canonical.
        RoadPathStationSampler.forEach(
            ctx.segments(),
            ctx.heightInfos(),
            true,
            local -> local,
            scale,
            ctx.request().designElevation(),
            ctx.buildProfile(),
            null,
            null,
            (center, left, y, local, index) -> {
                if (local + 1e-6 < start || local > end + 1e-6 || local >= length - 1e-6) return;
                var type = ctx.detection().constructionTypes().get(index);
                if (type.isStructure() && !(type.isBridgeFamily() && placement.bridgeAllowed())) return;
                var orientation = orientationAt(orientations, local);
                // Schedule against seam-invariant physical stations so Move Seam does not reshuffle lamps.
                double station = crossSections.physicalStationAtGeometryLocal(local);
                if (!Double.isFinite(station)) {
                    return;
                }
                Vec2d roadLeft = orientation != null && !orientation.forward() ? left.multiply(-1) : left;
                double canonical = crossSections.chainageAtGeometryLocal(local);
                if (!Double.isFinite(canonical)) {
                    return;
                }
                var section = crossSections.resolve(canonical);
                double blockStation = station / scale;
                switch (placement.sides()) {
                    case LEFT -> offer(slots, section, center, roadLeft, blockStation, placement.spacing(), 1, 0);
                    case RIGHT -> offer(slots, section, center, roadLeft, blockStation, placement.spacing(), -1, 0);
                    case BOTH -> {
                        offer(slots, section, center, roadLeft, blockStation, placement.spacing(), 1, 0);
                        offer(slots, section, center, roadLeft, blockStation, placement.spacing(), -1, 0);
                    }
                    case STAGGERED -> {
                        offer(slots, section, center, roadLeft, blockStation, placement.spacing(), 1, 0);
                        offer(slots, section, center, roadLeft, blockStation, placement.spacing(), -1, placement.rightSidePhase());
                    }
                    case MEDIAN -> offer(slots, section, center, roadLeft, blockStation, placement.spacing(), 0, 0);
                }
            });
        var shape = RoadLampCompiler.compile(placement.style());
        var projection = host.coordinateTransformer() != null
            ? host.coordinateTransformer().captureProjection() : WorldProjectionSnapshot.UNKNOWN;
        slots.entrySet().stream().sorted(Comparator.comparingDouble(e -> e.getKey().station())).forEach(entry -> {
            Candidate candidate = anchor(ctx, host, entry.getValue(), entry.getKey().side(), scale,
                shape, placement, projection);
            if (candidate != null) ctx.result().roadLampCandidates.add(candidate);
        });
    }

    private static OrientedRoadSegment orientationAt(List<OrientedRoadSegment> orientations, double local) {
        for (var oriented : orientations) {
            if (local >= oriented.geometryChainStart() && local < oriented.geometryChainEnd()) {
                return oriented;
            }
        }
        return orientations.isEmpty() ? null : orientations.getLast();
    }

    private static void offer(Map<Slot, Sample> slots, ResolvedCrossSection section, Vec2d center, Vec2d left,
                               double station, int spacing, int side, double phase) {
        double scheduled = Math.round((station - phase) / spacing) * (double) spacing + phase;
        if (scheduled < 0.0) {
            return;
        }
        // 半格相位先量化到整数采样格，再与路径采样比对；与情境预览共用 nearestStationSample。
        int target = nearestStationSample(scheduled);
        double error = Math.abs(station - target);
        if (error > 0.51) {
            return;
        }
        var slot = new Slot(target, side);
        Sample existing = slots.get(slot);
        if (existing == null || error < existing.error()) {
            slots.put(slot, new Sample(center, left, error, section));
        }
    }

    /**
     * 将目标桩号落到最近的整数采样格；半格等距时取较小一侧（与沿路扫掠的「先到先得」一致）。
     * 情境预览须用此规则，避免 {@code Math.round(spacing/2)} 在奇数间距上偏到下一格。
     */
    public static int nearestStationSample(double targetStation) {
        int floor = (int) Math.floor(targetStation);
        if (targetStation <= floor) {
            return floor;
        }
        int ceil = floor + 1;
        double errFloor = targetStation - floor;
        double errCeil = ceil - targetStation;
        return errFloor <= errCeil ? floor : ceil;
    }

    public static int lateralOffset(ResolvedCrossSection section, int side, int inset) {
        int bandWidth = section.outerBandBlockCount();
        return switch (side) {
            case 1 -> RoadDimensionUtils.maxLateralOffset(section.carriagewayWidth) + bandWidth - inset;
            case -1 -> RoadDimensionUtils.minLateralOffset(section.carriagewayWidth) - bandWidth + inset;
            default -> 0;
        };
    }

    public static RoadSolidLayer supportLayer(ResolvedCrossSection section, int side, int inset) {
        if (side == 0) return section.includeMedian ? RoadSolidLayer.MEDIAN : null;
        if (inset >= section.outerBandBlockCount()) return null;
        return section.surfaceBandAtLateral(lateralOffset(section, side, inset))
            .map(ResolvedCrossSection.SurfaceBandSpec::layer)
            .filter(layer -> layer == RoadSolidLayer.SIDEWALK || layer == RoadSolidLayer.SHOULDER)
            .orElse(null);
    }

    private static Candidate anchor(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host,
                                    Sample sample, int side, double scale, List<RoadLampCompiler.Voxel> shape,
                                    RoadLampPlacement placement, WorldProjectionSnapshot projection) {
        var section = sample.section();
        RoadSolidLayer layer = supportLayer(section, side, placement.inset());
        if (layer == null) return null;
        int lateral = lateralOffset(section, side, placement.inset());
        Vec2d planPoint = sample.center().add(sample.left().multiply(lateral * scale));
        BlockPos column = host.canvasToBlockPos(planPoint);
        var key = new ColumnKey(column.getX(), column.getZ());
        SurfaceCell surface = ctx.corridorPlan().surfaceMask().get(key);
        // Raised medians are emitted by the cross-section pass, above the main pavement mask.
        if (layer == RoadSolidLayer.MEDIAN) {
            if (surface == null) return null;
            for (int dy = section.medianHeight; dy >= 1; dy--) {
                BlockPos median = new BlockPos(column.getX(), surface.surfaceY() + dy, column.getZ());
                if (ctx.result().placementLayers.get(median) == RoadSolidLayer.MEDIAN) {
                    surface = new SurfaceCell(planPoint, median.getY(), RoadSolidLayer.MEDIAN, null);
                    break;
                }
            }
        }
        if (surface == null || surface.layer() != layer || (layer != RoadSolidLayer.MEDIAN && surface.materialId() == null)) {
            return null;
        }
        var anchor = new BlockPos(column.getX(), surface.surfaceY() + 1, column.getZ());
        Vec2d inwardPoint = planPoint.add(sample.left().multiply((side == 1 ? -1 : 1) * scale * 8));
        BlockPos inward = host.canvasToBlockPos(inwardPoint);
        int dx = inward.getX() - column.getX();
        int dz = inward.getZ() - column.getZ();
        if (dx == 0 && dz == 0) return null;
        boolean xAxis = Math.abs(dx) >= Math.abs(dz);
        var voxels = RoadLampCompiler.position(shape, anchor, xAxis ? Integer.signum(dx) : 0,
            xAxis ? 0 : Integer.signum(dz));
        var junction = ctx.request().junctionCorridor();
        var grade = ctx.request().gradeSeparation();
        for (var voxel : voxels) {
            var pos = voxel.position();
            var voxelColumn = new ColumnKey(pos.getX(), pos.getZ());
            if (pos.getY() < ctx.terrain().worldBottomY() || pos.getY() >= ctx.terrain().worldTopExclusiveY()
                    || !ctx.terrain().isChunkLoaded(pos.getX(), pos.getZ())) return null;
            if (junction.worldFootprint().clearance().contains(voxelColumn)) return null;
            var clearance = ctx.corridorPlan().clearanceCell(voxelColumn);
            if (clearance != null && clearance.mode() != RoadCorridorConstructionMode.OPEN
                    && !(clearance.mode() == RoadCorridorConstructionMode.BRIDGE && placement.bridgeAllowed())) {
                return null;
            }
            Vec2d voxelPlan = projection.isValid()
                ? projection.toCanvas(new Vec2d(pos.getX() + 0.5, pos.getZ() + 0.5)) : planPoint;
            Integer deckCap = grade.maxOpenClearTopY(voxelPlan);
            if (junction.exclusionPolygons().stream().anyMatch(polygon -> RoadGeometryUtils.pointInPolygon(voxelPlan, polygon))
                    || (deckCap != null && pos.getY() > deckCap)
                    || grade.protectsClearance(voxelPlan, pos.getY())) {
                return null;
            }
        }
        return new Candidate(anchor.down(), layer, voxels);
    }
}
