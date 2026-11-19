package com.plot.plugin.road.pipeline.corridor;

/**
 * Developer-facing junction construction diagnostics (not shown in player UI).
 * <p>
 * 过程诊断字段（隧道连通等）与终态验收字段（postMerge*）分轨合并：
 * 前者可 {@code Math.max} 保留历史峰值；后者在 {@link #finalAcceptanceApplied()} 为真时整组覆盖，
 * 避免早期断开计数在修复后仍误拦建造。
 */
public record JunctionConstructionReport(
        String nodeId,
        int expectedSurfaceColumns,
        int missingSurfaceColumns,
        int blockedTunnelAirBlocks,
        int protectedStructureConflicts,
        int unresolvedClearanceBlocks,
        boolean tunnelConnectivityApplied,
        /** 连通清障阶段的断开计数（过程诊断，不进建造门禁）。 */
        int processDisconnectedTunnelComponents,
        int postMergeMissingSurfaceColumns,
        int postMergeSurfaceSemanticMismatches,
        int postMergeProtectedLosses,
        /** 终态验收的隧道连通断开计数（建造门禁）。 */
        int disconnectedTunnelComponents,
        int crossLayerLeaks,
        /** 期望路面 Y 缺失但同列另有路面：merge/Y 错位镂空。 */
        int postMergeSurfaceYMismatches,
        /** 设计路面以上未授权实体（落顶衬砌/悬空残留等）。 */
        int postMergeUndesignedProtrusions,
        /** 是否已写入合并后终态验收（为真时 postMerge 字段覆盖而非 max）。 */
        boolean finalAcceptanceApplied) {

    public static JunctionConstructionReport empty(String nodeId) {
        return new JunctionConstructionReport(
            nodeId, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    /** 缺路面 / 保护破坏 / 隧道连通断裂 → 阻断建造（仅终态字段）。 */
    public boolean hasBlockingPostMergeIssues() {
        return postMergeMissingSurfaceColumns > 0
            || postMergeProtectedLosses > 0
            || disconnectedTunnelComponents > 0;
    }

    /** 语义偏差等轻微项 → 仅警告，不单独阻断。 */
    public boolean hasAdvisoryPostMergeIssues() {
        return postMergeSurfaceSemanticMismatches > 0
            || postMergeSurfaceYMismatches > 0
            || postMergeUndesignedProtrusions > 0
            || crossLayerLeaks > 0;
    }

    public JunctionConstructionReport withTunnelConnectivity(
            int blockedTunnelAirBlocks,
            int protectedStructureConflicts,
            boolean tunnelConnectivityApplied,
            int processDisconnectedTunnelComponents) {
        return new JunctionConstructionReport(
            nodeId,
            expectedSurfaceColumns,
            missingSurfaceColumns,
            blockedTunnelAirBlocks,
            protectedStructureConflicts,
            unresolvedClearanceBlocks,
            tunnelConnectivityApplied,
            processDisconnectedTunnelComponents,
            postMergeMissingSurfaceColumns,
            postMergeSurfaceSemanticMismatches,
            postMergeProtectedLosses,
            disconnectedTunnelComponents,
            crossLayerLeaks,
            postMergeSurfaceYMismatches,
            postMergeUndesignedProtrusions,
            finalAcceptanceApplied);
    }

    public JunctionConstructionReport withPostMerge(
            int postMergeMissingSurfaceColumns,
            int postMergeSurfaceSemanticMismatches,
            int postMergeProtectedLosses,
            int disconnectedTunnelComponents,
            int crossLayerLeaks,
            int postMergeSurfaceYMismatches,
            int postMergeUndesignedProtrusions) {
        return new JunctionConstructionReport(
            nodeId,
            expectedSurfaceColumns,
            missingSurfaceColumns,
            blockedTunnelAirBlocks,
            protectedStructureConflicts,
            unresolvedClearanceBlocks,
            tunnelConnectivityApplied,
            processDisconnectedTunnelComponents,
            postMergeMissingSurfaceColumns,
            postMergeSurfaceSemanticMismatches,
            postMergeProtectedLosses,
            disconnectedTunnelComponents,
            crossLayerLeaks,
            postMergeSurfaceYMismatches,
            postMergeUndesignedProtrusions,
            true);
    }

    public static JunctionConstructionReport merge(
            JunctionConstructionReport base,
            JunctionConstructionReport overlay) {
        if (base == null) {
            return overlay != null ? overlay : empty("");
        }
        if (overlay == null) {
            return base;
        }
        String nodeId = base.nodeId() != null && !base.nodeId().isBlank()
            ? base.nodeId()
            : overlay.nodeId();
        boolean finalApplied = base.finalAcceptanceApplied() || overlay.finalAcceptanceApplied();
        JunctionConstructionReport finalSource = overlay.finalAcceptanceApplied()
            ? overlay
            : base;
        return new JunctionConstructionReport(
            nodeId,
            Math.max(base.expectedSurfaceColumns(), overlay.expectedSurfaceColumns()),
            Math.max(base.missingSurfaceColumns(), overlay.missingSurfaceColumns()),
            Math.max(base.blockedTunnelAirBlocks(), overlay.blockedTunnelAirBlocks()),
            Math.max(base.protectedStructureConflicts(), overlay.protectedStructureConflicts()),
            Math.max(base.unresolvedClearanceBlocks(), overlay.unresolvedClearanceBlocks()),
            base.tunnelConnectivityApplied() || overlay.tunnelConnectivityApplied(),
            Math.max(
                base.processDisconnectedTunnelComponents(),
                overlay.processDisconnectedTunnelComponents()),
            finalSource.postMergeMissingSurfaceColumns(),
            finalSource.postMergeSurfaceSemanticMismatches(),
            finalSource.postMergeProtectedLosses(),
            finalSource.disconnectedTunnelComponents(),
            finalSource.crossLayerLeaks(),
            finalSource.postMergeSurfaceYMismatches(),
            finalSource.postMergeUndesignedProtrusions(),
            finalApplied);
    }
}
