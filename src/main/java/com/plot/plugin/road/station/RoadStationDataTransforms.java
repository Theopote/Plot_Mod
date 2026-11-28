package com.plot.plugin.road.station;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentChainOriginAligner;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalIntentTransforms;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 沿桩号工程数据的变换：拆分、裁剪、重映射（{@link RoadStationMirroring} 负责反向镜像）。
 */
public final class RoadStationDataTransforms {

    private static final double EPSILON = 1e-6;

    private RoadStationDataTransforms() {
    }

    /**
     * 在 {@code splitStation} 处拆分工程数据（{@link CenterlineEditStationPolicy#PARTITION_AND_RESET_TAIL}）：
     * {@code head} 保留链头 [0, split]，{@code tail} 获得 [split, total] 并重映射为 K0+000 起。
     * Fail-closed：任一段 VA/VCS 无法无损表示时不修改 head/tail。
     */
    public static StationMigrationResult applyRoadSplit(
            Road head, Road tail, double splitStation, double totalLength) {
        return applyRoadSplit(head, tail, splitStation, totalLength, null);
    }

    public static StationMigrationResult applyRoadSplit(
            Road head,
            Road tail,
            double splitStation,
            double totalLength,
            RoadSystemConfig defaults) {
        if (head == null || tail == null || totalLength <= EPSILON) {
            return StationMigrationResult.rejected("invalid split inputs");
        }
        if (splitStation <= EPSILON || splitStation >= totalLength - EPSILON) {
            return StationMigrationResult.rejected("split station out of range");
        }

        RoadVerticalAlignment verticalAlignment = head.getVerticalAlignment();
        RoadVariableCrossSections variableCrossSections = head.getVariableCrossSections();
        RoadStationFacilities stationFacilities = head.getStationFacilities();

        RoadVerticalAlignment headVa = null;
        RoadVerticalAlignment tailVa = null;
        if (verticalAlignment != null && !verticalAlignment.isEmpty()) {
            headVa = extractVerticalAlignmentRange(verticalAlignment, 0.0, splitStation);
            tailVa = extractVerticalAlignmentRange(verticalAlignment, splitStation, totalLength);
            if (headVa == null || tailVa == null) {
                return StationMigrationResult.rejected("VA split not lossless");
            }
        }

        RoadVariableCrossSections headVcs;
        RoadVariableCrossSections tailVcs;
        try {
            headVcs = trimHeadVariableCrossSections(
                variableCrossSections, head.getCrossSection(), splitStation, totalLength, defaults);
            tailVcs = extractTailVariableCrossSections(
                variableCrossSections, head.getCrossSection(), splitStation, totalLength, defaults);
        } catch (StationMigrationRefuseException e) {
            return StationMigrationResult.rejected(e.getMessage());
        }

        RoadStationFacilities headFac = trimHeadStationFacilities(
            stationFacilities, splitStation, totalLength);
        RoadStationFacilities tailFac = extractTailStationFacilities(
            stationFacilities, splitStation, totalLength);

        boolean refitHorizontalAlignment = head.getHorizontalAlignment() != null;
        head.setVerticalAlignment(headVa);
        tail.setVerticalAlignment(tailVa);
        head.setVariableCrossSections(nullIfEmpty(headVcs));
        tail.setVariableCrossSections(nullIfEmpty(tailVcs));
        head.setStationFacilities(headFac);
        tail.setStationFacilities(tailFac);
        // Stale signatures must not claim validity until final geometry rebind.
        clearEditedSignaturePendingRebind(head);
        clearEditedSignaturePendingRebind(tail);

        if (refitHorizontalAlignment) {
            head.setHorizontalAlignment(null);
            tail.setHorizontalAlignment(null);
        }
        return StationMigrationResult.ok(refitHorizontalAlignment);
    }

    public static void refitHorizontalAlignmentFromCenterline(RoadNetwork network, Road road) {
        if (road == null || !RoadStationing.isStationable(network, road)) {
            if (road != null) {
                road.setHorizontalAlignment(null);
            }
            return;
        }
        HorizontalAlignmentPolylineFitter.fit(network, road).ifPresentOrElse(
            fitted -> {
                road.setHorizontalAlignment(fitted);
                // Explicit user/refit path may sync origin and bearing to the new chain.
                HorizontalAlignmentChainOriginAligner.alignOriginAndBearingToChainStart(network, road);
            },
            () -> road.setHorizontalAlignment(null));
    }

    /**
     * 拆分 VA/VCS/设施与 flat intent。
     * <p>调用方须先完成本方法，再迁移分段与 reorder，最后再
     * {@link #refitHorizontalAlignmentFromCenterline}——拟合依赖当时的 segment 链。
     */
    public static StationMigrationResult applyRoadSplit(
            RoadNetwork network, Road head, Road tail, double splitStation) {
        return applyRoadSplit(network, head, tail, splitStation, null);
    }

    public static StationMigrationResult applyRoadSplit(
            RoadNetwork network,
            Road head,
            Road tail,
            double splitStation,
            RoadSystemConfig defaults) {
        if (network == null || head == null || tail == null) {
            return StationMigrationResult.rejected("invalid split network args");
        }
        double totalLength = RoadStationing.canonicalLength(network, head);
        RoadHorizontalAlignment sourceHa = head.getHorizontalAlignment();
        Optional<RoadHorizontalAlignment> extractedHead = Optional.empty();
        Optional<RoadHorizontalAlignment> extractedTail = Optional.empty();
        boolean preferExtract = sourceHa != null
            && !sourceHa.isEmpty()
            && RoadStationing.domainMap(network, head).isReliable();
        if (preferExtract) {
            extractedHead = HorizontalAlignmentGeometry.extractRange(sourceHa, 0.0, splitStation);
            extractedTail = HorizontalAlignmentGeometry.extractRange(
                sourceHa, splitStation, sourceHa.totalLength());
        }
        StationMigrationResult result = applyRoadSplit(head, tail, splitStation, totalLength, defaults);
        if (!result.isSuccess()) {
            return result;
        }
        if (preferExtract && extractedHead.isPresent() && extractedTail.isPresent()) {
            // Prefer accurate HA interval extract over polyline refit (P02).
            head.setHorizontalAlignment(extractedHead.get());
            tail.setHorizontalAlignment(extractedTail.get());
            result = StationMigrationResult.ok(false);
        }
        RoadVerticalIntentTransforms.partitionFlatIntentOnRoadSplit(network, head, tail, splitStation);
        return result;
    }

    /**
     * 将 {@code tail} 的沿桩号工程数据拼接到 {@code head} 之后（{@link CenterlineEditStationPolicy#OFFSET_BY_HEAD_LENGTH}），
     * 写入 {@code target}（通常为 head）。连接点高程冲突或 VCS 模式冲突时拒绝且不修改 target。
     */
    public static StationMigrationResult applyRoadMerge(
            Road target,
            Road head,
            Road tail,
            double headLength,
            double tailLength) {
        return applyRoadMerge(target, head, tail, headLength, tailLength, null);
    }

    public static StationMigrationResult applyRoadMerge(
            Road target,
            Road head,
            Road tail,
            double headLength,
            double tailLength,
            RoadSystemConfig defaults) {
        if (target == null || head == null || tail == null) {
            return StationMigrationResult.rejected("invalid merge args");
        }
        if (headLength < -EPSILON || tailLength < -EPSILON) {
            return StationMigrationResult.rejected("invalid merge lengths");
        }
        if (!verticalIntentMergeCompatible(head, tail)) {
            return StationMigrationResult.rejected("vertical intent incompatible");
        }

        boolean refitHorizontalAlignment = head.getHorizontalAlignment() != null
            || tail.getHorizontalAlignment() != null;

        boolean headHasVa = head.getVerticalAlignment() != null && !head.getVerticalAlignment().isEmpty();
        boolean tailHasVa = tail.getVerticalAlignment() != null && !tail.getVerticalAlignment().isEmpty();
        // One-sided VA would silently extend profile onto AUTO side — refuse.
        if (headHasVa != tailHasVa) {
            return StationMigrationResult.rejected("one-sided VA merge refused");
        }
        // Valid EDITED vs AUTO cannot be unified losslessly — refuse (rebind happens after final HA).
        ProfileSource headSrc = head.getEffectiveProfileSource();
        ProfileSource tailSrc = tail.getEffectiveProfileSource();
        if (headSrc != tailSrc
                && (headSrc == ProfileSource.EDITED || tailSrc == ProfileSource.EDITED)
                && (headHasVa || tailHasVa)) {
            return StationMigrationResult.rejected("EDITED/AUTO profile merge refused");
        }

        RoadVerticalAlignment mergedVa = mergeVerticalAlignments(
            head.getVerticalAlignment(), tail.getVerticalAlignment(), headLength);
        if ((headHasVa || tailHasVa) && mergedVa == null) {
            return StationMigrationResult.rejected("VA merge elev/constraint conflict");
        }

        RoadVariableCrossSections mergedVcs = mergeVariableCrossSections(
            head.getVariableCrossSections(),
            tail.getVariableCrossSections(),
            head.getCrossSection(),
            tail.getCrossSection(),
            headLength,
            tailLength,
            defaults);
        if ((head.getVariableCrossSections() != null && !head.getVariableCrossSections().isEmpty()
                || tail.getVariableCrossSections() != null && !tail.getVariableCrossSections().isEmpty())
                && mergedVcs == null) {
            return StationMigrationResult.rejected("VCS merge not lossless");
        }

        RoadStationFacilities mergedFac = mergeStationFacilities(
            head.getStationFacilities(),
            tail.getStationFacilities(),
            headLength,
            tailLength);

        var mergedFlat = mergeFlatVerticalIntents(head, tail);
        if (mergedFlat.rejected()) {
            return StationMigrationResult.rejected(mergedFlat.diagnostic());
        }

        target.setVerticalAlignment(mergedVa);
        target.setVariableCrossSections(nullIfEmpty(mergedVcs));
        target.setStationFacilities(mergedFac);
        if (mergedFlat.intent() != null) {
            target.setFlatVerticalIntent(mergedFlat.intent());
        }
        if (head.getCrossSection() != null) {
            target.setCrossSection(head.getCrossSection().copy());
        }
        // Do not copy stale geometry signatures — caller rebinds after final HA/topology.
        clearEditedSignaturePendingRebind(target);

        if (refitHorizontalAlignment) {
            target.setHorizontalAlignment(null);
        }
        return StationMigrationResult.ok(refitHorizontalAlignment);
    }

    private record FlatMergeOutcome(
            FlatVerticalIntent intent,
            boolean rejected,
            String diagnostic) {
        static FlatMergeOutcome ok(FlatVerticalIntent intent) {
            return new FlatMergeOutcome(intent, false, "");
        }

        static FlatMergeOutcome refuse(String diagnostic) {
            return new FlatMergeOutcome(null, true, diagnostic);
        }
    }

    private static FlatMergeOutcome mergeFlatVerticalIntents(Road head, Road tail) {
        FlatVerticalIntent headIntent = head.getFlatVerticalIntent();
        FlatVerticalIntent tailIntent = tail.getFlatVerticalIntent();
        if (headIntent == null && tailIntent == null) {
            return FlatMergeOutcome.ok(null);
        }
        if (headIntent == null) {
            return FlatMergeOutcome.ok(tailIntent.copy());
        }
        if (tailIntent == null) {
            return FlatMergeOutcome.ok(headIntent.copy());
        }
        if (Math.abs(headIntent.getBaseElevation() - tailIntent.getBaseElevation()) > 1e-3) {
            return FlatMergeOutcome.refuse("flat base elevation conflict");
        }
        FlatVerticalIntent merged = headIntent.copy();
        for (var entry : tailIntent.getIntersectionOverrides().entrySet()) {
            Double existing = merged.getIntersectionOverride(entry.getKey());
            if (existing != null && Math.abs(existing - entry.getValue()) > 1e-3) {
                return FlatMergeOutcome.refuse("flat override conflict");
            }
            merged.setIntersectionOverride(entry.getKey(), entry.getValue());
        }
        return FlatMergeOutcome.ok(merged);
    }

    /** Clear binding until {@link #rebindEditedProfileAfterFinalGeometry} runs. */
    private static void clearEditedSignaturePendingRebind(Road road) {
        if (road == null) {
            return;
        }
        if (road.getStoredProfileSource() == ProfileSource.EDITED
                || road.getEffectiveProfileSource() == ProfileSource.EDITED) {
            road.setProfileSource(ProfileSource.EDITED);
            road.setEditedGeometrySignature(EditedProfileGeometry.UNBOUND_SIGNATURE);
        }
    }

    /**
     * After final topology + HA: recompute geometry signature only when the source profile was
     * truly valid EDITED and the migrated VA remains evaluable. Never activates stale EDITED.
     */
    public static boolean rebindEditedProfileAfterFinalGeometry(
            RoadNetwork network,
            Road road,
            boolean sourceWasValidEdited) {
        if (network == null || road == null) {
            return true;
        }
        if (!sourceWasValidEdited) {
            // Keep archived curve; do not activate. Unbound signature → generation AUTO.
            if (road.getStoredProfileSource() == ProfileSource.EDITED) {
                road.setEditedGeometrySignature(EditedProfileGeometry.UNBOUND_SIGNATURE);
            }
            return true;
        }
        if (!VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            return false;
        }
        road.setProfileSource(ProfileSource.EDITED);
        road.setEditedGeometrySignature(EditedProfileGeometry.signature(network, road));
        return ResolvedProfileAuthority.hasValidEditedProfile(network, road);
    }

    /**
     * Physical pass of a VA sample: edge + geometry-local distance + expected elevation.
     * Used to prove elev equivalence after HA refit ({@code s_old → (edge,d) → s_new}).
     */
    public record PhysicalStationAnchor(
            String edgeId,
            double localDistance,
            double elevation) {
    }

    /** Capture VA PVI (and mid-span) anchors as physical edge-local passes. */
    public static List<PhysicalStationAnchor> captureVaPhysicalAnchors(
            RoadNetwork network,
            Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        RoadVerticalAlignment va = road.getVerticalAlignment();
        if (va == null || va.isEmpty() || !RoadStationing.isStationable(network, road)) {
            return List.of();
        }
        List<PhysicalStationAnchor> anchors = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : va.sortedPvis()) {
            captureOneVaAnchor(network, road, pvi.getStation(), pvi.getElevation())
                .ifPresent(anchors::add);
        }
        double length = RoadStationing.canonicalLength(network, road);
        if (length > EPSILON * 2.0) {
            double mid = length * 0.5;
            OptionalDouble elev = VerticalAlignmentGeometry.elevationAt(va, mid);
            if (elev.isPresent()) {
                captureOneVaAnchor(network, road, mid, elev.getAsDouble()).ifPresent(anchors::add);
            }
        }
        return anchors;
    }

    private static Optional<PhysicalStationAnchor> captureOneVaAnchor(
            RoadNetwork network,
            Road road,
            double canonicalStation,
            double elevation) {
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, canonicalStation);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PhysicalStationAnchor(
            resolved.get().segmentId(),
            resolved.get().localDistance(),
            elevation));
    }

    /**
     * Verify migrated VA still matches elevations at captured physical passes.
     * Anchors whose edge left this road are skipped (belong to the other split component).
     */
    public static boolean verifyVaPhysicalAnchors(
            RoadNetwork network,
            Road road,
            List<PhysicalStationAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return true;
        }
        if (network == null || road == null) {
            return false;
        }
        RoadVerticalAlignment va = road.getVerticalAlignment();
        if (va == null || !VerticalAlignmentGeometry.isEvaluable(va)) {
            return false;
        }
        Set<String> edgeIds = Set.copyOf(road.getOrderedSegmentIds());
        int checked = 0;
        for (PhysicalStationAnchor anchor : anchors) {
            if (anchor == null || anchor.edgeId() == null || !edgeIds.contains(anchor.edgeId())) {
                continue;
            }
            RoadEdge edge = network.getEdge(anchor.edgeId());
            if (edge == null || !road.getId().equals(edge.getRoadId())) {
                continue;
            }
            OptionalDouble station = RoadStationing.resolveCanonicalStationAtEdgeLocal(
                network, road, anchor.edgeId(), anchor.localDistance());
            if (station.isEmpty() || !Double.isFinite(station.getAsDouble())) {
                return false;
            }
            OptionalDouble elev = VerticalAlignmentGeometry.elevationAt(va, station.getAsDouble());
            if (elev.isEmpty() || Math.abs(elev.getAsDouble() - anchor.elevation()) > 1e-2) {
                return false;
            }
            checked++;
        }
        return checked > 0 || anchors.stream().noneMatch(a ->
            a != null && a.edgeId() != null && edgeIds.contains(a.edgeId()));
    }

    public static StationMigrationResult applyRoadMerge(RoadNetwork network, Road head, Road tail) {
        if (network == null || head == null || tail == null) {
            return StationMigrationResult.rejected("invalid merge network args");
        }
        double headLength = RoadStationing.canonicalLength(network, head);
        double tailLength = RoadStationing.canonicalLength(network, tail);
        StationMigrationResult result = applyRoadMerge(head, head, tail, headLength, tailLength);
        if (!result.isSuccess()) {
            return result;
        }
        if (result.requiresHaRefit()) {
            refitHorizontalAlignmentFromCenterline(network, head);
        }
        return result;
    }

    private static boolean verticalIntentMergeCompatible(Road head, Road tail) {
        if (head == null || tail == null) {
            return false;
        }
        var headMode = head.getVerticalMode();
        var tailMode = tail.getVerticalMode();
        if (headMode != tailMode) {
            return false;
        }
        var headTerrain = head.getStoredTerrainStyle();
        var tailTerrain = tail.getStoredTerrainStyle();
        if (headTerrain != null && tailTerrain != null && headTerrain != tailTerrain) {
            return false;
        }
        Double headFlat = head.getFlatVerticalIntent() != null
            ? head.getFlatVerticalIntent().getBaseElevation()
            : null;
        Double tailFlat = tail.getFlatVerticalIntent() != null
            ? tail.getFlatVerticalIntent().getBaseElevation()
            : null;
        if (headFlat != null && tailFlat != null && Math.abs(headFlat - tailFlat) > 1e-3) {
            return false;
        }
        return true;
    }

    /**
     * 分段几何长度变化后，按 {@link CenterlineEditStationPolicy#REPARAMETERIZE_STATION} 对 VA / VCS / 设施做仿射重映射：
     * {@code [rangeStart, rangeStart + oldSegmentLength)} 内按弧长比例缩放，其后整体平移差值。
     */
    public static void rescaleAfterGeometryEdit(
            Road road,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength,
            double totalLengthBefore) {
        if (road == null || oldSegmentLength <= EPSILON) {
            return;
        }
        if (Math.abs(oldSegmentLength - newSegmentLength) <= EPSILON) {
            return;
        }
        if (!hasStationEngineeringData(road)) {
            return;
        }

        road.setVerticalAlignment(rescaleVerticalAlignment(
            road.getVerticalAlignment(),
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
        road.setVariableCrossSections(rescaleVariableCrossSections(
            road.getVariableCrossSections(),
            road.getCrossSection(),
            totalLengthBefore,
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
        road.setStationFacilities(rescaleStationFacilities(
            road.getStationFacilities(),
            totalLengthBefore,
            rangeStart,
            oldSegmentLength,
            newSegmentLength));
    }

    /**
     * Dual-domain geometry capture: {@code instanceLength} for polyline PI/fillet policy;
     * canonical range for VA/VCS/facility clip. Do not mix domains.
     */
    public record SegmentGeometrySnapshot(
            double instanceLength,
            double canonicalRangeStart,
            double canonicalRangeEnd,
            double canonicalTotalLength) {

        /** Canonical start — station-data callers. */
        public double rangeStart() {
            return canonicalRangeStart;
        }

        /** Canonical segment length — station-data remap. */
        public double oldSegmentLength() {
            return Math.max(0.0, canonicalRangeEnd - canonicalRangeStart);
        }

        public double totalLengthBefore() {
            return canonicalTotalLength;
        }

        public static SegmentGeometrySnapshot capture(RoadNetwork network, String edgeId) {
            if (network == null || edgeId == null || edgeId.isBlank()) {
                return null;
            }
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null || edge.getRoadId() == null) {
                return null;
            }
            Road road = network.getRoadForEdge(edge);
            if (road == null) {
                return null;
            }
            Optional<OrientedRoadSegment> oriented =
                RoadStationing.orientedSegment(network, road, edgeId);
            if (oriented.isEmpty()) {
                return null;
            }
            OrientedRoadSegment segment = oriented.get();
            double cStart = RoadStationing.toCanonicalChainage(
                network, road, segment.startStation());
            double cEnd = RoadStationing.toCanonicalChainage(
                network, road, segment.endStation());
            if (!Double.isFinite(cStart) || !Double.isFinite(cEnd) || cEnd < cStart - EPSILON) {
                return null;
            }
            return new SegmentGeometrySnapshot(
                edge.getLength(),
                cStart,
                cEnd,
                RoadStationing.canonicalLength(network, road));
        }
    }

    /** Ordered physical station span of one oriented edge slice (LOOP may emit two for one edgeId). */
    public record EdgeStationSpan(
            String edgeId,
            double start,
            double end,
            int sliceIndex) {
        public boolean isValid() {
            return edgeId != null && !edgeId.isBlank() && end > start + EPSILON;
        }

        public double length() {
            return Math.max(0.0, end - start);
        }
    }

    static double remapStationAfterSegmentEdit(
            double station,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (!Double.isFinite(station)) {
            return station;
        }
        double rangeEnd = rangeStart + oldSegmentLength;
        if (station < rangeStart - EPSILON) {
            return station;
        }
        if (station < rangeEnd - EPSILON) {
            return rangeStart + (station - rangeStart) * (newSegmentLength / oldSegmentLength);
        }
        return station + (newSegmentLength - oldSegmentLength);
    }

    private static boolean hasStationEngineeringData(Road road) {
        return road.getVerticalAlignment() != null
            || road.getVariableCrossSections() != null
            || road.getStationFacilities() != null;
    }

    static RoadVerticalAlignment rescaleVerticalAlignment(
            RoadVerticalAlignment source,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<PointOfVerticalIntersection> remapped = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : source.sortedPvis()) {
            double station = remapStationAfterSegmentEdit(
                pvi.getStation(), rangeStart, oldSegmentLength, newSegmentLength);
            if (!remapped.isEmpty()) {
                PointOfVerticalIntersection last = remapped.getLast();
                if (Math.abs(last.getStation() - station) <= EPSILON) {
                    continue;
                }
            }
            remapped.add(new PointOfVerticalIntersection(
                station,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        return remapped.isEmpty() ? null : new RoadVerticalAlignment(remapped);
    }

    static RoadVariableCrossSections rescaleVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        return rescaleVariableCrossSections(
            source, roadDefault, totalLengthBefore, rangeStart, oldSegmentLength, newSegmentLength, null);
    }

    static RoadVariableCrossSections rescaleVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength,
            RoadSystemConfig defaults) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        VariableCrossSectionTransitionMode mode = source.getTransitionMode();
        // SMOOTH: never rebuild via STEP intervals — remap key stations and verify samples.
        if (mode == VariableCrossSectionTransitionMode.SMOOTH) {
            return rescaleSmoothVariableCrossSections(
                source, defaultSection, totalLengthBefore, rangeStart,
                oldSegmentLength, newSegmentLength, defaults);
        }
        List<Interval> intervals = buildIntervals(
            source.sortedStations(),
            defaultSection,
            totalLengthBefore);
        List<Interval> remapped = new ArrayList<>(intervals.size());
        for (Interval interval : intervals) {
            double start = remapStationAfterSegmentEdit(
                interval.start, rangeStart, oldSegmentLength, newSegmentLength);
            double end = remapStationAfterSegmentEdit(
                interval.end, rangeStart, oldSegmentLength, newSegmentLength);
            if (end > start + EPSILON) {
                remapped.add(new Interval(start, end, interval.template));
            }
        }
        remapped = mergeAdjacentIntervals(remapped);
        return intervalsToVariableCrossSections(remapped, defaultSection, mode);
    }

    private static RoadVariableCrossSections rescaleSmoothVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength,
            RoadSystemConfig defaults) {
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("plot");
        List<StationCrossSection> remapped = new ArrayList<>();
        for (StationCrossSection entry : source.sortedStations()) {
            double newStation = remapStationAfterSegmentEdit(
                entry.getStation(), rangeStart, oldSegmentLength, newSegmentLength);
            remapped.add(StationCrossSection.at(newStation, entry.getCrossSection().copy()));
        }
        remapped.sort(Comparator.comparingDouble(StationCrossSection::getStation));
        RoadVariableCrossSections result =
            new RoadVariableCrossSections(remapped, VariableCrossSectionTransitionMode.SMOOTH);
        if (!result.isValid()) {
            throw new StationMigrationRefuseException("SMOOTH rescale invalid");
        }
        double rangeEnd = rangeStart + oldSegmentLength;
        double step = Math.max(oldSegmentLength / 32.0, 0.5);
        for (double s = rangeStart; s <= rangeEnd + EPSILON; s += step) {
            ResolvedCrossSection before = VariableCrossSectionResolver.resolve(
                defaultSection,
                source.sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH,
                Math.min(s, rangeEnd),
                config);
            double afterStation = remapStationAfterSegmentEdit(
                Math.min(s, rangeEnd), rangeStart, oldSegmentLength, newSegmentLength);
            ResolvedCrossSection after = VariableCrossSectionResolver.resolve(
                defaultSection,
                result.sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH,
                afterStation,
                config);
            if (!before.displayEquals(after)) {
                throw new StationMigrationRefuseException("SMOOTH rescale displayEquals failed");
            }
        }
        return result;
    }

    static RoadStationFacilities rescaleStationFacilities(
            RoadStationFacilities source,
            double totalLengthBefore,
            double rangeStart,
            double oldSegmentLength,
            double newSegmentLength) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<StationFacilityRun> remapped = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            double start = remapStationAfterSegmentEdit(
                run.getStartStation(), rangeStart, oldSegmentLength, newSegmentLength);
            Double endStation = run.getEndStation() == null
                ? null
                : remapStationAfterSegmentEdit(
                    run.getEndStation(), rangeStart, oldSegmentLength, newSegmentLength);
            if (endStation != null && endStation <= start + EPSILON) {
                continue;
            }
            remapped.add(new StationFacilityRun(
                start,
                endStation,
                run.getKind(),
                run.getSide(),
                run.getMaterial(),
                run.getHeight()));
        }
        remapped.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return remapped.isEmpty() ? null : new RoadStationFacilities(remapped);
    }

    /**
     * 从 {@code source} 的沿桩号工程数据中提取 {@code [rangeStart, rangeEnd)}，写入 {@code target} 并重映射为 K0+000 起。
     * Fail-closed：有源 VA 却无法无损裁剪时拒绝且不修改 target。
     */
    public static StationMigrationResult applyStationRangeExtract(
            Road source,
            Road target,
            double totalSourceLength,
            double rangeStart,
            double rangeEnd) {
        return applyStationRangeExtract(
            source, target, totalSourceLength, rangeStart, rangeEnd, null);
    }

    public static StationMigrationResult applyStationRangeExtract(
            Road source,
            Road target,
            double totalSourceLength,
            double rangeStart,
            double rangeEnd,
            RoadSystemConfig defaults) {
        if (source == null || target == null || totalSourceLength <= EPSILON) {
            return StationMigrationResult.rejected("invalid extract args");
        }
        if (rangeEnd <= rangeStart + EPSILON) {
            target.setVerticalAlignment(null);
            target.setVariableCrossSections(null);
            target.setStationFacilities(null);
            return StationMigrationResult.ok(false);
        }
        double stationOffset = -rangeStart;
        RoadCrossSection defaultSection = source.getCrossSection();
        RoadVerticalAlignment sourceVa = source.getVerticalAlignment();
        RoadVerticalAlignment extractedVa = null;
        if (sourceVa != null && !sourceVa.isEmpty()) {
            extractedVa = extractVerticalAlignmentRange(sourceVa, rangeStart, rangeEnd);
            if (extractedVa == null) {
                return StationMigrationResult.rejected("VA extract not lossless");
            }
        }
        RoadVariableCrossSections extractedVcs;
        try {
            extractedVcs = remapVariableCrossSections(
                source.getVariableCrossSections(),
                defaultSection,
                totalSourceLength,
                rangeStart,
                rangeEnd,
                stationOffset,
                defaults);
        } catch (StationMigrationRefuseException e) {
            return StationMigrationResult.rejected(e.getMessage());
        }
        RoadStationFacilities extractedFac = remapStationFacilities(
            source.getStationFacilities(),
            totalSourceLength,
            rangeStart,
            rangeEnd,
            stationOffset);
        target.setVerticalAlignment(extractedVa);
        target.setVariableCrossSections(nullIfEmpty(extractedVcs));
        target.setStationFacilities(extractedFac);
        return StationMigrationResult.ok(false);
    }

    /**
     * Contiguous physical station runs for {@code edgeIds} in walk order.
     * Gaps (deleted middle / seam wrap) start a new run — never min/max across holes.
     */
    public static List<StationRange> computeComponentStationRanges(
            RoadNetwork network,
            Road road,
            Set<String> edgeIds) {
        if (network == null || road == null || edgeIds == null || edgeIds.isEmpty()) {
            return List.of();
        }
        List<StationRange> ranges = new ArrayList<>();
        double runStart = Double.NaN;
        double runEnd = Double.NaN;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            if (!edgeIds.contains(segment.edgeId())) {
                if (!Double.isNaN(runStart)) {
                    ranges.add(new StationRange(runStart, runEnd));
                    runStart = Double.NaN;
                    runEnd = Double.NaN;
                }
                continue;
            }
            double cStart = RoadStationing.toCanonicalChainage(
                network, road, segment.startStation());
            double cEnd = RoadStationing.toCanonicalChainage(
                network, road, segment.endStation());
            if (!Double.isFinite(cStart) || !Double.isFinite(cEnd)) {
                return List.of();
            }
            if (Double.isNaN(runStart)) {
                runStart = cStart;
                runEnd = cEnd;
            } else if (Math.abs(cStart - runEnd) <= EPSILON) {
                runEnd = cEnd;
            } else {
                ranges.add(new StationRange(runStart, runEnd));
                runStart = cStart;
                runEnd = cEnd;
            }
        }
        if (!Double.isNaN(runStart)) {
            ranges.add(new StationRange(runStart, runEnd));
        }
        return ranges;
    }

    /**
     * Single contiguous envelope when the component is one physical run; otherwise invalid.
     */
    public static StationRange computeComponentStationRange(
            RoadNetwork network,
            Road road,
            Set<String> edgeIds) {
        List<StationRange> ranges = computeComponentStationRanges(network, road, edgeIds);
        if (ranges.size() != 1) {
            return StationRange.invalid();
        }
        return ranges.getFirst();
    }

    public record StationRange(double start, double end) {
        public static StationRange invalid() {
            return new StationRange(0.0, 0.0);
        }

        public boolean isValid() {
            return end > start + EPSILON;
        }

        public double length() {
            return end - start;
        }
    }

    public record StationDataSnapshot(
            RoadVerticalAlignment verticalAlignment,
            RoadVariableCrossSections variableCrossSections,
            RoadStationFacilities stationFacilities,
            boolean hadHorizontalAlignment) {

        public static StationDataSnapshot capture(Road road) {
            if (road == null) {
                return new StationDataSnapshot(null, null, null, false);
            }
            return new StationDataSnapshot(
                road.getVerticalAlignment(),
                road.getVariableCrossSections(),
                road.getStationFacilities(),
                road.getHorizontalAlignment() != null);
        }

        public StationMigrationResult applyRangeTo(
                Road target, double totalSourceLength, StationRange range) {
            return applyRangeTo(target, totalSourceLength, range, null);
        }

        public StationMigrationResult applyRangeTo(
                Road target,
                double totalSourceLength,
                StationRange range,
                RoadSystemConfig defaults) {
            if (target == null || !range.isValid()) {
                return StationMigrationResult.rejected("invalid apply range");
            }
            Road scratch = new Road("snapshot");
            scratch.setVerticalAlignment(verticalAlignment);
            scratch.setVariableCrossSections(variableCrossSections);
            scratch.setStationFacilities(stationFacilities);
            scratch.setCrossSection(target.getCrossSection());
            return applyStationRangeExtract(
                scratch, target, totalSourceLength, range.start(), range.end(), defaults);
        }

        public boolean hasPhase2Data() {
            return verticalAlignment != null
                || variableCrossSections != null
                || stationFacilities != null
                || hadHorizontalAlignment;
        }
    }

    /**
     * Extract VA for {@code [rangeStart, rangeEnd]} remapped to {@code [0, L]}.
     * Inserts real boundary elevations; refuses interior vertical-curve cuts.
     */
    static RoadVerticalAlignment extractVerticalAlignmentRange(
            RoadVerticalAlignment source,
            double rangeStart,
            double rangeEnd) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return null;
        }
        if (!VerticalAlignmentGeometry.isEvaluable(source)) {
            return null;
        }
        List<PointOfVerticalIntersection> sorted = source.sortedPvis();
        for (VerticalAlignmentValidator.VerticalCurveInterval curve
                : VerticalAlignmentValidator.curveIntervals(sorted)) {
            boolean startOk = Math.abs(rangeStart - curve.bvcStation()) <= EPSILON
                || Math.abs(rangeStart - curve.evcStation()) <= EPSILON
                || rangeStart <= curve.bvcStation() + EPSILON
                || rangeStart >= curve.evcStation() - EPSILON;
            boolean endOk = Math.abs(rangeEnd - curve.bvcStation()) <= EPSILON
                || Math.abs(rangeEnd - curve.evcStation()) <= EPSILON
                || rangeEnd <= curve.bvcStation() + EPSILON
                || rangeEnd >= curve.evcStation() - EPSILON;
            boolean startInterior = rangeStart > curve.bvcStation() + EPSILON
                && rangeStart < curve.evcStation() - EPSILON;
            boolean endInterior = rangeEnd > curve.bvcStation() + EPSILON
                && rangeEnd < curve.evcStation() - EPSILON;
            if (startInterior || endInterior) {
                return null;
            }
            // Curve must be wholly inside or wholly outside the kept range.
            boolean whollyInside = curve.bvcStation() >= rangeStart - EPSILON
                && curve.evcStation() <= rangeEnd + EPSILON;
            boolean whollyOutside = curve.evcStation() <= rangeStart + EPSILON
                || curve.bvcStation() >= rangeEnd - EPSILON;
            if (!whollyInside && !whollyOutside && !startOk && !endOk) {
                return null;
            }
            if (!whollyInside && !whollyOutside
                    && (curve.bvcStation() < rangeEnd - EPSILON
                        && curve.evcStation() > rangeStart + EPSILON)
                    && !(Math.abs(rangeStart - curve.bvcStation()) <= EPSILON
                        || Math.abs(rangeStart - curve.evcStation()) <= EPSILON
                        || Math.abs(rangeEnd - curve.bvcStation()) <= EPSILON
                        || Math.abs(rangeEnd - curve.evcStation()) <= EPSILON)) {
                return null;
            }
        }

        OptionalDouble startElev = VerticalAlignmentGeometry.elevationAt(source, rangeStart);
        OptionalDouble endElev = VerticalAlignmentGeometry.elevationAt(source, rangeEnd);
        if (startElev.isEmpty() || endElev.isEmpty()) {
            return null;
        }

        VerticalControlPointConstraint startConstraint = VerticalControlPointConstraint.FREE;
        VerticalControlPointConstraint endConstraint = VerticalControlPointConstraint.FREE;
        for (PointOfVerticalIntersection pvi : sorted) {
            if (Math.abs(pvi.getStation() - rangeStart) <= EPSILON) {
                startConstraint = pvi.getConstraint() != null
                    ? pvi.getConstraint()
                    : VerticalControlPointConstraint.FREE;
            }
            if (Math.abs(pvi.getStation() - rangeEnd) <= EPSILON) {
                endConstraint = pvi.getConstraint() != null
                    ? pvi.getConstraint()
                    : VerticalControlPointConstraint.FREE;
            }
        }

        double length = rangeEnd - rangeStart;
        List<PointOfVerticalIntersection> remapped = new ArrayList<>();
        remapped.add(new PointOfVerticalIntersection(
            0.0, startElev.getAsDouble(), null, startConstraint));
        for (PointOfVerticalIntersection pvi : sorted) {
            double station = pvi.getStation();
            if (station <= rangeStart + EPSILON || station >= rangeEnd - EPSILON) {
                continue;
            }
            // Drop curve length on PVIs whose parabola would cross the cut (already refused above).
            remapped.add(new PointOfVerticalIntersection(
                station - rangeStart,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        remapped.add(new PointOfVerticalIntersection(
            length, endElev.getAsDouble(), null, endConstraint));
        remapped.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
        List<PointOfVerticalIntersection> deduped = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : remapped) {
            if (!deduped.isEmpty()
                    && Math.abs(deduped.getLast().getStation() - pvi.getStation()) <= EPSILON) {
                continue;
            }
            deduped.add(pvi);
        }
        RoadVerticalAlignment result = new RoadVerticalAlignment(deduped);
        return VerticalAlignmentGeometry.isEvaluable(result) ? result : null;
    }

    static RoadVerticalAlignment remapVerticalAlignment(
            RoadVerticalAlignment source,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        RoadVerticalAlignment extracted = extractVerticalAlignmentRange(source, rangeStart, rangeEnd);
        if (extracted == null) {
            return null;
        }
        if (Math.abs(stationOffset) <= EPSILON) {
            return extracted;
        }
        List<PointOfVerticalIntersection> shifted = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : extracted.sortedPvis()) {
            shifted.add(new PointOfVerticalIntersection(
                pvi.getStation() + stationOffset,
                pvi.getElevation(),
                pvi.getCurveLength(),
                pvi.getConstraint()));
        }
        return new RoadVerticalAlignment(shifted);
    }

    static RoadVerticalAlignment trimHeadVerticalAlignment(
            RoadVerticalAlignment source,
            double splitStation) {
        return extractVerticalAlignmentRange(source, 0.0, splitStation);
    }

    static RoadVerticalAlignment extractTailVerticalAlignment(
            RoadVerticalAlignment source,
            double splitStation,
            double totalLength) {
        return extractVerticalAlignmentRange(source, splitStation, totalLength);
    }

    /** @deprecated prefer {@link #extractTailVerticalAlignment(RoadVerticalAlignment, double, double)} */
    @Deprecated
    static RoadVerticalAlignment extractTailVerticalAlignment(
            RoadVerticalAlignment source,
            double splitStation) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return extractVerticalAlignmentRange(
            source, splitStation, source.sortedPvis().getLast().getStation());
    }

    static RoadVariableCrossSections trimHeadVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength) {
        return trimHeadVariableCrossSections(source, roadDefault, splitStation, totalLength, null);
    }

    static RoadVariableCrossSections trimHeadVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength,
            RoadSystemConfig defaults) {
        return remapVariableCrossSections(
            source, roadDefault, totalLength, 0.0, splitStation, 0.0, defaults);
    }

    static RoadVariableCrossSections extractTailVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength) {
        return extractTailVariableCrossSections(
            source, roadDefault, splitStation, totalLength, null);
    }

    static RoadVariableCrossSections extractTailVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double splitStation,
            double totalLength,
            RoadSystemConfig defaults) {
        return remapVariableCrossSections(
            source,
            roadDefault,
            totalLength,
            splitStation,
            totalLength,
            -splitStation,
            defaults);
    }

    static RoadStationFacilities trimHeadStationFacilities(
            RoadStationFacilities source,
            double splitStation,
            double totalLength) {
        return remapStationFacilities(source, totalLength, 0.0, splitStation, 0.0);
    }

    static RoadStationFacilities extractTailStationFacilities(
            RoadStationFacilities source,
            double splitStation,
            double totalLength) {
        return remapStationFacilities(source, totalLength, splitStation, totalLength, -splitStation);
    }

    static RoadVerticalAlignment mergeVerticalAlignments(
            RoadVerticalAlignment head,
            RoadVerticalAlignment tail,
            double headLength) {
        boolean hasHead = head != null && !head.isEmpty();
        boolean hasTail = tail != null && !tail.isEmpty();
        if (!hasHead && !hasTail) {
            return null;
        }
        if (hasHead && hasTail) {
            OptionalDouble headEnd = VerticalAlignmentGeometry.elevationAt(head, headLength);
            if (headEnd.isEmpty() && !head.sortedPvis().isEmpty()) {
                headEnd = OptionalDouble.of(head.sortedPvis().getLast().getElevation());
            }
            OptionalDouble tailStart = VerticalAlignmentGeometry.elevationAt(tail, 0.0);
            if (headEnd.isEmpty() || tailStart.isEmpty()) {
                return null;
            }
            if (Math.abs(headEnd.getAsDouble() - tailStart.getAsDouble()) > 1e-3) {
                return null;
            }
        }
        List<PointOfVerticalIntersection> merged = new ArrayList<>();
        if (hasHead) {
            for (PointOfVerticalIntersection pvi : head.sortedPvis()) {
                merged.add(pvi.copy());
            }
        }
        if (hasTail) {
            for (PointOfVerticalIntersection pvi : tail.sortedPvis()) {
                double station = pvi.getStation() + headLength;
                if (!merged.isEmpty()) {
                    PointOfVerticalIntersection last = merged.getLast();
                    if (Math.abs(last.getStation() - station) <= EPSILON) {
                        if (Math.abs(last.getElevation() - pvi.getElevation()) > 1e-3) {
                            return null;
                        }
                        VerticalControlPointConstraint mergedConstraint = mergeConstraints(
                            last.getConstraint(), pvi.getConstraint());
                        if (mergedConstraint == null) {
                            return null;
                        }
                        if (mergedConstraint != last.getConstraint()) {
                            merged.set(merged.size() - 1, new PointOfVerticalIntersection(
                                last.getStation(),
                                last.getElevation(),
                                last.getCurveLength(),
                                mergedConstraint));
                        }
                        continue;
                    }
                }
                merged.add(new PointOfVerticalIntersection(
                    station,
                    pvi.getElevation(),
                    pvi.getCurveLength(),
                    pvi.getConstraint()));
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        RoadVerticalAlignment result = new RoadVerticalAlignment(merged);
        return VerticalAlignmentGeometry.isEvaluable(result) ? result : null;
    }

    /** FREE yields to a concrete constraint; incompatible non-FREE pair → null (refuse). */
    private static VerticalControlPointConstraint mergeConstraints(
            VerticalControlPointConstraint headConstraint,
            VerticalControlPointConstraint tailConstraint) {
        VerticalControlPointConstraint head = headConstraint != null
            ? headConstraint : VerticalControlPointConstraint.FREE;
        VerticalControlPointConstraint tail = tailConstraint != null
            ? tailConstraint : VerticalControlPointConstraint.FREE;
        if (head == VerticalControlPointConstraint.FREE) {
            return tail;
        }
        if (tail == VerticalControlPointConstraint.FREE) {
            return head;
        }
        return head == tail ? head : null;
    }

    static RoadVariableCrossSections mergeVariableCrossSections(
            RoadVariableCrossSections head,
            RoadVariableCrossSections tail,
            RoadCrossSection headDefault,
            RoadCrossSection tailDefault,
            double headLength,
            double tailLength) {
        return mergeVariableCrossSections(
            head, tail, headDefault, tailDefault, headLength, tailLength, null);
    }

    static RoadVariableCrossSections mergeVariableCrossSections(
            RoadVariableCrossSections head,
            RoadVariableCrossSections tail,
            RoadCrossSection headDefault,
            RoadCrossSection tailDefault,
            double headLength,
            double tailLength,
            RoadSystemConfig defaults) {
        VariableCrossSectionTransitionMode headMode = head != null
            ? head.getTransitionMode()
            : VariableCrossSectionTransitionMode.STEP;
        VariableCrossSectionTransitionMode tailMode = tail != null
            ? tail.getTransitionMode()
            : headMode;
        boolean headHas = head != null && !head.isEmpty();
        boolean tailHas = tail != null && !tail.isEmpty();
        if (headHas && tailHas && headMode != tailMode) {
            return null;
        }
        VariableCrossSectionTransitionMode mode = headHas ? headMode : tailMode;
        RoadCrossSection mergedDefault = headDefault != null ? headDefault : new RoadCrossSection();
        RoadCrossSection tailSectionDefault = tailDefault != null ? tailDefault : mergedDefault;
        List<Interval> intervals = new ArrayList<>();
        intervals.addAll(intervalsForRoad(head, mergedDefault, headLength, 0.0));
        if (tailLength > EPSILON) {
            intervals.addAll(intervalsForRoad(tail, tailSectionDefault, tailLength, headLength));
        }
        intervals = mergeAdjacentIntervals(intervals);
        RoadVariableCrossSections result =
            intervalsToVariableCrossSections(intervals, mergedDefault, mode);
        if (mode == VariableCrossSectionTransitionMode.SMOOTH
                && !smoothVcsPhysicallySound(
                    head,
                    tail,
                    mergedDefault,
                    tailSectionDefault,
                    headLength,
                    tailLength,
                    result,
                    defaults)) {
            return null;
        }
        return result;
    }

    private static boolean smoothVcsPhysicallySound(
            RoadVariableCrossSections head,
            RoadVariableCrossSections tail,
            RoadCrossSection headDefault,
            RoadCrossSection tailDefault,
            double headLength,
            double tailLength,
            RoadVariableCrossSections merged,
            RoadSystemConfig defaults) {
        if (merged == null) {
            return false;
        }
        RoadCrossSection mergedDefault = headDefault != null ? headDefault : new RoadCrossSection();
        RoadCrossSection tailSectionDefault = tailDefault != null ? tailDefault : mergedDefault;
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("plot");
        double total = headLength + tailLength;
        double step = Math.max(total / 32.0, 0.5);
        for (double s = 0.0; s < total - EPSILON; s += step) {
            ResolvedCrossSection expected;
            if (s < headLength - EPSILON) {
                expected = VariableCrossSectionResolver.resolve(
                    mergedDefault,
                    head != null ? head.sortedStations() : List.of(),
                    VariableCrossSectionTransitionMode.SMOOTH,
                    s,
                    config);
            } else {
                double local = Math.max(0.0, s - headLength);
                expected = VariableCrossSectionResolver.resolve(
                    tailSectionDefault,
                    tail != null ? tail.sortedStations() : List.of(),
                    VariableCrossSectionTransitionMode.SMOOTH,
                    local,
                    config);
            }
            ResolvedCrossSection actual = VariableCrossSectionResolver.resolve(
                mergedDefault,
                merged.sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH,
                s,
                config);
            if (!expected.displayEquals(actual)) {
                return false;
            }
        }
        return true;
    }

    static RoadStationFacilities mergeStationFacilities(
            RoadStationFacilities head,
            RoadStationFacilities tail,
            double headLength,
            double tailLength) {
        List<StationFacilityRun> merged = new ArrayList<>();
        if (head != null) {
            for (StationFacilityRun run : head.getRuns()) {
                double end = run.getEndStation() != null ? run.getEndStation() : headLength;
                if (!(end > run.getStartStation())) {
                    continue;
                }
                merged.add(new StationFacilityRun(
                    run.getStartStation(),
                    end,
                    run.getKind(),
                    run.getSide(),
                    run.getMaterial(),
                    run.getHeight()));
            }
        }
        if (tail != null) {
            for (StationFacilityRun run : tail.getRuns()) {
                double start = run.getStartStation() + headLength;
                double end = (run.getEndStation() != null ? run.getEndStation() : tailLength)
                    + headLength;
                if (!(end > start)) {
                    continue;
                }
                if (!merged.isEmpty()) {
                    StationFacilityRun last = merged.getLast();
                    if (Math.abs(last.getStartStation() - start) <= EPSILON
                            && Math.abs((last.getEndStation() != null ? last.getEndStation() : -1)
                                - end) <= EPSILON
                            && last.getKind() == run.getKind()
                            && last.getSide() == run.getSide()
                            && java.util.Objects.equals(last.getMaterial(), run.getMaterial())
                            && java.util.Objects.equals(last.getHeight(), run.getHeight())) {
                        continue;
                    }
                }
                merged.add(new StationFacilityRun(
                    start,
                    end,
                    run.getKind(),
                    run.getSide(),
                    run.getMaterial(),
                    run.getHeight()));
            }
        }
        merged.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return merged.isEmpty() ? null : new RoadStationFacilities(merged);
    }

    private static List<Interval> intervalsForRoad(
            RoadVariableCrossSections variable,
            RoadCrossSection roadDefault,
            double totalLength,
            double stationOffset) {
        List<Interval> intervals = buildIntervals(
            variable != null ? variable.sortedStations() : List.of(),
            roadDefault,
            totalLength);
        if (Math.abs(stationOffset) <= EPSILON) {
            return intervals;
        }
        List<Interval> shifted = new ArrayList<>(intervals.size());
        for (Interval interval : intervals) {
            shifted.add(new Interval(
                interval.start + stationOffset,
                interval.end + stationOffset,
                interval.template));
        }
        return shifted;
    }

    private static RoadVariableCrossSections remapVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        return remapVariableCrossSections(
            source, roadDefault, totalLength, rangeStart, rangeEnd, stationOffset, null);
    }

    private static RoadVariableCrossSections remapVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection roadDefault,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset,
            RoadSystemConfig defaults) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return source == null || source.isEmpty()
                ? new RoadVariableCrossSections(List.of(), VariableCrossSectionTransitionMode.STEP)
                : null;
        }
        VariableCrossSectionTransitionMode mode = source.getTransitionMode();
        RoadCrossSection defaultSection = roadDefault != null ? roadDefault : new RoadCrossSection();
        if (mode == VariableCrossSectionTransitionMode.SMOOTH) {
            return remapSmoothVariableCrossSections(
                source, defaultSection, totalLength, rangeStart, rangeEnd, stationOffset, defaults);
        }
        List<Interval> intervals = buildIntervals(source.sortedStations(), defaultSection, totalLength);
        List<Interval> clipped = new ArrayList<>();
        for (Interval interval : intervals) {
            double start = Math.max(interval.start, rangeStart);
            double end = Math.min(interval.end, rangeEnd);
            if (end > start + EPSILON) {
                clipped.add(new Interval(start + stationOffset, end + stationOffset, interval.template));
            }
        }
        clipped = mergeAdjacentIntervals(clipped);
        return intervalsToVariableCrossSections(clipped, defaultSection, mode);
    }

    private static RoadVariableCrossSections remapSmoothVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset,
            RoadSystemConfig defaults) {
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("plot");
        List<StationCrossSection> sorted = source.sortedStations();
        List<StationCrossSection> remapped = new ArrayList<>();
        // Boundary samples preserve continuous interpolation at cut ends.
        RoadCrossSection startSection = materializeSmoothAt(
            source, defaultSection, rangeStart, config);
        RoadCrossSection endSection = materializeSmoothAt(
            source, defaultSection, rangeEnd, config);
        if (startSection == null || endSection == null) {
            throw new StationMigrationRefuseException("SMOOTH boundary sample failed");
        }
        remapped.add(StationCrossSection.at(rangeStart + stationOffset, startSection));
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() <= rangeStart + EPSILON
                    || entry.getStation() >= rangeEnd - EPSILON) {
                continue;
            }
            remapped.add(StationCrossSection.at(
                entry.getStation() + stationOffset, entry.getCrossSection().copy()));
        }
        remapped.add(StationCrossSection.at(rangeEnd + stationOffset, endSection));
        remapped.sort(Comparator.comparingDouble(StationCrossSection::getStation));
        List<StationCrossSection> deduped = new ArrayList<>();
        for (StationCrossSection entry : remapped) {
            if (!deduped.isEmpty()
                    && Math.abs(deduped.getLast().getStation() - entry.getStation()) <= EPSILON) {
                continue;
            }
            deduped.add(entry);
        }
        // Shift so range maps to [0, L] when stationOffset == -rangeStart.
        RoadVariableCrossSections result =
            new RoadVariableCrossSections(deduped, VariableCrossSectionTransitionMode.SMOOTH);
        if (!result.isValid()) {
            throw new StationMigrationRefuseException("SMOOTH split invalid");
        }
        double length = rangeEnd - rangeStart;
        double step = Math.max(length / 32.0, 0.5);
        for (double s = rangeStart; s < rangeEnd - EPSILON; s += step) {
            ResolvedCrossSection before = VariableCrossSectionResolver.resolve(
                defaultSection, sorted, VariableCrossSectionTransitionMode.SMOOTH, s, config);
            double afterStation = s + stationOffset;
            ResolvedCrossSection after = VariableCrossSectionResolver.resolve(
                defaultSection, result.sortedStations(), VariableCrossSectionTransitionMode.SMOOTH,
                afterStation, config);
            if (!before.displayEquals(after)) {
                throw new StationMigrationRefuseException("SMOOTH split displayEquals failed");
            }
        }
        return result;
    }

    private static RoadCrossSection materializeSmoothAt(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double station,
            RoadSystemConfig config) {
        return interpolateSmoothCrossSection(source, defaultSection, station, config);
    }

    private static RoadStationFacilities remapStationFacilities(
            RoadStationFacilities source,
            double totalLength,
            double rangeStart,
            double rangeEnd,
            double stationOffset) {
        if (source == null || source.isEmpty() || rangeEnd <= rangeStart + EPSILON) {
            return null;
        }
        List<StationFacilityRun> remapped = new ArrayList<>();
        for (StationFacilityRun run : source.getRuns()) {
            double runStart = run.getStartStation();
            double runEnd = run.getEndStation() != null ? run.getEndStation() : totalLength;
            double start = Math.max(runStart, rangeStart);
            double end = Math.min(runEnd, rangeEnd);
            if (end <= start + EPSILON) {
                continue;
            }
            Double endStation = end >= totalLength - EPSILON && run.getEndStation() == null
                ? null
                : end + stationOffset;
            remapped.add(new StationFacilityRun(
                start + stationOffset,
                endStation,
                run.getKind(),
                run.getSide(),
                run.getMaterial(),
                run.getHeight()));
        }
        remapped.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return remapped.isEmpty() ? null : new RoadStationFacilities(remapped);
    }

    private static List<Interval> buildIntervals(
            List<StationCrossSection> sorted,
            RoadCrossSection defaultSection,
            double totalLength) {
        List<Interval> intervals = new ArrayList<>();
        double previous = 0.0;
        RoadCrossSection active = defaultSection;
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() > previous + EPSILON) {
                intervals.add(new Interval(previous, entry.getStation(), active));
            }
            previous = entry.getStation();
            active = entry.getCrossSection();
        }
        if (totalLength > previous + EPSILON) {
            intervals.add(new Interval(previous, totalLength, active));
        }
        return intervals;
    }

    private static List<Interval> mergeAdjacentIntervals(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return intervals;
        }
        intervals.sort(Comparator.comparingDouble(interval -> interval.start));
        List<Interval> merged = new ArrayList<>();
        Interval current = intervals.getFirst();
        for (int i = 1; i < intervals.size(); i++) {
            Interval next = intervals.get(i);
            if (Math.abs(next.start - current.end) <= EPSILON
                    && RoadCrossSectionEngineeringEquality.equals(next.template, current.template)) {
                current = new Interval(current.start, next.end, current.template);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static RoadVariableCrossSections intervalsToVariableCrossSections(
            List<Interval> intervals,
            RoadCrossSection defaultSection) {
        return intervalsToVariableCrossSections(
            intervals, defaultSection, VariableCrossSectionTransitionMode.STEP);
    }

    private static RoadVariableCrossSections intervalsToVariableCrossSections(
            List<Interval> intervals,
            RoadCrossSection defaultSection,
            VariableCrossSectionTransitionMode transitionMode) {
        List<StationCrossSection> stations = new ArrayList<>();
        for (Interval interval : intervals) {
            if (interval.start <= EPSILON) {
                if (!RoadCrossSectionEngineeringEquality.equals(interval.template, defaultSection)) {
                    stations.add(StationCrossSection.at(0.0, interval.template.copy()));
                }
                continue;
            }
            stations.add(StationCrossSection.at(interval.start, interval.template.copy()));
        }
        // Empty = success clear (all default); never null — null is reserved for hard failure upstream.
        return new RoadVariableCrossSections(stations, transitionMode);
    }

    /** Persist empty VCS as cleared ({@code null}) on the road model. */
    private static RoadVariableCrossSections nullIfEmpty(RoadVariableCrossSections sections) {
        return sections == null || sections.isEmpty() ? null : sections;
    }

    private record Interval(double start, double end, RoadCrossSection template) {
    }

    /**
     * LOOP seam migrate: rotate station-domain engineering data so physical placement is unchanged.
     * Returns {@code false} without mutating when migration would be lossy (e.g. mid-curve seam).
     */
    public static boolean rotateLoopStations(
            com.plot.plugin.road.model.RoadNetwork network,
            com.plot.plugin.road.model.Road road,
            double shiftMeters,
            double loopLength) {
        return rotateLoopStations(network, road, shiftMeters, loopLength, null);
    }

    public static boolean rotateLoopStations(
            com.plot.plugin.road.model.RoadNetwork network,
            com.plot.plugin.road.model.Road road,
            double shiftMeters,
            double loopLength,
            RoadSystemConfig defaults) {
        if (road == null || loopLength <= EPSILON) {
            return true;
        }
        if (Math.abs(shiftMeters) <= EPSILON) {
            return true;
        }
        if (network == null) {
            return rotateLoopStations(road, shiftMeters, loopLength, defaults);
        }
        // Compute all crossing/stack replacements first — fail without mutating anything.
        List<com.plot.plugin.road.crossing.RoadCrossing> crossingUpdates = new ArrayList<>();
        for (com.plot.plugin.road.crossing.RoadCrossing crossing : network.crossingsForRoad(road.getId())) {
            double rotatedStation = RoadLoopSeamService.rotateLoopStation(
                crossing.stationOn(road.getId()), shiftMeters, loopLength);
            if (!Double.isFinite(rotatedStation)) {
                return false;
            }
            crossingUpdates.add(crossing.withStationsOnRoad(road.getId(), rotatedStation));
        }
        List<com.plot.plugin.road.crossing.RoadStackCrossing> stackUpdates = new ArrayList<>();
        for (com.plot.plugin.road.crossing.RoadStackCrossing stack
                : network.stackCrossingsForRoad(road.getId())) {
            double rotatedA = RoadLoopSeamService.rotateLoopStation(
                stack.stationA(), shiftMeters, loopLength);
            double rotatedB = RoadLoopSeamService.rotateLoopStation(
                stack.stationB(), shiftMeters, loopLength);
            if (!Double.isFinite(rotatedA) || !Double.isFinite(rotatedB)) {
                return false;
            }
            Double rotatedLower = stack.lowerStation() != null
                ? RoadLoopSeamService.rotateLoopStation(stack.lowerStation(), shiftMeters, loopLength)
                : null;
            Double rotatedUpper = stack.upperStation() != null
                ? RoadLoopSeamService.rotateLoopStation(stack.upperStation(), shiftMeters, loopLength)
                : null;
            if ((rotatedLower != null && !Double.isFinite(rotatedLower))
                    || (rotatedUpper != null && !Double.isFinite(rotatedUpper))) {
                return false;
            }
            stackUpdates.add(stack.withRotatedStations(
                rotatedA, rotatedB, rotatedLower, rotatedUpper));
        }
        if (!rotateLoopStations(road, shiftMeters, loopLength, defaults)) {
            return false;
        }
        for (com.plot.plugin.road.crossing.RoadCrossing update : crossingUpdates) {
            network.registerCrossing(update);
        }
        for (com.plot.plugin.road.crossing.RoadStackCrossing update : stackUpdates) {
            network.registerStackCrossing(update);
        }
        return true;
    }

    /**
     * LOOP seam migrate for in-road station data only. Fail-closed on lossy VA/VCS cases.
     */
    public static boolean rotateLoopStations(Road road, double shiftMeters, double loopLength) {
        return rotateLoopStations(road, shiftMeters, loopLength, null);
    }

    public static boolean rotateLoopStations(
            Road road,
            double shiftMeters,
            double loopLength,
            RoadSystemConfig defaults) {
        if (road == null || loopLength <= EPSILON) {
            return true;
        }
        if (Math.abs(shiftMeters) <= EPSILON) {
            return true;
        }
        RoadVerticalAlignment rotatedVa = null;
        if (road.getVerticalAlignment() != null && !road.getVerticalAlignment().isEmpty()) {
            rotatedVa = rotateVerticalAlignmentCyclic(road.getVerticalAlignment(), shiftMeters, loopLength);
            if (rotatedVa == null) {
                return false;
            }
        }
        boolean clearVcs = false;
        RoadVariableCrossSections rotatedVcs = null;
        if (road.getVariableCrossSections() != null && !road.getVariableCrossSections().isEmpty()) {
            if (!road.getVariableCrossSections().isValid()) {
                return false;
            }
            rotatedVcs = rotateVariableCrossSectionsCyclic(
                road.getVariableCrossSections(),
                road.getCrossSection(),
                shiftMeters,
                loopLength,
                defaults);
            if (rotatedVcs == null) {
                return false;
            }
            clearVcs = rotatedVcs.isEmpty();
        }
        RoadStationFacilities rotatedFacilities = null;
        if (road.getStationFacilities() != null && !road.getStationFacilities().isEmpty()) {
            rotatedFacilities = rotateStationFacilitiesCyclic(
                road.getStationFacilities(), shiftMeters, loopLength);
            if (rotatedFacilities == null) {
                return false;
            }
        }
        if (rotatedVa != null) {
            road.setVerticalAlignment(rotatedVa);
        }
        if (clearVcs) {
            road.setVariableCrossSections(null);
        } else if (rotatedVcs != null) {
            road.setVariableCrossSections(rotatedVcs);
        }
        if (rotatedFacilities != null) {
            road.setStationFacilities(rotatedFacilities);
        }
        return true;
    }

    /**
     * Cyclic VA migrate. Returns null when seam falls inside a vertical curve or 0/L elevations mismatch.
     */
    static RoadVerticalAlignment rotateVerticalAlignmentCyclic(
            RoadVerticalAlignment source,
            double shift,
            double loopLength) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<PointOfVerticalIntersection> sorted = source.sortedPvis();
        if (sorted.size() < 2) {
            return null;
        }
        PointOfVerticalIntersection first = sorted.getFirst();
        PointOfVerticalIntersection last = sorted.getLast();
        if (Math.abs(first.getStation()) > EPSILON
                || Math.abs(last.getStation() - loopLength) > EPSILON) {
            // Still allow migrate if endpoints bookend the loop within tolerance of evaluable range.
            if (!VerticalAlignmentGeometry.isEvaluable(source)) {
                return null;
            }
        }
        OptionalDouble elev0 = VerticalAlignmentGeometry.elevationAt(source, 0.0);
        OptionalDouble elevL = VerticalAlignmentGeometry.elevationAt(source, loopLength);
        if (elev0.isEmpty() || elevL.isEmpty()
                || Math.abs(elev0.getAsDouble() - elevL.getAsDouble()) > 1e-3) {
            return null;
        }
        // Curved PVI station is still mid-parabola (PVI ≠ road surface). Refuse that and
        // any strict interior; allow exactly BVC/EVC (tangent junctions).
        for (VerticalAlignmentValidator.VerticalCurveInterval curve
                : VerticalAlignmentValidator.curveIntervals(sorted)) {
            boolean atBvc = Math.abs(shift - curve.bvcStation()) <= EPSILON;
            boolean atEvc = Math.abs(shift - curve.evcStation()) <= EPSILON;
            if (atBvc || atEvc) {
                continue;
            }
            if (shift >= curve.bvcStation() - EPSILON && shift <= curve.evcStation() + EPSILON) {
                return null;
            }
        }
        OptionalDouble seamElevOpt = VerticalAlignmentGeometry.elevationAt(source, shift);
        if (seamElevOpt.isEmpty()) {
            return null;
        }
        double seamElev = seamElevOpt.getAsDouble();
        // If seam lands on an existing non-curve PVI, keep that physical point's constraint
        // on the new 0/L ends; truly synthesized ends stay FREE.
        VerticalControlPointConstraint endConstraint = VerticalControlPointConstraint.FREE;
        for (PointOfVerticalIntersection pvi : sorted) {
            if (Math.abs(pvi.getStation() - shift) <= EPSILON) {
                endConstraint = pvi.getConstraint() != null
                    ? pvi.getConstraint()
                    : VerticalControlPointConstraint.FREE;
                break;
            }
        }
        List<PointOfVerticalIntersection> rotated = new ArrayList<>();
        rotated.add(new PointOfVerticalIntersection(
            0.0, seamElev, null, endConstraint));
        for (PointOfVerticalIntersection pvi : sorted) {
            // Old L duplicates old 0 physically on a LOOP — keep the start endpoint once.
            if (Math.abs(pvi.getStation() - loopLength) <= EPSILON) {
                continue;
            }
            if (Math.abs(pvi.getStation() - shift) <= EPSILON) {
                // Seam lands on this PVI: elev/constraint already applied to new ends.
                continue;
            }
            double station = CyclicStationTransform.rotateByShift(pvi.getStation(), shift, loopLength);
            if (!Double.isFinite(station)) {
                return null;
            }
            if (Math.abs(station) <= EPSILON || Math.abs(station - loopLength) <= EPSILON) {
                // Physical point coincides with new seam ends — ends already carry constraint.
                continue;
            }
            // Endpoints lose curve length; intermediate PVIs keep theirs and constraints.
            Double curve = Math.abs(pvi.getStation()) <= EPSILON ? null : pvi.getCurveLength();
            rotated.add(new PointOfVerticalIntersection(
                station, pvi.getElevation(), curve, pvi.getConstraint()));
        }
        rotated.add(new PointOfVerticalIntersection(
            loopLength, seamElev, null, endConstraint));
        rotated.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
        List<PointOfVerticalIntersection> deduped = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : rotated) {
            if (!deduped.isEmpty()
                    && Math.abs(deduped.getLast().getStation() - pvi.getStation()) <= EPSILON) {
                continue;
            }
            deduped.add(pvi);
        }
        RoadVerticalAlignment result = new RoadVerticalAlignment(deduped);
        if (!VerticalAlignmentGeometry.isEvaluable(result)) {
            return null;
        }
        return result;
    }

    static RoadVariableCrossSections rotateVariableCrossSectionsCyclic(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double shift,
            double loopLength,
            RoadSystemConfig defaults) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        // Non-empty invalid VCS is migrate failure — never success-clear.
        if (!source.isValid()) {
            return null;
        }
        VariableCrossSectionTransitionMode mode = source.getTransitionMode();
        RoadCrossSection base = defaultSection != null ? defaultSection : new RoadCrossSection();
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("plot");
        if (mode == VariableCrossSectionTransitionMode.SMOOTH) {
            return rotateSmoothVariableCrossSections(source, base, shift, loopLength, config);
        }
        List<StationCrossSection> sorted = source.sortedStations();
        if (sorted.isEmpty()) {
            return new RoadVariableCrossSections(List.of(), mode);
        }
        List<Interval> intervals = buildIntervals(sorted, base, loopLength);
        List<Interval> rotated = new ArrayList<>();
        for (Interval interval : intervals) {
            for (double[] piece : CyclicStationTransform.rotateInterval(
                    interval.start, interval.end, shift, loopLength)) {
                rotated.add(new Interval(piece[0], piece[1], interval.template));
            }
        }
        rotated = mergeAdjacentIntervals(rotated);
        RoadVariableCrossSections result = intervalsToVariableCrossSections(rotated, base, mode);
        return result.isValid() ? result : null;
    }

    private static RoadVariableCrossSections rotateSmoothVariableCrossSections(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double shift,
            double loopLength,
            RoadSystemConfig defaults) {
        List<StationCrossSection> sorted = source.sortedStations();
        if (sorted.isEmpty()) {
            return new RoadVariableCrossSections(List.of(), VariableCrossSectionTransitionMode.SMOOTH);
        }
        // Prefer an existing keyframe at the seam (lossless). Otherwise rematerialize and verify.
        RoadCrossSection seamSection = null;
        for (StationCrossSection entry : sorted) {
            if (Math.abs(entry.getStation() - shift) <= EPSILON) {
                seamSection = entry.getCrossSection().copy();
                break;
            }
        }
        if (seamSection == null && Math.abs(shift) <= EPSILON) {
            seamSection = defaultSection != null ? defaultSection.copy() : new RoadCrossSection();
        }
        if (seamSection == null) {
            // Refuse when seam splits a span whose endpoints disagree on discrete attrs —
            // inserting the seam keyframe would move the t=0.5 discrete switch.
            if (smoothSeamWouldMoveDiscreteSwitch(sorted, defaultSection, shift, defaults)) {
                return null;
            }
            seamSection = interpolateSmoothCrossSection(
                source, defaultSection, shift, defaults);
            if (seamSection == null) {
                return null;
            }
        }
        List<StationCrossSection> rotated = new ArrayList<>();
        rotated.add(StationCrossSection.at(0.0, seamSection.copy()));
        // Explicit implicit default@0 when the old profile used it as a SMOOTH start anchor.
        boolean hasZeroKeyframe = sorted.stream().anyMatch(e -> Math.abs(e.getStation()) <= EPSILON);
        if (!hasZeroKeyframe && Math.abs(shift) > EPSILON) {
            double rotatedZero = CyclicStationTransform.rotateByShift(0.0, shift, loopLength);
            if (Double.isFinite(rotatedZero)
                    && Math.abs(rotatedZero) > EPSILON
                    && Math.abs(rotatedZero - loopLength) > EPSILON) {
                rotated.add(StationCrossSection.at(rotatedZero, defaultSection.copy()));
            }
        }
        for (StationCrossSection entry : sorted) {
            if (Math.abs(entry.getStation() - shift) <= EPSILON) {
                continue;
            }
            double station = CyclicStationTransform.rotateByShift(entry.getStation(), shift, loopLength);
            if (!Double.isFinite(station)) {
                return null;
            }
            if (Math.abs(station) <= EPSILON || Math.abs(station - loopLength) <= EPSILON) {
                continue;
            }
            rotated.add(StationCrossSection.at(station, entry.getCrossSection().copy()));
        }
        // Close the loop for SMOOTH: same seam sample at L so last→L lerps to seam.
        rotated.add(StationCrossSection.at(loopLength, seamSection.copy()));
        rotated.sort(Comparator.comparingDouble(StationCrossSection::getStation));
        List<StationCrossSection> deduped = new ArrayList<>();
        for (StationCrossSection entry : rotated) {
            if (!deduped.isEmpty()
                    && Math.abs(deduped.getLast().getStation() - entry.getStation()) <= EPSILON) {
                continue;
            }
            deduped.add(entry);
        }
        RoadVariableCrossSections result =
            new RoadVariableCrossSections(deduped, VariableCrossSectionTransitionMode.SMOOTH);
        if (!result.isValid()) {
            return null;
        }
        // Full display equivalence (widths, lanes, markings, bands, slopes, lamps, …).
        // Mid-span rematerialize that fails displayEquals (incl. integer quantize) → refuse.
        java.util.LinkedHashSet<Double> sampleStations = new java.util.LinkedHashSet<>();
        double step = Math.max(loopLength / 32.0, 0.5);
        for (double s = 0.0; s < loopLength - EPSILON; s += step) {
            sampleStations.add(s);
        }
        for (StationCrossSection entry : sorted) {
            sampleStations.add(entry.getStation());
        }
        sampleStations.add(shift);
        for (double s : sampleStations) {
            if (!(s >= 0.0) || s > loopLength + EPSILON) {
                continue;
            }
            double clamped = Math.min(s, loopLength);
            ResolvedCrossSection before = VariableCrossSectionResolver.resolve(
                defaultSection, sorted, VariableCrossSectionTransitionMode.SMOOTH, clamped, defaults);
            double afterStation = CyclicStationTransform.rotateByShift(clamped, shift, loopLength);
            if (!Double.isFinite(afterStation)) {
                return null;
            }
            ResolvedCrossSection after = VariableCrossSectionResolver.resolve(
                defaultSection, result.sortedStations(), VariableCrossSectionTransitionMode.SMOOTH,
                afterStation, defaults);
            if (!before.displayEquals(after)) {
                return null;
            }
        }
        return result;
    }

    /**
     * True when {@code shift} lies strictly inside a SMOOTH span whose endpoint templates
     * resolve to different discrete attrs (lane count / materials / markings / bands).
     */
    private static boolean smoothSeamWouldMoveDiscreteSwitch(
            List<StationCrossSection> sorted,
            RoadCrossSection defaultSection,
            double shift,
            RoadSystemConfig defaults) {
        RoadCrossSection base = defaultSection != null ? defaultSection : new RoadCrossSection();
        List<StationCrossSection> anchors = new ArrayList<>();
        boolean hasZero = sorted.stream().anyMatch(e -> Math.abs(e.getStation()) <= EPSILON);
        if (!hasZero) {
            anchors.add(StationCrossSection.at(0.0, base));
        }
        anchors.addAll(sorted);
        for (int i = 0; i < anchors.size() - 1; i++) {
            StationCrossSection left = anchors.get(i);
            StationCrossSection right = anchors.get(i + 1);
            if (shift <= left.getStation() + EPSILON || shift >= right.getStation() - EPSILON) {
                continue;
            }
            ResolvedCrossSection a = left.getCrossSection().resolve(defaults);
            ResolvedCrossSection b = right.getCrossSection().resolve(defaults);
            if (!smoothDiscreteEquals(a, b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean smoothDiscreteEquals(ResolvedCrossSection a, ResolvedCrossSection b) {
        return a.laneCount == b.laneCount
            && a.includeMedian == b.includeMedian
            && a.laneDividers == b.laneDividers
            && a.centerLineStyle == b.centerLineStyle
            && a.centerLineSolid == b.centerLineSolid
            && a.includeShoulder == b.includeShoulder
            && a.includeBikeLane == b.includeBikeLane
            && a.includeSidewalk == b.includeSidewalk
            && a.includeSlopeBatter == b.includeSlopeBatter
            && a.includeRetainingWall == b.includeRetainingWall
            && materialMixEquals(a.carriagewayMaterial, b.carriagewayMaterial)
            && java.util.Objects.equals(a.medianMaterial, b.medianMaterial)
            && java.util.Objects.equals(a.markingMaterial, b.markingMaterial)
            && java.util.Objects.equals(a.shoulderMaterial, b.shoulderMaterial)
            && java.util.Objects.equals(a.bikeLaneMaterial, b.bikeLaneMaterial)
            && java.util.Objects.equals(a.sidewalkMaterial, b.sidewalkMaterial)
            && java.util.Objects.equals(a.fillSlopeMaterial, b.fillSlopeMaterial)
            && java.util.Objects.equals(a.cutSlopeMaterial, b.cutSlopeMaterial)
            && java.util.Objects.equals(a.retainingWallMaterial, b.retainingWallMaterial);
    }

    private static boolean materialMixEquals(
            com.plot.core.material.MaterialMix left,
            com.plot.core.material.MaterialMix right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return java.util.Objects.equals(left.getPrimaryMaterial(), right.getPrimaryMaterial())
            && java.util.Objects.equals(left.getAccentMaterial(), right.getAccentMaterial())
            && Float.compare(left.getAccentRatio(), right.getAccentRatio()) == 0;
    }

    /**
     * True SMOOTH sample at {@code station}: continuous lerp + discrete nearer-side, materialized
     * as a {@link RoadCrossSection} keyframe. Never copies a flanking keyframe wholesale.
     */
    static RoadCrossSection interpolateSmoothCrossSection(
            RoadVariableCrossSections source,
            RoadCrossSection defaultSection,
            double station,
            RoadSystemConfig defaults) {
        if (source == null) {
            return defaultSection != null ? defaultSection.copy() : new RoadCrossSection();
        }
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("plot");
        RoadCrossSection base = defaultSection != null ? defaultSection : new RoadCrossSection();
        List<StationCrossSection> sorted = source.sortedStations();
        ResolvedCrossSection resolved = VariableCrossSectionResolver.resolve(
            base, sorted, VariableCrossSectionTransitionMode.SMOOTH, station, config);
        RoadCrossSection discreteTemplate = nearestKeyframeTemplate(sorted, base, station);
        return materializeResolvedAsCrossSection(resolved, discreteTemplate, config);
    }

    private static RoadCrossSection nearestKeyframeTemplate(
            List<StationCrossSection> sorted,
            RoadCrossSection defaultSection,
            double station) {
        if (sorted == null || sorted.isEmpty()) {
            return defaultSection.copy();
        }
        StationCrossSection prev = null;
        StationCrossSection next = null;
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() <= station + EPSILON) {
                prev = entry;
            } else {
                next = entry;
                break;
            }
        }
        if (prev == null) {
            // Before first keyframe: implicit default@0 is the from-side (t from 0).
            double first = sorted.getFirst().getStation();
            double t = first > EPSILON ? station / first : 0.0;
            return t < 0.5 ? defaultSection.copy() : sorted.getFirst().getCrossSection().copy();
        }
        if (next == null) {
            return prev.getCrossSection().copy();
        }
        double span = next.getStation() - prev.getStation();
        double t = span > EPSILON ? (station - prev.getStation()) / span : 0.0;
        return (t < 0.5 ? prev : next).getCrossSection().copy();
    }

    private static RoadCrossSection materializeResolvedAsCrossSection(
            ResolvedCrossSection resolved,
            RoadCrossSection discreteTemplate,
            RoadSystemConfig defaults) {
        CrossSectionDraft draft = CrossSectionDraft.fromCrossSection(discreteTemplate, defaults);
        draft.setLaneCount(Math.max(1, resolved.laneCount));
        draft.setWidth(Math.max(1, resolved.carriagewayWidth));
        if (resolved.laneWidths != null) {
            for (int i = 0; i < resolved.laneWidths.size(); i++) {
                Integer w = resolved.laneWidths.get(i);
                if (w != null && w > 0) {
                    draft.setLaneWidthAt(i, w);
                }
            }
        }
        draft.setIncludeShoulder(resolved.includeShoulder);
        draft.setShoulderWidth(Math.max(0, resolved.shoulderWidth));
        draft.setIncludeSidewalk(resolved.includeSidewalk);
        draft.setSidewalkWidth(Math.max(0, resolved.sidewalkWidth));
        draft.setIncludeBikeLane(resolved.includeBikeLane);
        draft.setBikeLaneWidth(Math.max(0, resolved.bikeLaneWidth));
        draft.setIncludeMedian(resolved.includeMedian);
        draft.setMedianWidth(Math.max(0, resolved.medianWidth));
        draft.setMedianHeight(Math.max(0, resolved.medianHeight));
        draft.setFillSlopeRatio(resolved.fillSlopeRatio);
        draft.setCutSlopeRatio(resolved.cutSlopeRatio);
        if (resolved.streetlightSpacing != null) {
            draft.setStreetlightSpacing(resolved.streetlightSpacing);
        }
        return draft.toCrossSection();
    }

    static RoadStationFacilities rotateStationFacilitiesCyclic(
            RoadStationFacilities source,
            double shift,
            double loopLength) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        RoadStationFacilities facilities = new RoadStationFacilities();
        for (StationFacilityRun run : source.getRuns()) {
            double start = run.getStartStation();
            double end = run.getEndStation() != null ? run.getEndStation() : loopLength;
            if (!(end > start + EPSILON)) {
                return null;
            }
            List<double[]> pieces = CyclicStationTransform.rotateInterval(start, end, shift, loopLength);
            if (pieces.isEmpty()) {
                return null;
            }
            for (double[] piece : pieces) {
                Double endStation = Math.abs(piece[1] - loopLength) <= EPSILON ? null : piece[1];
                // Open-ended original that covered to L stays open if piece ends at L after rotate.
                if (run.getEndStation() == null && Math.abs(end - loopLength) <= EPSILON
                        && Math.abs(piece[1] - loopLength) <= EPSILON) {
                    endStation = null;
                }
                if (endStation != null && endStation <= piece[0] + EPSILON) {
                    return null;
                }
                facilities.addRun(new StationFacilityRun(
                    piece[0],
                    endStation,
                    run.getKind(),
                    run.getSide(),
                    run.getMaterial(),
                    run.getHeight()));
            }
        }
        return facilities.isEmpty() ? null : facilities;
    }
}
