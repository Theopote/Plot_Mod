package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;

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
        Set<ColumnKey> touchedByUnresolvedRay = new HashSet<>();

        for (var rayEntry : byRay.entrySet()) {
            List<SlopeProfileSample> raySamples = new ArrayList<>(rayEntry.getValue());
            raySamples.sort(Comparator.comparingInt(SlopeProfileSample::horizontalOffset));
            // 环带补格是空间插值，不是完整肩部射线：跳过末端接地与挡墙收尾。
            boolean bandInterpolationRay = raySamples.stream()
                .allMatch(SlopeProfileSample::bandInterpolation);

            SlopeConstructionOutcome rayOutcome = raySamples.stream()
                .map(SlopeProfileSample::outcome)
                .reduce(SlopeConstructionOutcome.COMPLETE, SlopeConstructionOutcome::merge);

            if (!bandInterpolationRay && rayOutcome == SlopeConstructionOutcome.UNRESOLVED) {
                plan.noteUnresolvedSlopeConstruction();
                for (SlopeProfileSample sample : raySamples) {
                    touchedByUnresolvedRay.add(sample.columnKey());
                }
            }

            List<RayStep> steps = new ArrayList<>(raySamples.size());
            SlopeConstructionOutcome rayOutcomeForSteps = bandInterpolationRay
                ? SlopeConstructionOutcome.COMPLETE
                : rayOutcome;
            for (SlopeProfileSample sample : raySamples) {
                steps.add(new RayStep(
                    sample.columnKey(),
                    sample.planPoint(),
                    sample.slopeSurfaceY(),
                    sample.fill(),
                    sample.materialId(),
                    sample.horizontalOffset(),
                    sample.slopeRatio(),
                    sample.outcome(),
                    sample.allowWaterEmbankment(),
                    sample.localDaylightAuthorized()));
            }
            if (!bandInterpolationRay) {
                enforceMonotonicRay(steps);
                // 软接地等末端微调发生在单调校正之前；校正后必须再验证，否则 COMPLETE 名不副实。
                rayOutcomeForSteps = finalizeTipAfterMonotonic(steps, rayOutcomeForSteps, terrain);
                // 诊断计数以校正后的最终末端状态为准，避免「需要挡墙」与已接地矛盾。
                if (rayOutcomeForSteps == SlopeConstructionOutcome.NEEDS_RETAINING_WALL) {
                    plan.noteNeedsRetainingWall();
                }
            }

            int tipOffset = steps.stream().mapToInt(RayStep::horizontalOffset).max().orElse(0);
            for (RayStep step : steps) {
                boolean tipRetaining = !bandInterpolationRay
                    && rayOutcomeForSteps == SlopeConstructionOutcome.NEEDS_RETAINING_WALL
                    && step.horizontalOffset() == tipOffset;
                columnCandidates
                    .computeIfAbsent(step.columnKey(), ignored -> new ArrayList<>())
                    .add(new ColumnCandidate(
                        step,
                        rayEntry.getKey(),
                        rayOutcomeForSteps,
                        tipRetaining,
                        bandInterpolationRay));
            }
        }

        Set<ColumnKey> written = new HashSet<>();
        for (var entry : columnCandidates.entrySet()) {
            ColumnKey key = entry.getKey();
            if (isStructureInteriorColumn(plan, key)) {
                continue;
            }
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
            if (resolution.retainingFinish()) {
                cell.markRetainingFinish();
            }
            // 边坡水域授权绑定最终胜出列；不授予路基、也不保留落败堤道射线的污染。
            if (resolution.allowWaterEmbankment()) {
                plan.markAllowSlopeWaterFill(key);
            }
            // Any daylight-authorized batter sample that touched this column stamps the
            // carve exception — winner may be a neighboring ROAD ray without the flag.
            if (resolution.localDaylightAuthorized()) {
                plan.markLocalDaylightCut(key);
            }
            written.add(key);
        }

        for (ColumnKey key : touchedByUnresolvedRay) {
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

    private static boolean isStructureInteriorColumn(RoadCorridorPlan plan, ColumnKey key) {
        ClearanceCell clearance = plan.clearanceCell(key);
        if (clearance == null || clearance.constructionType() == null) {
            return false;
        }
        return RoadEarthworkPolicy.of(clearance.constructionType()) == RoadEarthworkPolicy.STRUCTURE_ONLY;
    }

    private static ColumnResolution resolveColumnWinner(
            ColumnKey key,
            List<ColumnCandidate> candidates,
            TerrainSampler terrain) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        boolean anyDaylight = candidates.stream()
            .anyMatch(candidate -> candidate.step().localDaylightAuthorized());
        if (candidates.size() == 1) {
            return ColumnResolution.from(candidates.getFirst(), false, anyDaylight);
        }

        // 真实肩部射线优先于环带插值：COMPLETE 环带不得靠 outcome 排序取消合法挡墙终点。
        List<ColumnCandidate> shoulder = candidates.stream()
            .filter(candidate -> !candidate.bandInterpolation())
            .toList();
        List<ColumnCandidate> pool = shoulder.isEmpty() ? candidates : shoulder;

        SlopeConstructionOutcome bestOutcome = pool.stream()
            .map(ColumnCandidate::rayOutcome)
            .min(Comparator.comparingInt(SlopeConstructionOutcome::ordinal))
            .orElse(SlopeConstructionOutcome.COMPLETE);
        List<ColumnCandidate> preferredOutcome = pool.stream()
            .filter(candidate -> candidate.rayOutcome() == bestOutcome)
            .toList();
        if (preferredOutcome.size() == 1) {
            return ColumnResolution.from(preferredOutcome.getFirst(), false, anyDaylight);
        }

        int minOffset = preferredOutcome.stream()
            .mapToInt(candidate -> candidate.step().horizontalOffset())
            .min()
            .orElse(Integer.MAX_VALUE);
        List<ColumnCandidate> closest = preferredOutcome.stream()
            .filter(candidate -> candidate.step().horizontalOffset() == minOffset)
            .toList();

        if (closest.size() == 1) {
            return ColumnResolution.from(closest.getFirst(), false, anyDaylight);
        }

        boolean mixedFillCut = closest.stream().anyMatch(c -> c.step().fill())
            && closest.stream().anyMatch(c -> !c.step().fill());
        if (!mixedFillCut) {
            ColumnCandidate merged = closest.getFirst().step().fill()
                ? closest.stream()
                    .max(Comparator.comparingInt(c -> c.step().slopeSurfaceY()))
                    .orElse(closest.getFirst())
                : closest.stream()
                    .min(Comparator.comparingInt(c -> c.step().slopeSurfaceY()))
                    .orElse(closest.getFirst());
            return ColumnResolution.from(merged, false, anyDaylight);
        }

        int groundY = sampleGroundY(key, closest.getFirst().step().planPoint(), terrain);
        List<ColumnCandidate> fillCandidates = closest.stream().filter(c -> c.step().fill()).toList();
        List<ColumnCandidate> cutCandidates = closest.stream().filter(c -> !c.step().fill()).toList();

        if (!fillCandidates.isEmpty() && !cutCandidates.isEmpty()) {
            ColumnCandidate bestFill = fillCandidates.stream()
                .max(Comparator.comparingInt(c -> c.step().slopeSurfaceY()))
                .orElseThrow();
            ColumnCandidate bestCut = cutCandidates.stream()
                .min(Comparator.comparingInt(c -> c.step().slopeSurfaceY()))
                .orElseThrow();
            ColumnCandidate chosen = bestFill.step().slopeSurfaceY() > groundY ? bestFill : bestCut;
            return ColumnResolution.from(chosen, true, anyDaylight);
        }

        return ColumnResolution.from(closest.getFirst(), true, anyDaylight);
    }

    private static int sampleGroundY(ColumnKey key, com.plot.api.geometry.Vec2d planPoint, TerrainSampler terrain) {
        if (terrain != null && terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
            return terrain.sampleSurfaceY(planPoint);
        }
        return planPoint != null ? (int) Math.round(planPoint.y) : 64;
    }

    /**
     * Snap each step toward the angle-aware target height derived from cumulative horizontal offset.
     */
    private static void enforceMonotonicRay(List<RayStep> raySteps) {
        if (raySteps.isEmpty()) {
            return;
        }
        RayStep anchor = raySteps.getFirst();
        int startY = shoulderEdgeY(anchor);
        boolean fill = anchor.fill();
        float ratio = Math.max(0.5f, anchor.slopeRatio());
        int direction = fill ? -1 : 1;
        int prevY = startY;

        for (int i = 0; i < raySteps.size(); i++) {
            RayStep current = raySteps.get(i);
            if (current.fill() != fill) {
                anchor = current;
                startY = shoulderEdgeY(current);
                fill = current.fill();
                ratio = Math.max(0.5f, current.slopeRatio());
                direction = fill ? -1 : 1;
                prevY = startY;
            }
            int expectedY = startY + direction * Math.round(current.horizontalOffset() / ratio);
            int adjustedY = current.slopeSurfaceY();
            if (Math.abs(adjustedY - expectedY) > 1) {
                adjustedY = expectedY;
            }
            if (i > 0) {
                if (fill) {
                    adjustedY = Math.min(adjustedY, prevY);
                    adjustedY = Math.max(adjustedY, prevY - 1);
                } else {
                    adjustedY = Math.max(adjustedY, prevY);
                    adjustedY = Math.min(adjustedY, prevY + 1);
                }
            }
            if (adjustedY != current.slopeSurfaceY()) {
                raySteps.set(i, current.withSlopeSurfaceY(adjustedY));
            }
            prevY = adjustedY;
        }
    }

    /** Shoulder-edge height at horizontal offset zero, backtracked from the first outward step. */
    private static int shoulderEdgeY(RayStep step) {
        int direction = step.fill() ? -1 : 1;
        float ratio = Math.max(0.5f, step.slopeRatio());
        return step.slopeSurfaceY() - direction * Math.round(step.horizontalOffset() / ratio);
    }

    /**
     * 单调校正后核对末端接地：若仍差 ≤ 软接地阈值则拉回地面；否则降级为挡墙收尾。
     */
    private static SlopeConstructionOutcome finalizeTipAfterMonotonic(
            List<RayStep> steps,
            SlopeConstructionOutcome outcome,
            TerrainSampler terrain) {
        if (steps == null || steps.isEmpty() || terrain == null) {
            return outcome;
        }
        if (outcome != SlopeConstructionOutcome.COMPLETE
                && outcome != SlopeConstructionOutcome.NEEDS_RETAINING_WALL) {
            return outcome;
        }
        int tipIndex = 0;
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).horizontalOffset() >= steps.get(tipIndex).horizontalOffset()) {
                tipIndex = i;
            }
        }
        RayStep tip = steps.get(tipIndex);
        if (!terrain.isChunkLoaded(tip.planPoint())) {
            return outcome == SlopeConstructionOutcome.COMPLETE
                ? SlopeConstructionOutcome.NEEDS_RETAINING_WALL
                : outcome;
        }
        int groundY = terrain.sampleSurfaceY(tip.planPoint());
        if (tipMeetsTerrain(tip, groundY)) {
            return SlopeConstructionOutcome.COMPLETE;
        }
        int remaining = Math.abs(tip.slopeSurfaceY() - groundY);
        if (remaining <= RoadEarthworkPolicy.WIDTH_CAP_SOFT_GROUND_REMAINING
                && tipSnapPreservesNeighborStep(steps, tipIndex, groundY)) {
            steps.set(tipIndex, tip.withSlopeSurfaceY(groundY));
            if (tipMeetsTerrain(steps.get(tipIndex), groundY)) {
                return SlopeConstructionOutcome.COMPLETE;
            }
        }
        return SlopeConstructionOutcome.NEEDS_RETAINING_WALL;
    }

    private static boolean tipMeetsTerrain(RayStep tip, int groundY) {
        if (tip.fill()) {
            return tip.slopeSurfaceY() <= groundY + 1;
        }
        return tip.slopeSurfaceY() >= groundY - 1;
    }

    private static boolean tipSnapPreservesNeighborStep(
            List<RayStep> steps,
            int tipIndex,
            int snappedY) {
        if (tipIndex <= 0) {
            return true;
        }
        RayStep prev = steps.get(tipIndex - 1);
        RayStep tip = steps.get(tipIndex);
        // 软接地允许末端相对邻步最多跳 WIDTH_CAP_SOFT_GROUND_REMAINING 格。
        int delta = Math.abs(snappedY - prev.slopeSurfaceY());
        if (delta > RoadEarthworkPolicy.WIDTH_CAP_SOFT_GROUND_REMAINING) {
            return false;
        }
        if (tip.fill()) {
            return snappedY <= prev.slopeSurfaceY();
        }
        return snappedY >= prev.slopeSurfaceY();
    }

    private record ColumnCandidate(
            RayStep step,
            SlopeRayId rayId,
            SlopeConstructionOutcome rayOutcome,
            boolean tipRetaining,
            boolean bandInterpolation) {
    }

    private record RayStep(
            ColumnKey columnKey,
            com.plot.api.geometry.Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            int horizontalOffset,
            float slopeRatio,
            SlopeConstructionOutcome outcome,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {

        RayStep withSlopeSurfaceY(int y) {
            return new RayStep(
                columnKey, planPoint, y, fill, materialId, horizontalOffset, slopeRatio, outcome,
                allowWaterEmbankment, localDaylightAuthorized);
        }
    }

    private record ColumnResolution(
            com.plot.api.geometry.Vec2d planPoint,
            int slopeSurfaceY,
            boolean fill,
            String materialId,
            boolean hadConflict,
            boolean retainingFinish,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {

        static ColumnResolution from(
                ColumnCandidate candidate,
                boolean hadConflict,
                boolean localDaylightAuthorized) {
            RayStep step = candidate.step();
            return new ColumnResolution(
                step.planPoint(),
                step.slopeSurfaceY(),
                step.fill(),
                step.materialId(),
                hadConflict,
                candidate.tipRetaining(),
                step.allowWaterEmbankment(),
                localDaylightAuthorized || step.localDaylightAuthorized());
        }
    }
}
