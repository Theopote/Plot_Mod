package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges per-cross-section slope samples into one continuous batter surface per world column.
 */
public final class SlopeSurfaceConsolidator {
    private SlopeSurfaceConsolidator() {
    }

    public static void apply(
            RoadCorridorPlan plan,
            List<SlopeProfileSample> samples,
            TerrainSampler terrain) {
        if (plan == null || samples == null || samples.isEmpty()) {
            return;
        }
        plan.clearSlopeCells();
        Map<ColumnKey, List<SlopeProfileSample>> byColumn = groupByColumn(samples);
        Map<String, List<ResolvedRayStep>> rays = new LinkedHashMap<>();

        for (var entry : byColumn.entrySet()) {
            ColumnKey key = entry.getKey();
            List<SlopeProfileSample> columnSamples = entry.getValue();
            ResolvedRayStep resolved = resolveColumn(key, columnSamples, terrain);
            if (resolved == null) {
                continue;
            }
            rays.computeIfAbsent(resolved.rayId(), ignored -> new ArrayList<>()).add(resolved);
        }

        for (List<ResolvedRayStep> raySteps : rays.values()) {
            enforceMonotonicRay(raySteps);
        }

        for (List<ResolvedRayStep> raySteps : rays.values()) {
            for (ResolvedRayStep step : raySteps) {
                if (step.incomplete()) {
                    plan.markSlopeColumnIncomplete(step.columnKey());
                    continue;
                }
                SlopeColumnCell cell = plan.slopeCellOrCreate(step.columnKey(), step.planPoint());
                if (step.fill()) {
                    cell.mergeFill(step.slopeSurfaceY(), step.materialId());
                } else {
                    cell.mergeCut(step.slopeSurfaceY(), step.materialId());
                }
            }
        }
    }

    static String rayId(Vec2d outerEdge, Vec2d outwardNormal) {
        int octant = (int) Math.round(Math.atan2(outwardNormal.y, outwardNormal.x) * 4.0 / Math.PI);
        int origin = (int) Math.floor(outerEdge.x * 0.7 + outerEdge.y * 0.3);
        return origin + ":" + octant;
    }

    private static Map<ColumnKey, List<SlopeProfileSample>> groupByColumn(List<SlopeProfileSample> samples) {
        Map<ColumnKey, List<SlopeProfileSample>> grouped = new LinkedHashMap<>();
        for (SlopeProfileSample sample : samples) {
            grouped.computeIfAbsent(sample.columnKey(), ignored -> new ArrayList<>()).add(sample);
        }
        return grouped;
    }

    private static ResolvedRayStep resolveColumn(
            ColumnKey key,
            List<SlopeProfileSample> columnSamples,
            TerrainSampler terrain) {
        if (columnSamples.isEmpty()) {
            return null;
        }
        int fillVotes = 0;
        int cutVotes = 0;
        for (SlopeProfileSample sample : columnSamples) {
            if (sample.fill()) {
                fillVotes++;
            } else {
                cutVotes++;
            }
        }
        boolean fillMode = fillVotes >= cutVotes;
        if (fillVotes > 0 && cutVotes > 0) {
            Vec2d planPoint = columnSamples.getFirst().planPoint();
            int groundY = terrain != null && terrain.isChunkLoaded(key.worldX(), key.worldZ())
                ? terrain.sampleSurfaceY(planPoint)
                : columnSamples.getFirst().slopeSurfaceY();
            int representativeY = columnSamples.stream()
                .mapToInt(SlopeProfileSample::slopeSurfaceY)
                .max()
                .orElse(groundY);
            fillMode = representativeY > groundY;
        }
        final boolean fill = fillMode;

        SlopeProfileSample anchor = columnSamples.stream()
            .filter(sample -> sample.fill() == fill)
            .max(Comparator.comparingInt(SlopeProfileSample::horizontalOffset))
            .orElse(columnSamples.getFirst());

        int slopeY = fill
            ? columnSamples.stream().filter(SlopeProfileSample::fill).mapToInt(SlopeProfileSample::slopeSurfaceY).max().orElse(anchor.slopeSurfaceY())
            : columnSamples.stream().filter(sample -> !sample.fill()).mapToInt(SlopeProfileSample::slopeSurfaceY).min().orElse(anchor.slopeSurfaceY());

        boolean incomplete = columnSamples.stream().anyMatch(SlopeProfileSample::incomplete);
        return new ResolvedRayStep(
            key,
            anchor.planPoint(),
            slopeY,
            fill,
            anchor.materialId(),
            anchor.rayId(),
            anchor.horizontalOffset(),
            incomplete);
    }

    private static void enforceMonotonicRay(List<ResolvedRayStep> raySteps) {
        raySteps.sort(Comparator.comparingInt(ResolvedRayStep::horizontalOffset));
        if (raySteps.size() < 2) {
            return;
        }
        ResolvedRayStep previous = raySteps.getFirst();
        for (int i = 1; i < raySteps.size(); i++) {
            ResolvedRayStep current = raySteps.get(i);
            if (previous.fill() != current.fill()) {
                previous = current;
                continue;
            }
            int delta = current.slopeSurfaceY() - previous.slopeSurfaceY();
            if (Math.abs(delta) <= 1) {
                previous = current;
                continue;
            }
            int adjustedY = previous.slopeSurfaceY() + (delta > 0 ? 1 : -1);
            raySteps.set(i, current.withSlopeSurfaceY(adjustedY));
            previous = raySteps.get(i);
        }
    }

    private record ResolvedRayStep(
            ColumnKey columnKey,
            Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            String rayId,
            int horizontalOffset,
            boolean incomplete) {

        ResolvedRayStep withSlopeSurfaceY(int y) {
            return new ResolvedRayStep(
                columnKey, planPoint, y, fill, materialId, rayId, horizontalOffset, incomplete);
        }
    }
}
