package com.plot.plugin.road.station;

/**
 * 中心线/道路几何编辑操作及其默认沿桩号策略。
 * <p>
 * 见 {@link CenterlineEditStationPolicy}；HA 同步策略见
 * {@link com.plot.plugin.road.centerline.CenterlinePhase2ConsistencyPolicy}。
 */
public enum CenterlineEditOperation {

    /** 折线插点；共线时链长不变 → {@link CenterlineEditStationPolicy#PRESERVE_STATION}。 */
    INSERT_PI(CenterlineEditStationPolicy.PRESERVE_STATION),

    /** 圆角；段长变化 → {@link CenterlineEditStationPolicy#REPARAMETERIZE_STATION}（段内比例，非绝对 chainage）。 */
    FILLET(CenterlineEditStationPolicy.REPARAMETERIZE_STATION),

    /** 图 split：Road 未拆，总链长不变 → {@link CenterlineEditStationPolicy#PRESERVE_STATION}。 */
    SPLIT_EDGE(CenterlineEditStationPolicy.PRESERVE_STATION),

    /** 图 merge：总链长不变 → {@link CenterlineEditStationPolicy#PRESERVE_STATION}。 */
    MERGE_EDGE(CenterlineEditStationPolicy.PRESERVE_STATION),

    /** {@code splitRoadBeforeSegment} → {@link CenterlineEditStationPolicy#PARTITION_AND_RESET_TAIL}。 */
    SPLIT_ROAD(CenterlineEditStationPolicy.PARTITION_AND_RESET_TAIL),

    /** {@code mergeRoadTailIntoHead} → {@link CenterlineEditStationPolicy#OFFSET_BY_HEAD_LENGTH}。 */
    MERGE_ROAD(CenterlineEditStationPolicy.OFFSET_BY_HEAD_LENGTH),

    /** 单段反向 → {@link CenterlineEditStationPolicy#MIRROR_IN_RANGE}。 */
    REVERSE_EDGE(CenterlineEditStationPolicy.MIRROR_IN_RANGE),

    /** 整路反向 → {@link CenterlineEditStationPolicy#MIRROR_FULL_ROAD}。 */
    REVERSE_ROAD(CenterlineEditStationPolicy.MIRROR_FULL_ROAD);

    private final CenterlineEditStationPolicy defaultStationPolicy;

    CenterlineEditOperation(CenterlineEditStationPolicy defaultStationPolicy) {
        this.defaultStationPolicy = defaultStationPolicy;
    }

    public CenterlineEditStationPolicy defaultStationPolicy() {
        return defaultStationPolicy;
    }

    /**
     * Insert PI 实际策略随段长是否变化而定。
     */
    public CenterlineEditStationPolicy resolveStationPolicy(double oldSegmentLength, double newSegmentLength) {
        if (this == INSERT_PI) {
            return CenterlineEditStationPolicy.forSegmentGeometryEdit(oldSegmentLength, newSegmentLength);
        }
        return defaultStationPolicy;
    }
}
