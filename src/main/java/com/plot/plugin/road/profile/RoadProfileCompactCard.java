package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;

import java.util.List;

/**
 * 紧凑纵断面卡片：约 80–88px 图表；图例由调用方决定是否绘制（侧栏概览共用一组）。
 */
public final class RoadProfileCompactCard {

    public static final float CHART_HEIGHT = 80f;
    public static final float ACTIVE_CHART_HEIGHT = 88f;
    public static final float INLINE_CHART_HEIGHT = 88f;

    private RoadProfileCompactCard() {
    }

    /** 仅绘制缩略图，不含图例。 */
    public static void renderOverviewChart(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode) {
        if (chart == null || !chart.canRenderChart()) {
            return;
        }
        RoadProfileChartRenderer.renderOverview(
            chart,
            design,
            intersections,
            chartHeight,
            flatOverlay,
            chartMode,
            verticalMode);
    }

    /**
     * @param showLegend true 时在图下画紧凑图例；侧栏多卡列表应传 false，改由外层画一组共享图例
     */
    public static void renderOverview(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale,
            boolean showLegend) {
        renderOverviewChart(
            chart, design, intersections, chartHeight, flatOverlay, chartMode, verticalMode);
        if (showLegend) {
            ProfileChartLegend.renderCompact(
                chartMode,
                verticalMode,
                flatMode,
                buildPreviewStale,
                chart);
        }
    }
}
