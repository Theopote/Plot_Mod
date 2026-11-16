package com.plot.plugin.road.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 沿桩号附属设施区间：左轨列表 + 右详情；顶栏世界投影 scrub 与区间标记。
 */
public final class StationFacilityEditor {

    private static final float FACILITY_RAIL_WIDTH_FRACTION = 0.28f;
    private static final int MAX_MATERIAL_LENGTH = 128;
    private static final RoadFacilityKind[] KINDS = RoadFacilityKind.values();
    private static final RoadFacilitySide[] SIDES = RoadFacilitySide.values();

    private Road syncedRoad;
    private RoadStationFacilities syncedFacilities;
    private double validationLength = Double.POSITIVE_INFINITY;
    private final List<FacilityRunDraft> drafts = new ArrayList<>();
    private int selectedIndex = -1;
    /** 沿路浏览位置（画布/canonical 域）；拖滑条默认 scrub，Shift 时平移选中区间。 */
    private float playheadStation;

    private RoadNetwork lengthNetwork;
    private Road lengthRoad;
    private ICoordinateService lengthCoordinates;
    private float canvasLength;
    private float worldLength;

    public void reset() {
        syncedRoad = null;
        syncedFacilities = null;
        drafts.clear();
        selectedIndex = -1;
        playheadStation = 0f;
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
                PlotI18n.tr("plugin.road.station_facility_requires_stationing"));
            return;
        }

        lengthNetwork = network;
        lengthRoad = road;
        lengthCoordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        canvasLength = (float) RoadStationing.canonicalLength(network, road);
        worldLength = (float) RoadLengthDisplay.canvasToBlocks(
            canvasLength, network, road, lengthCoordinates);
        validationLength = canvasLength;
        syncDrafts(road);
        clampSelection();

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.station_facility_hint"));
        ImGui.spacing();

        renderOverviewScrub(canvasLength, chainageDisplay, onHistory);
        ImGui.spacing();

        float totalWidth = ImGui.getContentRegionAvailX();
        float bodyHeight = ImGui.getContentRegionAvailY();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float railWidth = Math.max(120f, (totalWidth - spacing) * FACILITY_RAIL_WIDTH_FRACTION);
        float detailWidth = Math.max(160f, totalWidth - spacing - railWidth);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        ImGui.setCursorPos(startX, startY);
        ImGui.beginChild("##facility_station_rail", railWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderFacilityRail(road, canvasLength, chainageDisplay, onHistory);
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX + railWidth + spacing, startY);
        ImGui.beginChild("##facility_detail", detailWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderDetailPane(canvasLength, chainageDisplay, onHistory);
        } finally {
            ImGui.endChild();
        }

        applyDraftsIfChanged(road);
    }

    private void renderOverviewScrub(
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        float canvasMax = Math.max(0.001f, roadLength);
        playheadStation = Math.clamp(playheadStation, 0f, canvasMax);
        float worldMax = Math.max(0.001f, worldLength > 0f ? worldLength : canvasMax);
        float worldPlayhead = (float) RoadLengthDisplay.canvasToBlocks(
            playheadStation, lengthNetwork, lengthRoad, lengthCoordinates);
        worldPlayhead = Math.clamp(worldPlayhead, 0f, worldMax);
        float[] station = {worldPlayhead};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat("##facility_playhead", station, 0, worldMax, RoadUiFormat.SLIDER_METERS);
        boolean sliderActive = ImGui.isItemActive();
        boolean sliderActivated = ImGui.isItemActivated();
        playheadStation = (float) RoadLengthDisplay.blocksToCanvas(
            station[0], lengthNetwork, lengthRoad, lengthCoordinates);
        playheadStation = Math.clamp(playheadStation, 0f, canvasMax);

        boolean moveSelected = ImGui.getIO().getKeyShift();
        if (sliderActivated && moveSelected && onHistory != null) {
            onHistory.run();
        }
        if (sliderActive && moveSelected && selectedIndex >= 0 && selectedIndex < drafts.size()) {
            translateSelectedInterval(playheadStation, canvasMax);
        } else if (sliderActive && !moveSelected) {
            int owner = coveringDraftIndex(playheadStation, canvasMax);
            if (owner >= 0 && owner != selectedIndex) {
                selectIndex(owner, false);
            }
        }
        drawIntervalMarkersOnSlider(roadLength, chainageDisplay);
        if (ImGui.isItemHovered() && !sliderActive) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.station_facility_playhead_hint"));
        }

        String stationText = RoadLengthDisplay.formatChainage(
            chainageDisplay, playheadStation, lengthNetwork, lengthRoad, lengthCoordinates);
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, stationText);
    }

    /** Shift+拖：闭区间保持长度平移；open-end 只移 start。 */
    private void translateSelectedInterval(float newPlayhead, float canvasMax) {
        FacilityRunDraft draft = drafts.get(selectedIndex);
        float oldStart = draft.startStation;
        float delta = newPlayhead - oldStart;
        if (Math.abs(delta) < 1e-6f) {
            return;
        }
        if (draft.openEnded) {
            draft.startStation = Math.clamp(newPlayhead, 0f, canvasMax);
            return;
        }
        float span = Math.max(0.1f, draft.endStation - draft.startStation);
        float newStart = Math.clamp(oldStart + delta, 0f, Math.max(0f, canvasMax - span));
        draft.startStation = newStart;
        draft.endStation = Math.min(canvasMax, newStart + span);
    }

    private void drawIntervalMarkersOnSlider(float roadLength, ChainageDisplayContext chainageDisplay) {
        if (drafts.isEmpty() || roadLength <= 1e-6f) {
            return;
        }
        float x0 = ImGui.getItemRectMinX();
        float y0 = ImGui.getItemRectMinY();
        float x1 = ImGui.getItemRectMaxX();
        float y1 = ImGui.getItemRectMaxY();
        float trackW = x1 - x0;
        float trackH = y1 - y0;
        if (trackW < 8f || trackH < 4f) {
            return;
        }
        float pad = Math.min(6f, trackW * 0.02f);
        float usable0 = x0 + pad;
        float usable1 = x1 - pad;
        float usableW = Math.max(1f, usable1 - usable0);

        ImDrawList drawList = ImGui.getWindowDrawList();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean click = ImGui.isMouseClicked(ImGuiMouseButton.Left);
        int hoveredIndex = -1;

        float worldMax = Math.max(0.001f, worldLength > 0f ? worldLength : roadLength);
        float markTop = y0 + 2f;
        float markBot = y1 - 2f;
        float midY = (markTop + markBot) * 0.5f;

        for (int i = 0; i < drafts.size(); i++) {
            FacilityRunDraft draft = drafts.get(i);
            float worldStart = (float) RoadLengthDisplay.canvasToBlocks(
                draft.startStation, lengthNetwork, lengthRoad, lengthCoordinates);
            float endCanvas = draft.openEnded ? roadLength : draft.endStation;
            float worldEnd = (float) RoadLengthDisplay.canvasToBlocks(
                endCanvas, lengthNetwork, lengthRoad, lengthCoordinates);
            float t0 = Math.clamp(worldStart / worldMax, 0f, 1f);
            float t1 = Math.clamp(worldEnd / worldMax, 0f, 1f);
            float mx0 = usable0 + usableW * t0;
            float mx1 = usable0 + usableW * Math.max(t0, t1);

            String validation = validateDraft(draft, roadLength);
            boolean selected = i == selectedIndex;
            int color = validation != null
                ? PluginUiColors.WARNING
                : (selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.HINT_GRAY);

            drawList.addLine(mx0, midY, mx1, midY, color, selected ? 3f : 2f);
            drawList.addLine(mx0, markTop, mx0, markBot, color, selected ? 2.5f : 1.5f);
            drawList.addLine(mx1, markTop, mx1, markBot, color, selected ? 2.5f : 1.5f);
            if (selected) {
                float tipY = y1 + 1f;
                float baseY = tipY + 6f;
                float midX = (mx0 + mx1) * 0.5f;
                drawList.addTriangleFilled(midX, tipY, midX - 4.5f, baseY, midX + 4.5f, baseY, color);
            }

            float hitPad = 6f;
            if (mouseX >= mx0 - hitPad && mouseX <= mx1 + hitPad
                    && mouseY >= y0 - 2f && mouseY <= y1 + 8f) {
                hoveredIndex = i;
            }
        }

        if (hoveredIndex >= 0) {
            FacilityRunDraft hovered = drafts.get(hoveredIndex);
            String tip = formatRailLabel(
                hovered, chainageDisplay, lengthNetwork, lengthRoad, lengthCoordinates);
            String validation = validateDraft(hovered, roadLength);
            if (validation != null) {
                tip = tip + "\n" + validation;
            }
            ImGui.setTooltip(tip);
            if (click && hoveredIndex != selectedIndex && !ImGui.isItemActive()) {
                selectIndex(hoveredIndex, true);
            }
        }
    }

    private void renderFacilityRail(
            Road road,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        if (drafts.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.station_facility_none"));
        }

        float footerH = ImGui.getFrameHeightWithSpacing() + 4f;
        float listHeight = Math.max(40f, ImGui.getContentRegionAvailY() - footerH);
        ImGui.beginChild("##facility_station_list", 0f, listHeight, false);
        try {
            for (int i = 0; i < drafts.size(); i++) {
                FacilityRunDraft draft = drafts.get(i);
                ImGui.pushID(i);
                String validation = validateDraft(draft, roadLength);
                String label = formatRailLabel(
                    draft, chainageDisplay, lengthNetwork, lengthRoad, lengthCoordinates);
                boolean selected = i == selectedIndex;
                int stylePushes = 0;
                if (selected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, PluginUiColors.ACCENT_BLUE);
                    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, PluginUiColors.ACCENT_BLUE);
                    ImGui.pushStyleColor(ImGuiCol.HeaderActive, PluginUiColors.ACCENT_BLUE);
                    stylePushes += 3;
                }
                if (validation != null) {
                    ImGui.pushStyleColor(ImGuiCol.Text, PluginUiColors.INVALID);
                    stylePushes++;
                }
                if (ImGui.selectable(label + "##rail", selected, ImGuiSelectableFlags.AllowDoubleClick)) {
                    selectIndex(i, true);
                }
                if (stylePushes > 0) {
                    ImGui.popStyleColor(stylePushes);
                }
                if (validation != null && ImGui.isItemHovered()) {
                    ImGui.setTooltip(validation);
                }
                ImGui.popID();
            }
        } finally {
            ImGui.endChild();
        }

        renderFacilityRailFooter(road, roadLength, onHistory);
    }

    private void renderFacilityRailFooter(Road road, float roadLength, Runnable onHistory) {
        float avail = ImGui.getContentRegionAvailX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float deleteW = Math.clamp(avail * 0.34f, 56f, 88f);
        float addW = Math.max(72f, avail - spacing - deleteW);

        if (ImGui.button(PlotI18n.tr("plugin.road.station_facility_add") + "##facility_add", addW, 0)) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(FacilityRunDraft.defaultRun(roadLength, playheadStation));
            selectIndex(drafts.size() - 1, true);
            applyDraftsIfChanged(road);
        }
        ImGui.sameLine(0f, spacing);

        boolean canDelete = selectedIndex >= 0 && selectedIndex < drafts.size();
        if (!canDelete) {
            ImGui.beginDisabled();
        }
        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        boolean deleted = ImGui.button(PlotI18n.tr("plugin.road.delete") + "##facility_delete", deleteW, 0);
        ImGui.popStyleColor(3);
        if (!canDelete) {
            ImGui.endDisabled();
        }
        if (deleted && canDelete) {
            deleteSelectedRun(road, onHistory);
        }
    }

    private void renderDetailPane(
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.station_facility_none"));
            return;
        }

        // 固定 ID：scrub 时不因选中项变化打断详情交互。
        ImGui.pushID("facility_detail");
        try {
            FacilityRunDraft draft = drafts.get(selectedIndex);
            renderDetailFields(draft, roadLength, chainageDisplay, onHistory);
        } finally {
            ImGui.popID();
        }
    }

    private void renderDetailFields(
            FacilityRunDraft draft,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory) {
        float canvasMax = Math.max(0.001f, roadLength);
        float worldMax = Math.max(0.001f, worldLength > 0f ? worldLength : canvasMax);

        float worldStart = (float) RoadLengthDisplay.canvasToBlocks(
            draft.startStation, lengthNetwork, lengthRoad, lengthCoordinates);
        worldStart = Math.clamp(worldStart, 0f, Math.max(0f, worldMax - 0.1f));
        float[] start = {worldStart};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat(PlotI18n.tr("plugin.road.station_facility_start") + "##start", start,
            0, Math.max(0f, worldMax - 0.1f), RoadUiFormat.SLIDER_METERS);
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        draft.startStation = (float) RoadLengthDisplay.blocksToCanvas(
            start[0], lengthNetwork, lengthRoad, lengthCoordinates);
        draft.startStation = Math.clamp(draft.startStation, 0f, canvasMax);
        if (!draft.openEnded && draft.endStation <= draft.startStation + 1e-6f) {
            draft.endStation = Math.min(canvasMax, draft.startStation + 1f);
        }

        ImBoolean openEnded = new ImBoolean(draft.openEnded);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.station_facility_open_end") + "##open", openEnded)) {
            if (onHistory != null) {
                onHistory.run();
            }
        }
        draft.openEnded = openEnded.get();

        if (!draft.openEnded) {
            float worldEnd = (float) RoadLengthDisplay.canvasToBlocks(
                draft.endStation, lengthNetwork, lengthRoad, lengthCoordinates);
            float worldStartMin = (float) RoadLengthDisplay.canvasToBlocks(
                draft.startStation, lengthNetwork, lengthRoad, lengthCoordinates);
            float endMin = Math.min(worldMax, worldStartMin + Math.min(0.1f, worldMax));
            worldEnd = Math.clamp(worldEnd, endMin, worldMax);
            float[] end = {worldEnd};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.station_facility_end") + "##end",
                end,
                endMin,
                worldMax,
                RoadUiFormat.SLIDER_METERS);
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            draft.endStation = (float) RoadLengthDisplay.blocksToCanvas(
                end[0], lengthNetwork, lengthRoad, lengthCoordinates);
            draft.endStation = Math.clamp(draft.endStation, 0f, canvasMax);
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
                RoadUiFormat.SLIDER);
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
                describeDraft(draft, chainageDisplay, roadLength));
        }
    }

    private String describeDraft(
            FacilityRunDraft draft,
            ChainageDisplayContext chainageDisplay,
            float roadLength) {
        String start = RoadLengthDisplay.formatChainage(
            chainageDisplay, draft.startStation, lengthNetwork, lengthRoad, lengthCoordinates);
        String end = draft.openEnded
            ? RoadLengthDisplay.formatChainage(
                chainageDisplay, roadLength, lengthNetwork, lengthRoad, lengthCoordinates)
            : RoadLengthDisplay.formatChainage(
                chainageDisplay, draft.endStation, lengthNetwork, lengthRoad, lengthCoordinates);
        return StationFacilityResolver.describeLabels(
            start,
            end,
            draft.kind,
            draft.side,
            draft.kind == RoadFacilityKind.RETAINING_WALL ? (Double) (double) draft.height : null,
            normalizeMaterial(draft.materialBuffer.get()));
    }

    private void deleteSelectedRun(Road road, Runnable onHistory) {
        int index = selectedIndex;
        if (index < 0 || index >= drafts.size()) {
            return;
        }
        if (onHistory != null) {
            onHistory.run();
        }
        drafts.remove(index);
        if (drafts.isEmpty()) {
            selectedIndex = -1;
        } else {
            selectIndex(Math.min(index, drafts.size() - 1), true);
        }
        applyDraftsIfChanged(road);
    }

    private void selectIndex(int index, boolean syncPlayhead) {
        selectedIndex = index;
        if (syncPlayhead && index >= 0 && index < drafts.size()) {
            playheadStation = drafts.get(index).startStation;
        }
    }

    private void clampSelection() {
        if (drafts.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            selectedIndex = 0;
            playheadStation = drafts.get(0).startStation;
        }
    }

    /** 覆盖 playhead 的第一条草稿；无覆盖则 -1。 */
    private int coveringDraftIndex(float chainage, float roadEnd) {
        for (int i = 0; i < drafts.size(); i++) {
            FacilityRunDraft draft = drafts.get(i);
            if (chainage + 1e-4f < draft.startStation) {
                continue;
            }
            float end = draft.openEnded ? roadEnd : draft.endStation;
            if (chainage <= end + 1e-4f) {
                return i;
            }
        }
        return -1;
    }

    private static String formatRailLabel(
            FacilityRunDraft draft,
            ChainageDisplayContext chainageDisplay,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        String start = RoadLengthDisplay.formatChainage(
            chainageDisplay, draft.startStation, network, road, coordinates);
        String end = draft.openEnded
            ? "…"
            : RoadLengthDisplay.formatChainage(
                chainageDisplay, draft.endStation, network, road, coordinates);
        return start + "-" + end + " "
            + kindLabel(draft.kind) + " · " + sideLabel(draft.side);
    }

    private static String kindLabel(RoadFacilityKind kind) {
        if (kind == RoadFacilityKind.RETAINING_WALL) {
            return PlotI18n.tr("plugin.road.station_facility_kind.retaining_wall");
        }
        return PlotI18n.tr("plugin.road.station_facility_kind.guardrail");
    }

    private static String sideLabel(RoadFacilitySide side) {
        return switch (side) {
            case LEFT -> PlotI18n.tr("plugin.road.station_facility_side.left");
            case RIGHT -> PlotI18n.tr("plugin.road.station_facility_side.right");
            case BOTH -> PlotI18n.tr("plugin.road.station_facility_side.both");
        };
    }

    private void syncDrafts(Road road) {
        if (syncedRoad == road && syncedFacilities == road.getStationFacilities()) {
            return;
        }
        syncedRoad = road;
        syncedFacilities = road.getStationFacilities();
        drafts.clear();
        if (road.getStationFacilities() == null) {
            selectedIndex = -1;
            return;
        }
        for (StationFacilityRun run : road.getStationFacilities().sortedRuns()) {
            drafts.add(FacilityRunDraft.fromRun(run));
        }
        if (!drafts.isEmpty()) {
            selectedIndex = 0;
            playheadStation = drafts.get(0).startStation;
        } else {
            selectedIndex = -1;
        }
    }

    private void applyDraftsIfChanged(Road road) {
        if (hasInvalidDrafts()) return;
        List<StationFacilityRun> built = buildRuns(drafts);
        List<StationFacilityRun> current = road.getStationFacilities() != null
            ? road.getStationFacilities().sortedRuns()
            : List.of();
        if (runsEqual(current, built)) {
            return;
        }
        road.setStationFacilities(built.isEmpty() ? null : new RoadStationFacilities(built));
        syncedFacilities = road.getStationFacilities();
    }

    public boolean hasInvalidDrafts() {
        for (var draft : drafts) {
            if (validateDraft(draft, (float) validationLength) != null) return true;
        }
        return false;
    }

    private static String[] kindLabels() {
        return new String[] {
            PlotI18n.tr("plugin.road.station_facility_kind.retaining_wall"),
            PlotI18n.tr("plugin.road.station_facility_kind.guardrail")
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
        if (!Float.isFinite(draft.startStation) || (!draft.openEnded && !Float.isFinite(draft.endStation))
                || draft.startStation < 0.0f
                || draft.startStation > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        if (!draft.openEnded && draft.endStation <= draft.startStation + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        if (!draft.openEnded && draft.endStation > roadLength + 1e-6f) {
            return PlotI18n.tr("plugin.road.station_facility_range_invalid");
        }
        if (draft.kind == RoadFacilityKind.RETAINING_WALL
                && (!Float.isFinite(draft.height) || draft.height < 1f || draft.height > 8f)) {
            return PlotI18n.tr("plugin.road.station_facility_height_invalid");
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
        Double endStation = draft.openEnded ? null : originalOrEdited(draft.endStation, draft.originalEndStation);
        Double height = draft.kind == RoadFacilityKind.RETAINING_WALL
            ? originalOrEdited(draft.height, draft.originalHeight) : null;
        String material = normalizeMaterial(draft.materialBuffer.get());
        return new StationFacilityRun(
            originalOrEdited(draft.startStation, draft.originalStartStation),
            endStation,
            draft.kind,
            draft.side,
            material,
            height);
    }

    private static double originalOrEdited(float value, Double original) {
        return original != null && Float.compare(value, original.floatValue()) == 0 ? original : value;
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
        Double originalStartStation;
        Double originalEndStation;
        Double originalHeight;
        final ImString materialBuffer = new ImString(MAX_MATERIAL_LENGTH);

        static FacilityRunDraft fromRun(StationFacilityRun run) {
            FacilityRunDraft draft = new FacilityRunDraft();
            draft.startStation = (float) run.getStartStation();
            draft.originalStartStation = run.getStartStation();
            draft.originalEndStation = run.getEndStation();
            draft.originalHeight = run.getHeight();
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
            return defaultRun(roadLength, 0f);
        }

        static FacilityRunDraft defaultRun(double roadLength, float playheadStation) {
            FacilityRunDraft draft = new FacilityRunDraft();
            float length = (float) Math.max(0.0, roadLength);
            float span = (float) Math.max(10.0, roadLength * 0.1);
            float start = Math.clamp(playheadStation, 0f, length);
            if (start + Math.min(span, Math.max(0.1f, length)) > length + 1e-6f && length > 1e-6f) {
                start = Math.max(0f, length - Math.min(span, length));
            }
            draft.startStation = start;
            draft.endStation = Math.min(length, start + span);
            if (draft.endStation <= draft.startStation + 1e-6f && length > 1e-6f) {
                draft.endStation = length;
            }
            draft.openEnded = false;
            draft.kind = RoadFacilityKind.GUARDRAIL;
            draft.side = RoadFacilitySide.BOTH;
            return draft;
        }
    }
}
