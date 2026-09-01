package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 沿桩号可变横断面 CRUD：桩号 + {@link CrossSectionDraft} 模板。
 */
public final class VariableCrossSectionEditor {

    private String syncedRoadId = "";
    private final List<StationDraft> drafts = new ArrayList<>();

    public void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        if (road == null || network == null || ctx == null) {
            return;
        }

        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.variable_cross_section_section"))) {
            return;
        }

        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.variable_cross_section_requires_stationing"));
            return;
        }

        RoadSystemConfig config = ctx.networkManager().getConfig();
        syncDrafts(road, config);
        double roadLength = RoadStationing.canonicalLength(network, road);

        if (drafts.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.variable_cross_section_none"));
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.variable_cross_section_hint"));

        for (int i = 0; i < drafts.size(); i++) {
            renderDraftRow(ctx, road, drafts.get(i), i, (float) roadLength, chainageDisplay, onHistory);
            if (i < drafts.size() - 1) {
                ImGui.separator();
            }
        }

        applyDraftsIfChanged(road, config);

        if (ImGui.button(PlotI18n.tr("plugin.road.variable_cross_section_add"))) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(StationDraft.defaultEntry(road, config, roadLength));
            applyDraftsIfChanged(road, config);
        }
    }

    private void renderDraftRow(
            RoadUiContext ctx,
            Road road,
            StationDraft draft,
            int index,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        ImGui.pushID(index);

        float[] station = {draft.station};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.variable_cross_section_station") + "##station",
            station,
            0,
            roadLength,
            "%.1fm");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        draft.station = station[0];

        String validation = validateDraft(draft, drafts, index, roadLength);
        if (validation != null) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, validation);
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                chainageDisplay != null
                    ? VariableCrossSectionResolver.describe(
                        new StationCrossSection(draft.station, draft.crossSectionDraft.toCrossSection()),
                        chainageDisplay)
                    : VariableCrossSectionResolver.describe(
                        new StationCrossSection(draft.station, draft.crossSectionDraft.toCrossSection()),
                        RoadStationFormat.KILOMETER_PLUS));
        }

        int treeFlags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.FramePadding;
        if (ImGui.treeNodeEx(PlotI18n.tr("plugin.road.variable_cross_section_details"), treeFlags)) {
            CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forDraftWithHistory(
                draft.crossSectionDraft,
                onHistory);
            CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.stationVariable(index);
            CrossSectionDraftEditor.renderCrossSection(ctx, mutator, options);
            CrossSectionDraftEditor.renderMaterials(ctx, mutator, options);
            ImGui.treePop();
        }

        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.button(PlotI18n.tr("plugin.road.delete") + "##var_xs_delete")) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.remove(index);
            applyDraftsIfChanged(road, ctx.networkManager().getConfig());
            ImGui.popStyleColor(3);
            ImGui.popID();
            return;
        }
        ImGui.popStyleColor(3);

        ImGui.popID();
    }

    private void syncDrafts(Road road, RoadSystemConfig config) {
        if (Objects.equals(syncedRoadId, road.getId())) {
            return;
        }
        syncedRoadId = road.getId();
        drafts.clear();
        if (road.getVariableCrossSections() == null) {
            return;
        }
        List<StationCrossSection> stations = road.getVariableCrossSections().sortedStations();
        if (stations.isEmpty() && !road.getVariableCrossSections().isEmpty()) {
            stations = road.getVariableCrossSections().getStations();
        }
        for (StationCrossSection entry : stations) {
            drafts.add(StationDraft.from(entry, config));
        }
    }

    private void applyDraftsIfChanged(Road road, RoadSystemConfig config) {
        List<StationCrossSection> built = buildStations(drafts, config);
        List<StationCrossSection> current = road.getVariableCrossSections() != null
            ? road.getVariableCrossSections().sortedStations()
            : List.of();
        if (stationsEqual(current, built, config)) {
            return;
        }
        road.setVariableCrossSections(built.isEmpty() ? null : new RoadVariableCrossSections(built));
    }

    private static String validateDraft(StationDraft draft, List<StationDraft> all, int index, float roadLength) {
        if (draft.station < 0.0f || draft.station > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.variable_cross_section_station_invalid");
        }
        for (int i = 0; i < all.size(); i++) {
            if (i != index && Math.abs(all.get(i).station - draft.station) < 1e-6f) {
                return PlotI18n.tr("plugin.road.variable_cross_section_duplicate");
            }
        }
        return null;
    }

    static List<StationCrossSection> buildStations(List<StationDraft> drafts, RoadSystemConfig config) {
        List<StationDraft> valid = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            StationDraft draft = drafts.get(i);
            if (!isStructurallyValid(draft, drafts, i)) {
                continue;
            }
            valid.add(draft);
        }
        valid.sort(Comparator.comparingDouble(d -> d.station));
        List<StationCrossSection> stations = new ArrayList<>();
        for (StationDraft draft : valid) {
            try {
                stations.add(StationCrossSection.at(draft.station, draft.crossSectionDraft.toCrossSection()));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid draft rows until user fixes them.
            }
        }
        return stations;
    }

    static boolean stationsEqual(
            List<StationCrossSection> left,
            List<StationCrossSection> right,
            RoadSystemConfig config) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            StationCrossSection a = left.get(i);
            StationCrossSection b = right.get(i);
            if (Double.compare(a.getStation(), b.getStation()) != 0) {
                return false;
            }
            CrossSectionDraft leftDraft = CrossSectionDraft.fromCrossSection(a.getCrossSection(), config);
            CrossSectionDraft rightDraft = CrossSectionDraft.fromCrossSection(b.getCrossSection(), config);
            if (!crossSectionDraftEqual(leftDraft, rightDraft)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStructurallyValid(StationDraft draft, List<StationDraft> all, int index) {
        if (draft.station < 0.0f) {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (Math.abs(all.get(i).station - draft.station) < 1e-6f) {
                return false;
            }
        }
        return true;
    }

    private static boolean crossSectionDraftEqual(CrossSectionDraft left, CrossSectionDraft right) {
        if (left == null || right == null) {
            return false;
        }
        return left.width() == right.width()
            && left.laneCount() == right.laneCount()
            && left.includeShoulder() == right.includeShoulder()
            && left.shoulderWidth() == right.shoulderWidth()
            && left.includeSidewalk() == right.includeSidewalk()
            && left.sidewalkWidth() == right.sidewalkWidth()
            && Objects.equals(left.sidewalkMaterial(), right.sidewalkMaterial())
            && left.includeDrainage() == right.includeDrainage()
            && left.includeBikeLane() == right.includeBikeLane()
            && left.bikeLaneWidth() == right.bikeLaneWidth()
            && left.includeMedian() == right.includeMedian()
            && left.medianWidth() == right.medianWidth();
    }

    static final class StationDraft {
        float station;
        CrossSectionDraft crossSectionDraft;

        static StationDraft from(StationCrossSection entry, RoadSystemConfig config) {
            StationDraft draft = new StationDraft();
            draft.station = (float) entry.getStation();
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(entry.getCrossSection(), config);
            return draft;
        }

        static StationDraft defaultEntry(Road road, RoadSystemConfig config, double roadLength) {
            StationDraft draft = new StationDraft();
            draft.station = (float) Math.min(roadLength, Math.max(10.0, roadLength * 0.25));
            if (roadLength <= 0.0) {
                draft.station = 0f;
            }
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(road.getCrossSection(), config);
            return draft;
        }
    }
}
