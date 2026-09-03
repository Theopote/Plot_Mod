package com.plot.plugin.earthwork.design;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 分区设计面的运行时解析结果：不只是 {@code evaluateAt}，还携带来源、置信状态与可调性。
 * <p>
 * 管线位置：{@code GradingZone → DesignSurfaceResolver → ResolvedDesignSurface → DesignTerrainGrid}。
 */
public final class ResolvedDesignSurface implements DesignSurfaceResolver.ZoneTargetEvaluator {

    private final String zoneId;
    private final ResolvedDesignSource source;
    private final ResolutionResult.Status status;
    private final VerticalAdjustmentPolicy verticalPolicy;
    private final DesignSurfaceResolver.ZoneTargetEvaluator evaluator;
    private final String detail;

    public ResolvedDesignSurface(
            String zoneId,
            ResolvedDesignSource source,
            ResolutionResult.Status status,
            VerticalAdjustmentPolicy verticalPolicy,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator,
            String detail) {
        this.zoneId = zoneId != null ? zoneId : "";
        this.source = source != null ? source : ResolvedDesignSource.UNKNOWN;
        this.status = status != null ? status : ResolutionResult.Status.FALLBACK;
        this.verticalPolicy = verticalPolicy != null ? verticalPolicy.copy() : VerticalAdjustmentPolicy.locked();
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.detail = detail != null ? detail : "";
    }

    public static ResolvedDesignSurface of(
            String zoneId,
            ResolvedDesignSource source,
            ResolutionResult.Status status,
            VerticalAdjustmentPolicy verticalPolicy,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
        return new ResolvedDesignSurface(zoneId, source, status, verticalPolicy, evaluator, "");
    }

    public String zoneId() {
        return zoneId;
    }

    public ResolvedDesignSource source() {
        return source;
    }

    public ResolutionResult.Status status() {
        return status;
    }

    public VerticalAdjustmentPolicy verticalPolicy() {
        return verticalPolicy;
    }

    public String detail() {
        return detail;
    }

    public DesignSurfaceResolver.ZoneTargetEvaluator evaluator() {
        return evaluator;
    }

    @Override
    public int evaluateAt(DesignTerrainCell cell) {
        return evaluator.evaluateAt(cell);
    }

    public boolean allowsVerticalAdjustment() {
        return verticalPolicy.allowsVerticalAdjustment();
    }

    public boolean isElevationLocked() {
        return !allowsVerticalAdjustment();
    }

    /**
     * 是否可作为 Mode B Solver 变量：可调且解析状态可信（RESOLVED / 有意 FALLBACK）。
     * 引用失败（MISSING / INVALID）不得进入优化变量集。
     */
    public boolean isSolverVariable() {
        if (!allowsVerticalAdjustment()) {
            return false;
        }
        return status == ResolutionResult.Status.RESOLVED
            || status == ResolutionResult.Status.FALLBACK;
    }

    public ResolvedDesignSurface withVerticalOffset(int delta) {
        if (delta == 0) {
            return this;
        }
        return new ResolvedDesignSurface(
            zoneId,
            source,
            status,
            verticalPolicy,
            cell -> evaluator.evaluateAt(cell) + delta,
            detail);
    }

    public static Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> toEvaluatorMap(
            Map<String, ResolvedDesignSurface> resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return Map.of();
        }
        Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> map = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedDesignSurface> entry : resolved.entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
