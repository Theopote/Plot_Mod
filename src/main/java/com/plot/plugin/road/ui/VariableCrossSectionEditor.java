package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.editor.CrossSectionDesignerPanel;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 沿桩号可变横断面：左轨桩号列表 + 右详情（横断面设计器同构面板）。
 */
public final class VariableCrossSectionEditor {

    private static final float STATION_RAIL_WIDTH_FRACTION = 0.28f;

    private Road syncedRoad;
    private RoadVariableCrossSections syncedSections;
    private VariableCrossSectionTransitionMode draftTransitionMode = VariableCrossSectionTransitionMode.STEP;
    private double validationLength = Double.POSITIVE_INFINITY;
    private final List<StationDraft> drafts = new ArrayList<>();
    private int selectedIndex = -1;

    private final CrossSectionDesignerPanel detailPanel = new CrossSectionDesignerPanel();
    private CrossSectionDraftMutator cachedMutator;
    private CrossSectionDraft cachedMutatorDraft;

    public void reset() {
        syncedRoad = null;
        syncedSections = null;
        draftTransitionMode = VariableCrossSectionTransitionMode.STEP;
        drafts.clear();
        selectedIndex = -1;
        cachedMutator = null;
        cachedMutatorDraft = null;
        detailPanel.invalidate();
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
        clampSelection();

        renderTransitionMode(onHistory);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            draftTransitionMode == VariableCrossSectionTransitionMode.SMOOTH
                ? PlotI18n.tr("plugin.road.variable_cross_section_hint_smooth")
                : PlotI18n.tr("plugin.road.variable_cross_section_hint"));
        ImGui.spacing();

        float totalWidth = ImGui.getContentRegionAvailX();
        float bodyHeight = ImGui.getContentRegionAvailY();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float railWidth = Math.max(120f, (totalWidth - spacing) * STATION_RAIL_WIDTH_FRACTION);
        float detailWidth = Math.max(160f, totalWidth - spacing - railWidth);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        ImGui.setCursorPos(startX, startY);
        ImGui.beginChild("##vcs_station_rail", railWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderStationRail(road, (float) roadLength, chainageDisplay, onHistory, config);
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX + railWidth + spacing, startY);
        ImGui.beginChild("##vcs_detail", detailWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderDetailPane(ctx, road, (float) roadLength, chainageDisplay, onHistory, config);
        } finally {
            ImGui.endChild();
        }

        applyDraftsIfChanged(road, config);
    }

    private void renderTransitionMode(Runnable onHistory) {
        ImInt index = new ImInt(draftTransitionMode == VariableCrossSectionTransitionMode.SMOOTH ? 1 : 0);
        String[] labels = {
            PlotI18n.tr("plugin.road.variable_cross_section_transition.step"),
            PlotI18n.tr("plugin.road.variable_cross_section_transition.smooth")
        };
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.variable_cross_section_transition") + "##vcs_transition",
                index,
                labels)) {
            if (onHistory != null) {
                onHistory.run();
            }
            draftTransitionMode = index.get() == 1
                ? VariableCrossSectionTransitionMode.SMOOTH
                : VariableCrossSectionTransitionMode.STEP;
        }
    }

    private void renderStationRail(
            Road road,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory,
            RoadSystemConfig config) {
        if (drafts.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.variable_cross_section_none"));
        }

        float listHeight = Math.max(40f, ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing() - 4f);
        ImGui.beginChild("##vcs_station_list", 0f, listHeight, false);
        try {
            for (int i = 0; i < drafts.size(); i++) {
                StationDraft draft = drafts.get(i);
                ImGui.pushID(i);
                String validation = validateDraft(draft, drafts, i, roadLength);
                String label = formatRailLabel(draft, chainageDisplay);
                if (validation != null) {
                    ImGui.pushStyleColor(ImGuiCol.Text, PluginUiColors.INVALID);
                }
                boolean selected = i == selectedIndex;
                if (ImGui.selectable(label + "##rail", selected, ImGuiSelectableFlags.AllowDoubleClick)) {
                    selectIndex(i);
                }
                if (validation != null) {
                    ImGui.popStyleColor();
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(validation);
                    }
                }
                ImGui.popID();
            }
        } finally {
            ImGui.endChild();
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.variable_cross_section_add") + "##vcs_add", -1, 0)) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(StationDraft.defaultEntry(road, config, roadLength));
            selectIndex(drafts.size() - 1);
            applyDraftsIfChanged(road, config);
        }
    }

    private void renderDetailPane(
            RoadUiContext ctx,
            Road road,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory,
            RoadSystemConfig config) {
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.variable_cross_section_none"));
            return;
        }

        StationDraft draft = drafts.get(selectedIndex);
        ImGui.pushID("vcs_detail_" + selectedIndex);
        try {
            renderSelectedHeader(road, draft, selectedIndex, roadLength, chainageDisplay, onHistory, config);
            if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
                return;
            }
            draft = drafts.get(selectedIndex);
            ImGui.separator();
            ImGui.spacing();

            CrossSectionDraftMutator mutator = mutatorFor(draft, onHistory);
            String presetName = road.getStyleId() != null && !road.getStyleId().isBlank()
                ? PlotI18n.tr("preset.road." + road.getStyleId())
                : PlotI18n.tr("plugin.road.preset_custom");
            detailPanel.renderForDraft(ctx, mutator, draft.crossSectionDraft, config, presetName);
        } finally {
            ImGui.popID();
        }
    }

    private void renderSelectedHeader(
            Road road,
            StationDraft draft,
            int index,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory,
            RoadSystemConfig config) {
        float[] station = {draft.station};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.variable_cross_section_station") + "##station",
            station,
            0,
            roadLength,
            RoadUiFormat.SLIDER_METERS);
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

        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        boolean deleted = ImGui.button(PlotI18n.tr("plugin.road.delete") + "##var_xs_delete");
        ImGui.popStyleColor(3);
        if (deleted) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.remove(index);
            cachedMutator = null;
            cachedMutatorDraft = null;
            detailPanel.invalidate();
            if (drafts.isEmpty()) {
                selectedIndex = -1;
            } else {
                selectIndex(Math.min(index, drafts.size() - 1));
            }
            applyDraftsIfChanged(road, config);
        }
    }

    private CrossSectionDraftMutator mutatorFor(StationDraft draft, Runnable onHistory) {
        if (cachedMutator == null || cachedMutatorDraft != draft.crossSectionDraft) {
            cachedMutator = CrossSectionDraftMutator.forDraftWithHistory(
                draft.crossSectionDraft,
                onHistory);
            cachedMutatorDraft = draft.crossSectionDraft;
            detailPanel.invalidate();
        }
        return cachedMutator;
    }

    private void selectIndex(int index) {
        if (selectedIndex != index) {
            detailPanel.invalidate();
            cachedMutator = null;
            cachedMutatorDraft = null;
        }
        selectedIndex = index;
    }

    private void clampSelection() {
        if (drafts.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            selectedIndex = 0;
        }
    }

    private static String formatRailLabel(StationDraft draft, ChainageDisplayContext chainageDisplay) {
        String stationText = chainageDisplay != null
            ? chainageDisplay.format(draft.station)
            : RoadStationing.format(draft.station, RoadStationFormat.DISTANCE_METERS);
        return stationText
            + "  W="
            + draft.crossSectionDraft.width()
            + " L="
            + draft.crossSectionDraft.laneCount();
    }

    private void syncDrafts(Road road, RoadSystemConfig config) {
        if (syncedRoad == road && syncedSections == road.getVariableCrossSections()) {
            return;
        }
        syncedRoad = road;
        syncedSections = road.getVariableCrossSections();
        drafts.clear();
        cachedMutator = null;
        cachedMutatorDraft = null;
        detailPanel.invalidate();
        if (road.getVariableCrossSections() == null) {
            draftTransitionMode = VariableCrossSectionTransitionMode.STEP;
            selectedIndex = -1;
            return;
        }
        draftTransitionMode = road.getVariableCrossSections().getTransitionMode();
        List<StationCrossSection> stations = road.getVariableCrossSections().sortedStations();
        if (stations.isEmpty() && !road.getVariableCrossSections().isEmpty()) {
            stations = road.getVariableCrossSections().getStations();
        }
        for (StationCrossSection entry : stations) {
            drafts.add(StationDraft.from(entry, config));
        }
        selectedIndex = drafts.isEmpty() ? -1 : 0;
    }

    private void applyDraftsIfChanged(Road road, RoadSystemConfig config) {
        if (hasInvalidDrafts()) return;
        List<StationCrossSection> built = buildStations(drafts, config);
        List<StationCrossSection> current = road.getVariableCrossSections() != null
            ? road.getVariableCrossSections().sortedStations()
            : List.of();
        VariableCrossSectionTransitionMode currentMode = road.getVariableCrossSections() != null
            ? road.getVariableCrossSections().getTransitionMode()
            : VariableCrossSectionTransitionMode.STEP;
        boolean stationsUnchanged = stationsEqual(current, built, config);
        boolean modeUnchanged = currentMode == draftTransitionMode;
        if (stationsUnchanged && modeUnchanged) {
            return;
        }
        if (built.isEmpty()) {
            road.setVariableCrossSections(null);
        } else {
            road.setVariableCrossSections(new RoadVariableCrossSections(built, draftTransitionMode));
        }
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
            // Prefer ~25% / at least 10, but never exceed road length (short roads cannot use min=10).
            draft.station = (float) Math.min(roadLength, Math.max(10.0, roadLength * 0.25));
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(road.getCrossSection(), config);
            return draft;
        }
    }
}
