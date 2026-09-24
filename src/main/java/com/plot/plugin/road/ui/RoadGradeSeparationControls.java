package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.AutoGradeSeparationRecommendation;
import com.plot.plugin.road.AutoGradeSeparationRecommendationCache;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/** 节点立体交叉 / 高程关系编辑控件（路径 Tab、纵剖面交叉标注共用）。 */
public final class RoadGradeSeparationControls {

    public enum Layout {
        BLOCK,
        INLINE,
        PROFILE
    }

    private final RoadUiContext ctx;
    private final AutoGradeSeparationRecommendationCache autoGradeSeparationCache =
        new AutoGradeSeparationRecommendationCache();

    public RoadGradeSeparationControls(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    /**
     * @return true if grade separation or clearance changed
     */
    public boolean render(RoadNode node, RoadNetwork network, RoadSystemConfig config, Layout layout) {
        if (node == null || network == null || config == null) {
            return false;
        }
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            if (layout == Layout.PROFILE) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.path.complex_grade_separation_hint"));
            }
            return false;
        }
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return false;
        }

        ImGui.pushID(node.getId());
        boolean changed = false;
        if (layout == Layout.PROFILE) {
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_grade_edit"));
        }

        String[] labels = buildGradeSeparationLabels(roadIds, network);
        int currentIndex = gradeSeparationIndex(node, roadIds);
        if (layout == Layout.INLINE) {
            ImGui.sameLine();
        }
        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.grade_separation") + "##grade_sep", index, labels)) {
            applyGradeSeparationSelection(node, network, config, roadIds, index.get());
            changed = true;
        }

        if (node.isGradeSeparated()) {
            changed |= renderClearanceSlider(node, config, layout == Layout.INLINE);
            if (node.getElevatedRoadId() == null) {
                renderAutoElevatedRoadHint(node, network, config);
            }
        }

        if (node.getManualElevation() != null && node.isGradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.grade_separation_manual_override"));
        }
        ImGui.popID();
        if (changed) {
            ctx.requestOverlayRefresh();
        }
        return changed;
    }

    private String[] buildGradeSeparationLabels(List<String> roadIds, RoadNetwork network) {
        String[] labels = new String[roadIds.size() + 2];
        labels[0] = PlotI18n.tr("plugin.road.grade_separation_none");
        labels[1] = PlotI18n.tr("plugin.road.grade_separation_auto");
        for (int i = 0; i < roadIds.size(); i++) {
            labels[i + 2] = formatRoadLabel(network, roadIds.get(i));
        }
        return labels;
    }

    private static int gradeSeparationIndex(RoadNode node, List<String> roadIds) {
        if (!node.isGradeSeparated()) {
            return 0;
        }
        if (node.getElevatedRoadId() == null) {
            return 1;
        }
        int roadIndex = roadIds.indexOf(node.getElevatedRoadId());
        return roadIndex >= 0 ? roadIndex + 2 : 0;
    }

    private void applyGradeSeparationSelection(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            int index) {
        ctx.networkManager().pushHistory();
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        if (index == 0) {
            network.setNodeGradeSeparation(node.getId(), false, null, null);
        } else if (index == 1) {
            network.setNodeGradeSeparation(node.getId(), true, null, clearance);
        } else {
            String selectedRoadId = roadIds.get(index - 2);
            network.setNodeGradeSeparation(node.getId(), true, selectedRoadId, clearance);
        }
    }

    private boolean renderClearanceSlider(RoadNode node, RoadSystemConfig config, boolean inline) {
        double currentClearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        int[] clearance = {(int) Math.round(currentClearance)};
        if (inline) {
            ImGui.sameLine();
        }
        boolean clearanceChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.crossing_clearance") + "##clearance",
            clearance,
            RoadParameterLimits.MIN_CROSSING_CLEARANCE,
            RoadParameterLimits.MAX_CROSSING_CLEARANCE,
            "%d");
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
        }
        if (clearanceChanged) {
            node.setCrossingClearance((double) clearance[0]);
        }
        return clearanceChanged;
    }

    private void renderAutoElevatedRoadHint(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        AutoGradeSeparationRecommendation recommendation = autoGradeSeparationCache.resolve(
            node,
            network,
            config,
            ctx.host(),
            ctx.networkManager().getNetworkRevision(),
            config.generationInputsFingerprint(),
            AutoGradeSeparationRecommendationCache.worldVersion(
                RoadNetworkGenerator.getClientWorld(),
                ctx.previewManager().getTerrainRevision()));
        if (!recommendation.hasRecommendation()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.grade_separation_auto_result",
                formatRoadLabel(network, recommendation.elevatedRoadId())));
    }

    private static String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road != null && road.getName() != null && !road.getName().isBlank()) {
            return road.getName();
        }
        String shortId = roadId.length() > 6 ? roadId.substring(0, 6) : roadId;
        return PlotI18n.tr("plugin.road.road_label_fallback", shortId);
    }
}
