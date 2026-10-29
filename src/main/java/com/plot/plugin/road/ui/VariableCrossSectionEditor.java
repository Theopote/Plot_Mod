package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadCrossSection;
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

/**
 * 沿桩号可变横断面 CRUD：桩号 + {@link CrossSectionDraft} 模板。
 */
public final class VariableCrossSectionEditor {

    private Road syncedRoad;
    private RoadVariableCrossSections syncedSections;
    private double validationLength = Double.POSITIVE_INFINITY;
    private final List<StationDraft> drafts = new ArrayList<>();

    public void reset() {
        syncedRoad = null;
        syncedSections = null;
        drafts.clear();
    }

    public void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        if (road == null || network == null || ctx == null) {
            return;
        }

        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.variable_cross_section_requires_stationing"));
            return;
        }

        RoadSystemConfig config = ctx.networkManager().getConfig();
        double roadLength = RoadStationing.canonicalLength(network, road);
        validationLength = roadLength;
        syncDrafts(road, config);

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
                        RoadStationFormat.DISTANCE_METERS));
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
        if (syncedRoad == road && syncedSections == road.getVariableCrossSections()) {
            return;
        }
        syncedRoad = road;
        syncedSections = road.getVariableCrossSections();
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
        if (hasInvalidDrafts()) return;
        List<StationCrossSection> built = buildStations(drafts, config);
        List<StationCrossSection> current = road.getVariableCrossSections() != null
            ? road.getVariableCrossSections().sortedStations()
            : List.of();
        if (stationsEqual(current, built, config)) {
            return;
        }
        road.setVariableCrossSections(built.isEmpty() ? null : new RoadVariableCrossSections(built));
        syncedSections = road.getVariableCrossSections();
    }

    public boolean hasInvalidDrafts() {
        for (int i = 0; i < drafts.size(); i++) {
            if (validateDraft(drafts.get(i), drafts, i, (float) validationLength) != null) return true;
        }
        return false;
    }

    private static String validateDraft(StationDraft draft, List<StationDraft> all, int index, float roadLength) {
        if (!Float.isFinite(draft.station) || draft.station < 0.0f || draft.station > roadLength + 1e-6f) {
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
                double station = draft.originalStation != null
                    && Float.compare(draft.station, draft.originalStation.floatValue()) == 0
                    ? draft.originalStation : draft.station;
                RoadCrossSection section = rebuildStationCrossSection(draft);
                stations.add(StationCrossSection.at(station, section));
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
        return com.plot.plugin.road.ui.editor.CrossSectionDraftComparer.equals(left, right);
    }

    /**
     * Rebuilds a station section from the opened original whenever possible so that
     * unexposed materials and unchanged inherited nulls survive a single-field edit.
     */
    static RoadCrossSection rebuildStationCrossSection(StationDraft draft) {
        if (draft.originalCrossSection == null) {
            return draft.crossSectionDraft.toCrossSection();
        }
        RoadCrossSection section = draft.originalCrossSection.copy();
        if (crossSectionDraftEqual(draft.crossSectionDraft, draft.originalDraft)) {
            return section;
        }
        draft.crossSectionDraft.applyChangedFieldsOnto(section, draft.originalDraft);
        return section;
    }

    static final class StationDraft {
        float station;
        Double originalStation;
        CrossSectionDraft crossSectionDraft;
        RoadCrossSection originalCrossSection;
        CrossSectionDraft originalDraft;

        static StationDraft from(StationCrossSection entry, RoadSystemConfig config) {
            StationDraft draft = new StationDraft();
            draft.station = (float) entry.getStation();
            draft.originalStation = entry.getStation();
            // Opening a form or moving its station must preserve inherited and unexposed parameters.
            draft.originalCrossSection = entry.getCrossSection().copy();
            draft.originalDraft = CrossSectionDraft.fromCrossSection(draft.originalCrossSection.copy(), config);
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(entry.getCrossSection(), config);
            return draft;
        }

        static StationDraft defaultEntry(Road road, RoadSystemConfig config, double roadLength) {
            StationDraft draft = new StationDraft();
            draft.station = (float) Math.min(roadLength, Math.max(10.0, roadLength * 0.25));
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(road.getCrossSection(), config);
            return draft;
        }
    }
}
