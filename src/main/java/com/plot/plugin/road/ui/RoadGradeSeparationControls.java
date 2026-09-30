package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.AutoGradeSeparationRecommendation;
import com.plot.plugin.road.AutoGradeSeparationRecommendationCache;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/** 立体交叉 / 高程关系编辑（注册表 Crossing 为主，拓扑节点为兼容入口）。 */
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
     * 编辑 {@link RoadCrossing} 注册表中的交叉关系。
     *
     * @return true if grade separation or clearance changed
     */
    public boolean renderCrossing(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            Layout layout) {
        if (crossing == null || network == null || config == null) {
            return false;
        }
        List<String> roadIds = List.of(crossing.roadAId(), crossing.roadBId());

        ImGui.pushID(crossing.id());
        boolean changed = false;
        if (layout == Layout.PROFILE) {
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_grade_edit"));
        }

        changed |= renderCrossingTypeForRegistry(crossing, network, config, layout);
        if (crossing.type() == CrossingType.GRADE_SEPARATED) {
            changed |= renderPassModeForRegistry(crossing, network, config, roadIds, layout);
            changed |= renderClearanceSliderForRegistry(crossing, network, config, layout == Layout.INLINE);
            renderRecommendationForRegistry(crossing, network, config);
        }

        if (crossing.sharedElevation() != null && crossing.type() == CrossingType.GRADE_SEPARATED) {
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

    /**
     * 显式拓扑节点（旧 junction / Connect）上的立交编辑。
     */
    public boolean renderLegacyJunction(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            Layout layout) {
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

        changed |= renderCrossingTypeForNode(node, network, config, layout);
        if (node.isGradeSeparated()) {
            changed |= renderPassModeForNode(node, network, config, roadIds, layout);
            changed |= renderClearanceSliderForNode(node, config, layout == Layout.INLINE);
            renderRecommendationForNode(node, network, config);
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

    /** @deprecated 使用 {@link #renderCrossing} 或 {@link #renderLegacyJunction} */
    @Deprecated
    public boolean render(RoadNode node, RoadNetwork network, RoadSystemConfig config, Layout layout) {
        return renderLegacyJunction(node, network, config, layout);
    }

    private boolean renderCrossingTypeForRegistry(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            Layout layout) {
        if (layout != Layout.INLINE) {
            ImGui.text(PlotI18n.tr("plugin.road.crossing_type"));
        }
        boolean atGrade = crossing.type() == CrossingType.AT_GRADE;
        if (ImGui.radioButton(PlotI18n.tr("plugin.road.crossing_type_at_grade") + "##at_grade", atGrade)) {
            if (!atGrade) {
                applyAtGrade(crossing, network);
                return true;
            }
        }
        ImGui.sameLine();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.road.crossing_type_grade_separated") + "##grade_sep", !atGrade)) {
            if (atGrade) {
                applyGradeSeparatedAuto(crossing, network, config);
                return true;
            }
        }
        return false;
    }

    private boolean renderCrossingTypeForNode(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
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

    private boolean renderPassModeForRegistry(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            Layout layout) {
        String[] labels = buildPassModeLabels(roadIds, network);
        int currentIndex = passModeIndex(crossing.elevatedRoadId(), roadIds);
        if (layout == Layout.INLINE) {
            ImGui.sameLine();
        }
        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.crossing_pass_mode") + "##pass_mode", index, labels)) {
            applyPassModeSelection(crossing, network, config, roadIds, index.get());
            return true;
        }
        return false;
    }

    private boolean renderPassModeForNode(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            Layout layout) {
        String[] labels = buildPassModeLabels(roadIds, network);
        int currentIndex = passModeIndex(node.getElevatedRoadId(), roadIds);
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

    private void renderRecommendationForRegistry(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config) {
        AutoGradeSeparationRecommendation recommendation = autoGradeSeparationCache.resolveForCrossing(
            crossing,
            network,
            config,
            ctx.host(),
            ctx.networkManager().getNetworkRevision(),
            config.generationInputsFingerprint(),
            AutoGradeSeparationRecommendationCache.worldVersion(
                RoadNetworkGenerator.getClientWorld(),
                ctx.previewManager().getTerrainRevision()));
        renderRecommendationBody(recommendation, crossing.elevatedRoadId(), network, () ->
            applyLockedElevatedRoad(crossing, network, config, recommendation.adoptSuggestedElevatedRoadId(
                crossing.elevatedRoadId())));
    }

    private void renderRecommendationForNode(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
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
        renderRecommendationBody(recommendation, node.getElevatedRoadId(), network, () ->
            applyLockedElevatedRoad(node, network, config, recommendation.adoptSuggestedElevatedRoadId(node)));
    }

    private void renderRecommendationBody(
            AutoGradeSeparationRecommendation recommendation,
            String lockedElevatedRoadId,
            RoadNetwork network,
            Runnable adoptAction) {
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
        } else if (lockedElevatedRoadId == null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.crossing_recommendation_pending"));
        }

        if (recommendation.warnsLockedChoice(lockedElevatedRoadId)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.crossing_warning_steep"));
        }
        String adoptId = recommendation.adoptSuggestedElevatedRoadId(lockedElevatedRoadId);
        if (adoptId != null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.crossing_recommendation_better",
                    formatRoadLabel(network, adoptId)));
            if (ImGui.button(PlotI18n.tr("plugin.road.crossing_adopt_recommendation") + "##adopt_rec")) {
                adoptAction.run();
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

    private static int passModeIndex(String elevatedRoadId, List<String> roadIds) {
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            return 0;
        }
        int roadIndex = roadIds.indexOf(elevatedRoadId);
        return roadIndex >= 0 ? roadIndex + 1 : 0;
    }

    private void applyAtGrade(RoadCrossing crossing, RoadNetwork network) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.AT_GRADE, null, null);
        network.setCrossingSharedElevation(crossing.id(), null);
    }

    private void applyAtGrade(RoadNode node, RoadNetwork network) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        network.setNodeGradeSeparation(node.getId(), false, null, null);
    }

    private void applyGradeSeparatedAuto(RoadCrossing crossing, RoadNetwork network, RoadSystemConfig config) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        double clearance = crossing.crossingClearance() != null
            ? crossing.crossingClearance()
            : config.getDefaultCrossingClearance();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, null, clearance);
    }

    private void applyGradeSeparatedAuto(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        network.setNodeGradeSeparation(node.getId(), true, null, clearance);
    }

    private void applyPassModeSelection(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            int index) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        double clearance = crossing.crossingClearance() != null
            ? crossing.crossingClearance()
            : config.getDefaultCrossingClearance();
        if (index == 0) {
            network.setCrossingGradeSeparation(
                crossing.id(), CrossingType.GRADE_SEPARATED, null, clearance);
        } else {
            network.setCrossingGradeSeparation(
                crossing.id(), CrossingType.GRADE_SEPARATED, roadIds.get(index - 1), clearance);
        }
    }

    private void applyPassModeSelection(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            int index) {
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
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
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            String elevatedRoadId) {
        if (elevatedRoadId == null) {
            return;
        }
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        double clearance = crossing.crossingClearance() != null
            ? crossing.crossingClearance()
            : config.getDefaultCrossingClearance();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, elevatedRoadId, clearance);
    }

    private void applyLockedElevatedRoad(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            String elevatedRoadId) {
        if (elevatedRoadId == null) {
            return;
        }
        ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        network.setNodeGradeSeparation(node.getId(), true, elevatedRoadId, clearance);
    }

    private boolean renderClearanceSliderForRegistry(
            RoadCrossing crossing,
            RoadNetwork network,
            RoadSystemConfig config,
            boolean inline) {
        double currentClearance = crossing.crossingClearance() != null
            ? crossing.crossingClearance()
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
            ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
        }
        if (clearanceChanged) {
            network.setCrossingGradeSeparation(
                crossing.id(),
                CrossingType.GRADE_SEPARATED,
                crossing.elevatedRoadId(),
                (double) clearance[0]);
        }
        return clearanceChanged;
    }

    private boolean renderClearanceSliderForNode(RoadNode node, RoadSystemConfig config, boolean inline) {
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
            ctx.networkManager().pushHistory(RoadChangeKind.JUNCTION);
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
