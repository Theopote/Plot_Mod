package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges per-cross-section slope samples into one continuous batter surface per world column.
 * Processing order: group by explicit {@link SlopeRayId}, monotonic fix per ray, then merge columns.
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

        Map<SlopeRayId, List<SlopeProfileSample>> byRay = groupByRay(samples);
        Map<ColumnKey, List<ColumnCandidate>> columnCandidates = new LinkedHashMap<>();
        Set<ColumnKey> touchedByIncompleteRay = new HashSet<>();

        for (var rayEntry : byRay.entrySet()) {
            List<SlopeProfileSample> raySamples = new ArrayList<>(rayEntry.getValue());
            raySamples.sort(Comparator.comparingInt(SlopeProfileSample::horizontalOffset));

            boolean rayIncomplete = raySamples.stream().anyMatch(SlopeProfileSample::incomplete);
            if (rayIncomplete) {
                plan.noteUnresolvedSlopeConstruction();
                for (SlopeProfileSample sample : raySamples) {
                    touchedByIncompleteRay.add(sample.columnKey());
                }
                continue;
            }

            List<RayStep> steps = new ArrayList<>(raySamples.size());
            for (SlopeProfileSample sample : raySamples) {
                steps.add(new RayStep(
                    sample.columnKey(),
                    sample.planPoint(),
                    sample.slopeSurfaceY(),
                    sample.fill(),
                    sample.materialId(),
                    sample.horizontalOffset()));
            }
            enforceMonotonicRay(steps);

            for (RayStep step : steps) {
                columnCandidates
                    .computeIfAbsent(step.columnKey(), ignored -> new ArrayList<>())
                    .add(new ColumnCandidate(step, rayEntry.getKey()));
            }
        }

        Set<ColumnKey> written = new HashSet<>();
        for (var entry : columnCandidates.entrySet()) {
            ColumnKey key = entry.getKey();
            ColumnResolution resolution = resolveColumnWinner(key, entry.getValue(), terrain);
            if (resolution == null) {
                continue;
            }
            if (resolution.hadConflict()) {
                plan.noteUnresolvedSlopeConstruction();
            }
            SlopeColumnCell cell = plan.slopeCellOrCreate(key, resolution.planPoint());
            if (resolution.fill()) {
                cell.mergeFill(resolution.slopeSurfaceY(), resolution.materialId());
            } else {
                cell.mergeCut(resolution.slopeSurfaceY(), resolution.materialId());
            }
            written.add(key);
        }

        for (ColumnKey key : touchedByIncompleteRay) {
            if (!written.contains(key)) {
                plan.markSlopeColumnIncomplete(key);
            }
        }
    }

    private static Map<SlopeRayId, List<SlopeProfileSample>> groupByRay(List<SlopeProfileSample> samples) {
        Map<SlopeRayId, List<SlopeProfileSample>> grouped = new LinkedHashMap<>();
        for (SlopeProfileSample sample : samples) {
            grouped.computeIfAbsent(sample.rayId(), ignored -> new ArrayList<>()).add(sample);
        }
        return grouped;
    }

    /**
     * Prefer the candidate closest to its shoulder (smallest horizontal offset).
     * Resolve fill/cut conflicts using natural ground at the column.
     */
    private static ColumnResolution resolveColumnWinner(
            ColumnKey key,
            List<ColumnCandidate> candidates,
            TerrainSampler terrain) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return ColumnResolution.from(candidates.getFirst().step(), false);
        }

        int minOffset = candidates.stream()
            .mapToInt(candidate -> candidate.step().horizontalOffset())
            .min()
            .orElse(Integer.MAX_VALUE);
        List<ColumnCandidate> closest = candidates.stream()
            .filter(candidate -> candidate.step().horizontalOffset() == minOffset)
            .toList();

        if (closest.size() == 1) {
            return ColumnResolution.from(closest.getFirst().step(), false);
        }

        boolean mixedFillCut = closest.stream().anyMatch(c -> c.step().fill())
            && closest.stream().anyMatch(c -> !c.step().fill());
        if (!mixedFillCut) {
            RayStep merged = closest.getFirst().step().fill()
                ? closest.stream().map(ColumnCandidate::step)
                    .max(Comparator.comparingInt(RayStep::slopeSurfaceY))
                    .orElse(closest.getFirst().step())
                : closest.stream().map(ColumnCandidate::step)
                    .min(Comparator.comparingInt(RayStep::slopeSurfaceY))
                    .orElse(closest.getFirst().step());
            return ColumnResolution.from(merged, false);
        }

        int groundY = sampleGroundY(key, closest.getFirst().step().planPoint(), terrain);
        List<ColumnCandidate> fillCandidates = closest.stream().filter(c -> c.step().fill()).toList();
        List<ColumnCandidate> cutCandidates = closest.stream().filter(c -> !c.step().fill()).toList();

        if (!fillCandidates.isEmpty() && !cutCandidates.isEmpty()) {
            RayStep bestFill = fillCandidates.stream()
                .map(ColumnCandidate::step)
                .max(Comparator.comparingInt(RayStep::slopeSurfaceY))
                .orElseThrow();
            RayStep bestCut = cutCandidates.stream()
                .map(ColumnCandidate::step)
                .min(Comparator.comparingInt(RayStep::slopeSurfaceY))
                .orElseThrow();
            RayStep chosen = bestFill.slopeSurfaceY() > groundY ? bestFill : bestCut;
            return ColumnResolution.from(chosen, true);
        }

        RayStep fallback = closest.getFirst().step();
        return ColumnResolution.from(fallback, true);
    }

    private static int sampleGroundY(ColumnKey key, com.plot.api.geometry.Vec2d planPoint, TerrainSampler terrain) {
        if (terrain != null && terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
            return terrain.sampleSurfaceY(planPoint);
        }
        return planPoint != null ? (int) Math.round(planPoint.y) : 64;
    }

    private static void enforceMonotonicRay(List<RayStep> raySteps) {
        if (raySteps.size() < 2) {
            return;
        }
        RayStep previous = raySteps.getFirst();
        for (int i = 1; i < raySteps.size(); i++) {
            RayStep current = raySteps.get(i);
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
            RayStep adjusted = current.withSlopeSurfaceY(adjustedY);
            raySteps.set(i, adjusted);
            previous = adjusted;
        }
    }

    private record ColumnCandidate(RayStep step, SlopeRayId rayId) {
    }

    private record RayStep(
            ColumnKey columnKey,
            com.plot.api.geometry.Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            int horizontalOffset) {

        RayStep withSlopeSurfaceY(int y) {
            return new RayStep(columnKey, planPoint, y, fill, materialId, horizontalOffset);
        }
    }

    private record ColumnResolution(
            com.plot.api.geometry.Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            boolean hadConflict) {

        static ColumnResolution from(RayStep step, boolean hadConflict) {
            return new ColumnResolution(
                step.planPoint(),
                step.slopeSurfaceY(),
                step.fill(),
                step.materialId(),
                hadConflict);
        }
    }
}
