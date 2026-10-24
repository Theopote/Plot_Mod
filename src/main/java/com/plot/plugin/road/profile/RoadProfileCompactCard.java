package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;

import java.util.List;

/**
 * 紧凑纵断面卡片：约 80–88px 图表 + 单行精简图例。
 * <p>
 * Overview / Editor：地形 + 建造（预览拟合）；Editor 另叠加设计线。
 */
public final class RoadProfileCompactCard {

    public static final float CHART_HEIGHT = 80f;
    public static final float ACTIVE_CHART_HEIGHT = 88f;
    public static final float INLINE_CHART_HEIGHT = 88f;

    private RoadProfileCompactCard() {
    }

    public static void renderOverview(
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay,
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale) {
        renderOverview(
            chart,
            design,
            intersections,
            chartHeight,
            flatOverlay,
            chartMode,
            verticalMode,
            flatMode,
            buildPreviewStale,
            false);
    }

    /**
     * @param workspaceLegend true 时图例包含设计线（内联工作区）；false 为列表卡片的地形+建造图例
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
            boolean workspaceLegend) {
        if (chart == null || !chart.hasProfileData()) {
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
        if (workspaceLegend) {
            ProfileChartLegend.renderCompact(
                ProfileChartRenderMode.EDITOR,
                verticalMode,
                flatMode,
                buildPreviewStale,
                chart);
        } else {
            ProfileChartLegend.renderCompact(
                chartMode,
                verticalMode,
                flatMode,
                buildPreviewStale,
                chart);
        }
    }
}
