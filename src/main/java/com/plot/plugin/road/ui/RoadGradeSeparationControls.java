package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.AutoGradeSeparationRecommendation;
import com.plot.plugin.road.AutoGradeSeparationRecommendationCache;
import com.plot.plugin.road.RoadEdgeListHelper;
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

        changed |= renderCrossingType(node, network, config, roadIds, layout);
        if (node.isGradeSeparated()) {
            changed |= renderPassMode(node, network, config, roadIds, layout);
            changed |= renderClearanceSlider(node, config, layout == Layout.INLINE);
            renderRecommendation(node, network, config);
        }

        if (node.getManualElevation() != null && node.isGradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.grade_separation_manual_override"));
        }
        ImGui.popID();
        if (changed) {
            ctx.previewManager().invalidatePreview();
            ctx.requestOverlayRefresh();
        }
        return changed;
    }

    private boolean renderCrossingType(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            Layout layout) {
        if (layout != Layout.INLINE) {
            ImGui.text(PlotI18n.tr("plugin.road.crossing_type"));
        }
        boolean atGrade = !node.isGradeSeparated();
        if (ImGui.radioButton(PlotI18n.tr("plugin.road.crossing_type_at_grade") + "##at_grade", atGrade)) {
            if (!atGrade) {
                applyAtGrade(node, network);
                return true;
            }
        }
        ImGui.sameLine();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.crossing_type_grade_separated") + "##grade_sep", !atGrade)) {
            if (atGrade) {
                applyGradeSeparatedAuto(node, network, config);
                return true;
            }
        }
        return false;
    }

    private boolean renderPassMode(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            Layout layout) {
        String[] labels = buildPassModeLabels(roadIds, network);
        int currentIndex = passModeIndex(node, roadIds);
        if (layout == Layout.INLINE) {
            ImGui.sameLine();
        }
        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.crossing_pass_mode") + "##pass_mode", index, labels)) {
            applyPassModeSelection(node, network, config, roadIds, index.get());
            return true;
        }
        return false;
    }

    private void renderRecommendation(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
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

        if (recommendation.hasRecommendation()) {
            String label = formatRoadLabel(network, recommendation.elevatedRoadId());
            if (recommendation.evaluation() != null && recommendation.evaluation().terrainAnalyzed()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.STATUS_INFO,
                    PlotI18n.tr("plugin.road.crossing_recommendation_analyzed", label));
            } else {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.crossing_recommendation_provisional", label));
            }
        } else if (node.getElevatedRoadId() == null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.crossing_recommendation_pending"));
        }

        if (recommendation.warnsLockedChoice(node)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.crossing_warning_steep"));
        }
        String adoptId = recommendation.adoptSuggestedElevatedRoadId(node);
        if (adoptId != null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.crossing_recommendation_better",
                    formatRoadLabel(network, adoptId)));
            if (ImGui.button(PlotI18n.tr("plugin.road.crossing_adopt_recommendation") + "##adopt_rec")) {
                applyLockedElevatedRoad(node, network, config, adoptId);
            }
        }
    }

    private String[] buildPassModeLabels(List<String> roadIds, RoadNetwork network) {
        String[] labels = new String[roadIds.size() + 1];
        labels[0] = PlotI18n.tr("plugin.road.crossing_pass_auto");
        for (int i = 0; i < roadIds.size(); i++) {
            labels[i + 1] = PlotI18n.tr(
                "plugin.road.crossing_pass_locked_above",
                formatRoadLabel(network, roadIds.get(i)));
        }
        return labels;
    }

    private static int passModeIndex(RoadNode node, List<String> roadIds) {
        if (!node.isGradeSeparated() || node.getElevatedRoadId() == null) {
            return 0;
        }
        int roadIndex = roadIds.indexOf(node.getElevatedRoadId());
        return roadIndex >= 0 ? roadIndex + 1 : 0;
    }

    private void applyAtGrade(RoadNode node, RoadNetwork network) {
        ctx.networkManager().pushHistory();
        network.setNodeGradeSeparation(node.getId(), false, null, null);
    }

    private void applyGradeSeparatedAuto(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        ctx.networkManager().pushHistory();
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        network.setNodeGradeSeparation(node.getId(), true, null, clearance);
    }

    private void applyPassModeSelection(
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
            network.setNodeGradeSeparation(node.getId(), true, null, clearance);
        } else {
            network.setNodeGradeSeparation(node.getId(), true, roadIds.get(index - 1), clearance);
        }
    }

    private void applyLockedElevatedRoad(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String elevatedRoadId) {
        ctx.networkManager().pushHistory();
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        network.setNodeGradeSeparation(node.getId(), true, elevatedRoadId, clearance);
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

    private String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road != null) {
            return RoadEdgeListHelper.formatRoadLabel(network, road);
        }
        String shortId = roadId.length() > 6 ? roadId.substring(0, 6) : roadId;
        return PlotI18n.tr("plugin.road.road_label_fallback", shortId);
    }
}
