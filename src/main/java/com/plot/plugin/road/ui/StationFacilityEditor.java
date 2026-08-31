package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 沿桩号附属设施区间 CRUD（挡土墙、护栏、排水）。
 */
public final class StationFacilityEditor {

    private static final int MAX_MATERIAL_LENGTH = 128;
    private static final RoadFacilityKind[] KINDS = RoadFacilityKind.values();
    private static final RoadFacilitySide[] SIDES = RoadFacilitySide.values();

    private String syncedRoadId = "";
    private final List<FacilityRunDraft> drafts = new ArrayList<>();

    public void render(RoadNetwork network, Road road, Runnable onHistory) {
        if (road == null || network == null) {
            return;
        }

        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.station_facility_section"))) {
            return;
        }

        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.station_facility_requires_stationing"));
            return;
        }

        syncDrafts(road);
        double roadLength = RoadStationing.totalLength(network, road);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.station_facility_hint"));

        for (int i = 0; i < drafts.size(); i++) {
            renderDraftRow(road, drafts.get(i), i, (float) roadLength, onHistory);
            if (i < drafts.size() - 1) {
                ImGui.separator();
            }
        }

        applyDraftsIfChanged(road);

        if (ImGui.button(PlotI18n.tr("plugin.road.station_facility_add"))) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(FacilityRunDraft.defaultRun(roadLength));
            applyDraftsIfChanged(road);
        }
    }

    private void renderDraftRow(
            Road road,
            FacilityRunDraft draft,
            int index,
            float roadLength,
            Runnable onHistory) {
        ImGui.pushID(index);

        float[] start = {(float) draft.startStation};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat(PlotI18n.tr("plugin.road.station_facility_start") + "##start", start, 0, roadLength, "%.1fm");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        draft.startStation = start[0];
        if (draft.endStation <= draft.startStation + 1e-6f) {
            draft.endStation = Math.min(roadLength, draft.startStation + 1f);
        }

        ImBoolean openEnded = new ImBoolean(draft.openEnded);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.station_facility_open_end") + "##open", openEnded)) {
            if (onHistory != null) {
                onHistory.run();
            }
        }
        draft.openEnded = openEnded.get();

        if (!draft.openEnded) {
            float[] end = {draft.endStation};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.station_facility_end") + "##end",
                end,
                Math.max(0.1f, draft.startStation + 0.1f),
                roadLength,
                "%.1fm");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            draft.endStation = end[0];
        }

        ImInt kindIndex = new ImInt(draft.kind.ordinal());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(
            PlotI18n.tr("plugin.road.station_facility_kind") + "##kind",
            kindIndex,
            kindLabels())) {
            if (onHistory != null) {
                onHistory.run();
            }
        }
        draft.kind = KINDS[kindIndex.get()];

        ImInt sideIndex = new ImInt(draft.side.ordinal());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(
            PlotI18n.tr("plugin.road.station_facility_side") + "##side",
            sideIndex,
            sideLabels())) {
            if (onHistory != null) {
                onHistory.run();
            }
        }
        draft.side = SIDES[sideIndex.get()];

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint(
            PlotI18n.tr("plugin.road.station_facility_material") + "##material",
            PlotI18n.tr("plugin.road.station_facility_material_hint"),
            draft.materialBuffer,
            ImGuiInputTextFlags.None);
        if (ImGui.isItemDeactivatedAfterEdit() && onHistory != null) {
            onHistory.run();
        }

        if (draft.kind == RoadFacilityKind.RETAINING_WALL) {
            float[] height = {draft.height};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.station_facility_height") + "##height",
                height,
                1f,
                8f,
                "%.1f");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            draft.height = height[0];
        }

        String validation = validateDraft(draft, roadLength);
        if (validation != null) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, validation);
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                StationFacilityResolver.describe(toRun(draft), RoadStationFormat.KILOMETER_PLUS));
        }

        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        if (ImGui.button(PlotI18n.tr("plugin.road.delete") + "##facility_delete")) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.remove(index);
            applyDraftsIfChanged(road);
            ImGui.popStyleColor(3);
            ImGui.popID();
            return;
        }
        ImGui.popStyleColor(3);

        ImGui.popID();
    }

    private void syncDrafts(Road road) {
        if (Objects.equals(syncedRoadId, road.getId())) {
            return;
        }
        syncedRoadId = road.getId();
        drafts.clear();
        if (road.getStationFacilities() == null) {
            return;
        }
        for (StationFacilityRun run : road.getStationFacilities().sortedRuns()) {
            drafts.add(FacilityRunDraft.fromRun(run));
        }
    }

    private void applyDraftsIfChanged(Road road) {
        List<StationFacilityRun> built = buildRuns(drafts);
        List<StationFacilityRun> current = road.getStationFacilities() != null
            ? road.getStationFacilities().sortedRuns()
            : List.of();
        if (runsEqual(current, built)) {
            return;
        }
        road.setStationFacilities(built.isEmpty() ? null : new RoadStationFacilities(built));
    }

    private static String[] kindLabels() {
        return new String[] {
            PlotI18n.tr("plugin.road.station_facility_kind.retaining_wall"),
            PlotI18n.tr("plugin.road.station_facility_kind.guardrail"),
            PlotI18n.tr("plugin.road.station_facility_kind.drainage")
        };
    }

    private static String[] sideLabels() {
        return new String[] {
            PlotI18n.tr("plugin.road.station_facility_side.left"),
            PlotI18n.tr("plugin.road.station_facility_side.right"),
            PlotI18n.tr("plugin.road.station_facility_side.both")
        };
    }

    private static String validateDraft(FacilityRunDraft draft, float roadLength) {
        if (draft.startStation < 0.0f || draft.startStation > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        if (!draft.openEnded && draft.endStation <= draft.startStation + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        if (!draft.openEnded && draft.endStation > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        return null;
    }

    static List<StationFacilityRun> buildRuns(List<FacilityRunDraft> drafts) {
        List<StationFacilityRun> runs = new ArrayList<>();
        for (FacilityRunDraft draft : drafts) {
            if (draft.startStation < 0.0f) {
                continue;
            }
            if (!draft.openEnded && draft.endStation <= draft.startStation) {
                continue;
            }
            try {
                runs.add(toRun(draft));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid draft rows until user fixes them.
            }
        }
        return runs;
    }

    static StationFacilityRun toRun(FacilityRunDraft draft) {
        Double endStation = draft.openEnded ? null : (double) draft.endStation;
        Double height = draft.kind == RoadFacilityKind.RETAINING_WALL ? (double) draft.height : null;
        String material = normalizeMaterial(draft.materialBuffer.get());
        return new StationFacilityRun(
            draft.startStation,
            endStation,
            draft.kind,
            draft.side,
            material,
            height);
    }

    static boolean runsEqual(List<StationFacilityRun> left, List<StationFacilityRun> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            StationFacilityRun a = left.get(i);
            StationFacilityRun b = right.get(i);
            if (Double.compare(a.getStartStation(), b.getStartStation()) != 0) {
                return false;
            }
            if (!Objects.equals(a.getEndStation(), b.getEndStation())) {
                return false;
            }
            if (a.getKind() != b.getKind() || a.getSide() != b.getSide()) {
                return false;
            }
            if (!Objects.equals(a.getMaterial(), b.getMaterial())) {
                return false;
            }
            if (!Objects.equals(a.getHeight(), b.getHeight())) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static final class FacilityRunDraft {
        float startStation;
        float endStation;
        boolean openEnded;
        RoadFacilityKind kind = RoadFacilityKind.GUARDRAIL;
        RoadFacilitySide side = RoadFacilitySide.BOTH;
        float height = 2f;
        final ImString materialBuffer = new ImString(MAX_MATERIAL_LENGTH);

        static FacilityRunDraft fromRun(StationFacilityRun run) {
            FacilityRunDraft draft = new FacilityRunDraft();
            draft.startStation = (float) run.getStartStation();
            draft.openEnded = run.getEndStation() == null;
            draft.endStation = draft.openEnded ? draft.startStation + 10f : run.getEndStation().floatValue();
            draft.kind = run.getKind();
            draft.side = run.getSide();
            if (run.getHeight() != null) {
                draft.height = run.getHeight().floatValue();
            }
            if (run.getMaterial() != null) {
                draft.materialBuffer.set(run.getMaterial());
            }
            return draft;
        }

        static FacilityRunDraft defaultRun(double roadLength) {
            FacilityRunDraft draft = new FacilityRunDraft();
            draft.startStation = 0f;
            draft.endStation = (float) Math.min(roadLength, Math.max(10.0, roadLength * 0.25));
            draft.openEnded = roadLength <= 0.0;
            draft.kind = RoadFacilityKind.GUARDRAIL;
            draft.side = RoadFacilitySide.BOTH;
            return draft;
        }
    }
}
