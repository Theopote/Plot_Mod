package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.corridor.RoadPavementSupportMaterials;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最终 {@link RoadGenerationResult#placementRecords} 接缝验收：
 * 识别接缝 → 沿桩号采样 → 读最终方块 → 检查纵向连续 / 横向缺口 / 材料完整性。
 */
public final class RoadPlacementSeamAssertions {
    private RoadPlacementSeamAssertions() {
    }

    public record StationPavement(
            double station,
            Vec2d planPoint,
            int worldX,
            int worldZ,
            Integer pavementY,
            RoadSolidLayer pavementLayer,
            boolean hasBridgeMember,
            boolean clearedToAir,
            Vec2d leftNormal) {
    }

    public record ModeTransition(int fromIndex, int toIndex, StationPavement from, StationPavement to) {
    }

    public static List<StationPavement> sampleAlongEdgeCenterline(
            RoadNetwork network,
            RoadEdge edge,
            RoadGenerationResult placements,
            double sampleSpacing) {
        List<PlanCenterlineSample> samples = RoadPlanGeometry.resolveEdgeCenterlineSamples(
            network, edge, Math.max(0.5, sampleSpacing));
        List<StationPavement> out = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            PlanCenterlineSample sample = samples.get(i);
            if (sample == null || sample.position() == null) {
                continue;
            }
            Vec2d left = leftNormalAt(samples, i);
            out.add(inspectColumn(placements, sample.canonicalStation(), sample.position(), left));
        }
        return out;
    }

    public static StationPavement inspectColumn(
            RoadGenerationResult placements,
            double station,
            Vec2d planPoint) {
        return inspectColumn(placements, station, planPoint, new Vec2d(0, 1));
    }

    public static StationPavement inspectColumn(
            RoadGenerationResult placements,
            double station,
            Vec2d planPoint,
            Vec2d leftNormal) {
        int worldX = (int) Math.round(planPoint.x);
        int worldZ = (int) Math.round(planPoint.y);
        Integer pavementY = null;
        RoadSolidLayer pavementLayer = null;
        boolean bridge = false;
        boolean cleared = false;
        for (Map.Entry<BlockPos, BlockRecord> entry : placements.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != worldX || pos.getZ() != worldZ) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = placements.placementLayers.get(pos);
            if (record != null && RoadPavementSupportMaterials.isAirId(record.newBlockId)) {
                if (layer == RoadSolidLayer.ROAD
                        || layer == RoadSolidLayer.SHOULDER
                        || layer == RoadSolidLayer.SIDEWALK) {
                    cleared = true;
                }
                continue;
            }
            if (layer == RoadSolidLayer.BRIDGE) {
                bridge = true;
            }
            if (isPavementLayer(layer)) {
                if (pavementY == null || pos.getY() > pavementY) {
                    pavementY = pos.getY();
                    pavementLayer = layer;
                }
            }
        }
        if (pavementY != null) {
            BlockPos below = new BlockPos(worldX, pavementY - 1, worldZ);
            RoadSolidLayer belowLayer = placements.placementLayers.get(below);
            if (belowLayer == RoadSolidLayer.BRIDGE) {
                bridge = true;
            }
        }
        return new StationPavement(
            station, planPoint, worldX, worldZ, pavementY, pavementLayer, bridge, cleared, leftNormal);
    }

    private static Vec2d leftNormalAt(List<PlanCenterlineSample> samples, int index) {
        Vec2d prev = samples.get(Math.max(0, index - 1)).position();
        Vec2d next = samples.get(Math.min(samples.size() - 1, index + 1)).position();
        Vec2d tangent = next.subtract(prev);
        if (tangent.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        Vec2d unit = tangent.normalize();
        return new Vec2d(-unit.y, unit.x);
    }

    public static List<ModeTransition> findBridgeModeTransitions(List<StationPavement> samples) {
        List<ModeTransition> transitions = new ArrayList<>();
        for (int i = 1; i < samples.size(); i++) {
            StationPavement prev = samples.get(i - 1);
            StationPavement curr = samples.get(i);
            if (prev.hasBridgeMember() != curr.hasBridgeMember()) {
                transitions.add(new ModeTransition(i - 1, i, prev, curr));
            }
        }
        return transitions;
    }

    public static void assertLongitudinalContinuity(
            List<StationPavement> samples,
            int maxStep,
            String context) {
        assertTrue(samples != null && samples.size() >= 2, context + ": need centerline samples");
        Integer prevY = null;
        int present = 0;
        for (StationPavement sample : samples) {
            if (sample.pavementY() == null) {
                assertFalse(sample.clearedToAir(), context + ": pavement cleared to air at station " + sample.station()
                        + " (" + sample.worldX() + "," + sample.worldZ() + ")");
                continue;
            }
            present++;
            if (prevY != null) {
                int step = Math.abs(sample.pavementY() - prevY);
                assertTrue(step <= maxStep,
                    context + ": longitudinal step " + step + " > " + maxStep
                        + " at station " + sample.station()
                        + " Y " + prevY + "→" + sample.pavementY());
            }
            prevY = sample.pavementY();
        }
        assertTrue(present >= Math.max(2, samples.size() / 3),
            context + ": too few pavement samples present=" + present + "/" + samples.size());
    }

    public static void assertNoClearedPavement(List<StationPavement> samples, String context) {
        for (StationPavement sample : samples) {
            assertFalse(sample.clearedToAir(), context + ": design pavement cleared at station " + sample.station());
        }
    }

    /**
     * 路口进近：沿每条边靠近广场的桩号段，检查最终 aggregate 路面存在、与广场高差、相邻阶梯。
     */
    public static void assertJunctionApproachSeams(
            RoadNetwork network,
            RoadGenerationResult aggregate,
            Vec2d plazaPlan,
            int plazaY,
            double nearMin,
            double nearMax,
            int maxStepToPlaza) {
        assertTrue(aggregate != null && !aggregate.placementRecords.isEmpty(), "aggregate placements required");
        int approachesChecked = 0;
        for (RoadEdge edge : network.getEdges().values()) {
            List<StationPavement> samples = sampleAlongEdgeCenterline(network, edge, aggregate, 1.0);
            List<StationPavement> near = samples.stream()
                .filter(s -> {
                    double dist = s.planPoint().distance(plazaPlan);
                    return dist >= nearMin && dist <= nearMax;
                })
                .sorted(Comparator.comparingDouble(s -> s.planPoint().distance(plazaPlan)))
                .toList();
            if (near.isEmpty()) {
                continue;
            }
            approachesChecked++;
            assertLongitudinalContinuity(near, 1, "junction approach edge=" + edge.getId());
            assertNoClearedPavement(near, "junction approach edge=" + edge.getId());
            StationPavement closest = near.getFirst();
            assertNotNull(closest.pavementY(), "missing pavement at junction seam edge=" + edge.getId()
                    + " @" + closest.worldX() + "," + closest.worldZ());
            assertTrue(Math.abs(closest.pavementY() - plazaY) <= maxStepToPlaza,
                "junction seam cliff edge=" + edge.getId()
                    + " pavementY=" + closest.pavementY() + " plazaY=" + plazaY);
            assertCurbPresence(aggregate, closest, 2, "junction curb edge=" + edge.getId());
        }
        assertTrue(approachesChecked >= 2, "expected multiple junction approaches, got " + approachesChecked);
    }

    /**
     * 桥头：在 OPEN↔BRIDGE 真实转换点附近检查最终路面连续。
     */
    public static void assertBridgeHeadSeams(
            List<StationPavement> samples,
            int window,
            int maxStep) {
        List<ModeTransition> transitions = findBridgeModeTransitions(samples);
        assertFalse(transitions.isEmpty(), "expected at least one OPEN↔BRIDGE transition along centerline");
        for (ModeTransition transition : transitions) {
            int from = Math.max(0, transition.fromIndex() - window);
            int to = Math.min(samples.size() - 1, transition.toIndex() + window);
            List<StationPavement> local = samples.subList(from, to + 1);
            assertLongitudinalContinuity(local, maxStep,
                String.format(Locale.ROOT, "bridge head @stations %.1f→%.1f",
                    transition.from().station(), transition.to().station()));
            assertNoClearedPavement(local, "bridge head");
            assertTrue(transition.from().pavementY() != null && transition.to().pavementY() != null,
                "bridge head missing pavement at mode change");
            assertTrue(Math.abs(transition.from().pavementY() - transition.to().pavementY()) <= maxStep,
                "bridge head Y cliff "
                    + transition.from().pavementY() + "→" + transition.to().pavementY());
        }
    }

    /**
     * 变宽：过渡前半宽小于过渡后半宽；过渡区内半宽不剧烈回缩；路肩带无洞。
     */
    public static void assertVariableWidthWidensAlongStations(
            RoadGenerationResult placements,
            List<StationPavement> samples,
            double beforeEndStation,
            double afterStartStation) {
        assertTrue(samples.size() >= 4, "variable-width needs centerline samples");
        int maxBefore = 0;
        int maxAfter = 0;
        int prevHalf = -1;
        for (StationPavement sample : samples) {
            int half = carriagewayHalfWidthAlongNormal(placements, sample, 8);
            if (sample.station() <= beforeEndStation) {
                maxBefore = Math.max(maxBefore, half);
            }
            if (sample.station() >= afterStartStation) {
                maxAfter = Math.max(maxAfter, half);
            }
            if (sample.station() >= beforeEndStation && sample.station() <= afterStartStation) {
                if (prevHalf >= 0) {
                    assertTrue(half + 1 >= prevHalf,
                        "variable-width half-width should not shrink sharply at station "
                            + sample.station() + " " + prevHalf + "→" + half);
                }
                prevHalf = half;
            }
        }
        assertTrue(maxAfter > maxBefore,
            "carriageway must widen after variable section, beforeHalf=" + maxBefore
                + " afterHalf=" + maxAfter);
        assertEquals(0, placements.roadShoulderSeamGapCount, "variable-width must keep ROAD/SHOULDER seam continuous");
    }

    private static void assertCurbPresence(
            RoadGenerationResult placements,
            StationPavement center,
            int lateralMax,
            String context) {
        boolean foundOuter = false;
        for (int dz = -lateralMax; dz <= lateralMax; dz++) {
            for (int dx = -lateralMax; dx <= lateralMax; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) > lateralMax) {
                    continue;
                }
                Integer y = topPavementY(placements, center.worldX() + dx, center.worldZ() + dz);
                if (y != null) {
                    foundOuter = true;
                    assertTrue(Math.abs(y - center.pavementY()) <= 1,
                        context + ": curb/shoulder Y cliff at offset " + dx + "," + dz);
                }
            }
        }
        assertTrue(foundOuter, context + ": missing curb/shoulder band near seam");
    }

    /** 仅沿中心线左法向测行车道半宽，避免把沿程方向误当成横向。 */
    private static int carriagewayHalfWidthAlongNormal(
            RoadGenerationResult placements,
            StationPavement sample,
            int searchRadius) {
        Integer centerY = topRoadY(placements, sample.worldX(), sample.worldZ());
        if (centerY == null) {
            return 0;
        }
        Vec2d normal = sample.leftNormal() != null ? sample.leftNormal() : new Vec2d(0, 1);
        if (normal.lengthSquared() < 1e-12) {
            normal = new Vec2d(0, 1);
        }
        normal = normal.normalize();
        int maxAbs = 0;
        for (int side : new int[] {-1, 1}) {
            for (int offset = 1; offset <= searchRadius; offset++) {
                int x = (int) Math.round(sample.planPoint().x + normal.x * offset * side);
                int z = (int) Math.round(sample.planPoint().y + normal.y * offset * side);
                Integer y = topRoadY(placements, x, z);
                if (y == null || Math.abs(y - centerY) > 1) {
                    break;
                }
                maxAbs = Math.max(maxAbs, offset);
            }
        }
        return maxAbs;
    }

    private static Integer topPavementY(RoadGenerationResult placements, int x, int z) {
        Integer y = null;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : placements.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != x || pos.getZ() != z || !isPavementLayer(entry.getValue())) {
                continue;
            }
            BlockRecord record = placements.placementRecords.get(pos);
            if (record != null && RoadPavementSupportMaterials.isAirId(record.newBlockId)) {
                continue;
            }
            if (y == null || pos.getY() > y) {
                y = pos.getY();
            }
        }
        return y;
    }

    private static Integer topRoadY(RoadGenerationResult placements, int x, int z) {
        Integer y = null;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : placements.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != x || pos.getZ() != z || entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockRecord record = placements.placementRecords.get(pos);
            if (record != null && RoadPavementSupportMaterials.isAirId(record.newBlockId)) {
                continue;
            }
            if (y == null || pos.getY() > y) {
                y = pos.getY();
            }
        }
        return y;
    }

    private static boolean isPavementLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE;
    }
}
