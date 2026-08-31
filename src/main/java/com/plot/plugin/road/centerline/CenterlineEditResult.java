package com.plot.plugin.road.centerline;

/**
 * 中心线编辑操作结果。
 */
public record CenterlineEditResult(
        CenterlineEditStatus status,
        String detailMessageKey,
        String firstEdgeId,
        String secondEdgeId,
        String mergedEdgeId,
        String nodeId) {

    public static CenterlineEditResult success() {
        return new CenterlineEditResult(CenterlineEditStatus.SUCCESS, null, null, null, null, null);
    }

    public static CenterlineEditResult failure(CenterlineEditStatus status) {
        return new CenterlineEditResult(status, null, null, null, null, null);
    }

    public static CenterlineEditResult split(String firstEdgeId, String secondEdgeId, String nodeId) {
        return new CenterlineEditResult(
            CenterlineEditStatus.SUCCESS,
            null,
            firstEdgeId,
            secondEdgeId,
            null,
            nodeId
        );
    }

    public static CenterlineEditResult merged(String mergedEdgeId) {
        return new CenterlineEditResult(
            CenterlineEditStatus.SUCCESS,
            null,
            null,
            null,
            mergedEdgeId,
            null
        );
    }

    public boolean isSuccess() {
        return status == CenterlineEditStatus.SUCCESS;
    }
}
