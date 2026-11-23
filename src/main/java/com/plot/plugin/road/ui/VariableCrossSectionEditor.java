package com.plot.plugin.road.ui;


import com.plot.ui.component.UIUtils;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.editor.CrossSectionDesignerPanel;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 沿桩号可变横断面：左轨桩号列表 + 右详情（横断面设计器同构面板）。
 */
public final class VariableCrossSectionEditor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/VariableCrossSection");

    private static final float STATION_RAIL_WIDTH_FRACTION = 0.28f;

    private Road syncedRoad;
    private RoadVariableCrossSections syncedSections;
    private VariableCrossSectionTransitionMode draftTransitionMode = VariableCrossSectionTransitionMode.STEP;
    private double validationLength = Double.POSITIVE_INFINITY;
    private final List<StationDraft> drafts = new ArrayList<>();
    private int selectedIndex = -1;
    /** 沿路浏览位置（画布/canonical 域）；拖滑条默认 scrub，Shift 时移动选中桩号。 */
    private float playheadStation;

    /** 本帧长度展示上下文：内部桩号为画布域，滑条/文案为世界投影（与路径面板一致，1m=1格）。 */
    private RoadNetwork lengthNetwork;
    private Road lengthRoad;
    private ICoordinateService lengthCoordinates;
    private float canvasLength;
    private float worldLength;

    private final CrossSectionDesignerPanel detailPanel = new CrossSectionDesignerPanel();
    private CrossSectionDraftMutator cachedMutator;
    private CrossSectionDraft cachedMutatorDraft;

    public void reset() {
        syncedRoad = null;
        syncedSections = null;
        draftTransitionMode = VariableCrossSectionTransitionMode.STEP;
        drafts.clear();
        selectedIndex = -1;
        playheadStation = 0f;
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
        lengthNetwork = network;
        lengthRoad = road;
        lengthCoordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        canvasLength = (float) RoadStationing.canonicalLength(network, road);
        worldLength = (float) RoadLengthDisplay.canvasToBlocks(
            canvasLength, network, road, lengthCoordinates);
        validationLength = canvasLength;
        syncDrafts(road, config);
        clampSelection();

        renderTransitionMode(onHistory);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            draftTransitionMode == VariableCrossSectionTransitionMode.SMOOTH
                ? PlotI18n.tr("plugin.road.variable_cross_section_hint_smooth")
                : PlotI18n.tr("plugin.road.variable_cross_section_hint"));
        ImGui.spacing();

        renderPlanSchematic(network, road, canvasLength);
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
            renderStationRail(road, canvasLength, chainageDisplay, onHistory, config);
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX + railWidth + spacing, startY);
        ImGui.beginChild("##vcs_detail", detailWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderDetailPane(ctx, road, canvasLength, chainageDisplay, onHistory, config);
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

    private void renderPlanSchematic(RoadNetwork network, Road road, float roadLength) {
        float canvasMax = Math.max(0.001f, roadLength);
        playheadStation = Math.clamp(playheadStation, 0f, canvasMax);
        List<RoadStationPlanSchematicRenderer.StationMarker> markers = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            markers.add(new RoadStationPlanSchematicRenderer.StationMarker(
                drafts.get(i).station, i == selectedIndex));
        }
        Float clicked = RoadStationPlanSchematicRenderer.render(
            network,
            road,
            playheadStation,
            canvasMax,
            markers,
            List.of());
        if (clicked == null) {
            return;
        }
        playheadStation = Math.clamp(clicked, 0f, canvasMax);
        int owner = controllingDraftIndex(playheadStation);
        if (owner >= 0 && owner != selectedIndex) {
            selectIndex(owner, false);
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

        // 底栏：添加 + 删除同一行，成对管理列表。
        float footerH = ImGui.getFrameHeightWithSpacing() + 4f;
        float listHeight = Math.max(40f, ImGui.getContentRegionAvailY() - footerH);
        ImGui.beginChild("##vcs_station_list", 0f, listHeight, false);
        try {
            for (int i = 0; i < drafts.size(); i++) {
                StationDraft draft = drafts.get(i);
                ImGui.pushID(i);
                String validation = validateDraft(draft, drafts, i, roadLength);
                String label = formatRailLabel(draft, chainageDisplay, lengthNetwork, lengthRoad, lengthCoordinates);
                boolean selected = i == selectedIndex;
                int stylePushes = 0;
                if (selected) {
                    // Selectable 选中态用 Header 色；蓝色高亮与滑条选中三角一致。
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
                    UIUtils.setTooltip(validation);
                }
                ImGui.popID();
            }
        } finally {
            ImGui.endChild();
        }

        renderStationRailFooter(road, roadLength, onHistory, config);
    }

    /** 左轨底栏：添加与删除并排，窄栏时删除仍可读。 */
    private void renderStationRailFooter(
            Road road,
            float roadLength,
            Runnable onHistory,
            RoadSystemConfig config) {
        float avail = ImGui.getContentRegionAvailX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float deleteW = Math.clamp(avail * 0.34f, 56f, 88f);
        float addW = Math.max(72f, avail - spacing - deleteW);

        if (ImGui.button(PlotI18n.tr("plugin.road.variable_cross_section_add") + "##vcs_add", addW, 0)) {
            if (onHistory != null) {
                onHistory.run();
            }
            drafts.add(StationDraft.defaultEntry(road, config, roadLength, drafts, playheadStation));
            selectIndex(drafts.size() - 1, true);
            applyDraftsIfChanged(road, config);
        }
        ImGui.sameLine(0f, spacing);

        boolean canDelete = selectedIndex >= 0 && selectedIndex < drafts.size();
        if (!canDelete) {
            ImGui.beginDisabled();
        }
        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
        boolean deleted = ImGui.button(PlotI18n.tr("plugin.road.delete") + "##var_xs_delete", deleteW, 0);
        ImGui.popStyleColor(3);
        if (!canDelete) {
            ImGui.endDisabled();
        }
        if (deleted && canDelete) {
            deleteSelectedStation(road, onHistory, config);
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
        // 固定 ID：scrub 时不因选中项变化打断滑条拖动。
        ImGui.pushID("vcs_detail");
        try {
            renderSelectedHeader(road, selectedIndex, roadLength, chainageDisplay, onHistory, config);
            if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
                return;
            }
            draft = drafts.get(selectedIndex);
            ImGui.separator();
            ImGui.spacing();

            CrossSectionDraftMutator mutator = mutatorFor(draft, onHistory);
            mutator.setInheritRoadLighting(true);
            mutator.setInheritedStationLighting(road.getCrossSection().getStreetFurniture().getRoadLampPlacement());
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
            int index,
            float roadLength,
            ChainageDisplayContext chainageDisplay,
            Runnable onHistory,
            RoadSystemConfig config) {
        ImGui.text(PlotI18n.tr("plugin.road.variable_cross_section_station"));
        // 滑条用世界投影长度（与路径面板一致）；内部 playhead/draft.station 仍为画布域。
        float canvasMax = Math.max(0.001f, roadLength);
        playheadStation = Math.clamp(playheadStation, 0f, canvasMax);
        float worldMax = Math.max(0.001f, worldLength > 0f ? worldLength : canvasMax);
        float worldPlayhead = (float) RoadLengthDisplay.canvasToBlocks(
            playheadStation, lengthNetwork, lengthRoad, lengthCoordinates);
        worldPlayhead = Math.clamp(worldPlayhead, 0f, worldMax);
        float[] station = {worldPlayhead};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat("##vcs_station_slider", station, 0, worldMax, RoadUiFormat.SLIDER_METERS);
        boolean sliderActive = ImGui.isItemActive();
        boolean sliderActivated = ImGui.isItemActivated();
        playheadStation = (float) RoadLengthDisplay.blocksToCanvas(
            station[0], lengthNetwork, lengthRoad, lengthCoordinates);
        playheadStation = Math.clamp(playheadStation, 0f, canvasMax);

        boolean moveSelected = ImGui.getIO().getKeyShift();
        if (sliderActivated && moveSelected && onHistory != null) {
            onHistory.run();
        }
        if (sliderActive && moveSelected && index >= 0 && index < drafts.size()) {
            // Shift+拖：移动选中桩号标记。
            drafts.get(index).station = playheadStation;
        } else if (sliderActive && !moveSelected) {
            // 普通拖：播放头 scrub；选中控制该区间的桩号，右侧预览/参数跟着变（标记位置不变）。
            int owner = controllingDraftIndex(playheadStation);
            if (owner >= 0 && owner != selectedIndex) {
                selectIndex(owner, false);
            }
        }
        index = selectedIndex;
        // 桩号标记固定在各 draft.station；播放头由滑块抓手表示。
        drawStationMarkersOnSlider(roadLength, chainageDisplay);
        if (ImGui.isItemHovered() && !sliderActive) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.variable_cross_section_playhead_hint"));
        }
        // 桩号/W/L 摘要已在左侧列表展示，滑条下不再重复。

        if (index >= 0 && index < drafts.size()) {
            String validation = validateDraft(drafts.get(index), drafts, index, roadLength);
            if (validation != null) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, validation);
            }
        }
    }

    private void deleteSelectedStation(Road road, Runnable onHistory, RoadSystemConfig config) {
        int index = selectedIndex;
        if (index < 0 || index >= drafts.size()) {
            return;
        }
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
            selectIndex(Math.min(index, drafts.size() - 1), true);
        }
        applyDraftsIfChanged(road, config);
    }

    /**
     * 在刚渲染的桩号滑条轨道上绘制各桩号标记。
     * 选中：强调色三角；其它：灰色竖线；无效：警告色。点击标记切换选中。
     */
    private void drawStationMarkersOnSlider(float roadLength, ChainageDisplayContext chainageDisplay) {
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
        // 滑块抓手有内边距，标记按可用行程近似映射。
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
        for (int i = 0; i < drafts.size(); i++) {
            StationDraft entry = drafts.get(i);
            float worldStation = (float) RoadLengthDisplay.canvasToBlocks(
                entry.station, lengthNetwork, lengthRoad, lengthCoordinates);
            float t = Math.clamp(worldStation / worldMax, 0f, 1f);
            float mx = usable0 + usableW * t;
            String validation = validateDraft(entry, drafts, i, roadLength);
            boolean selected = i == selectedIndex;
            int color = validation != null
                ? PluginUiColors.WARNING
                : (selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.HINT_GRAY);

            if (selected) {
                // 向下小三角，标明当前编辑桩号。
                float tipY = y1 + 1f;
                float baseY = tipY + 6f;
                drawList.addTriangleFilled(mx, tipY, mx - 4.5f, baseY, mx + 4.5f, baseY, color);
            } else {
                float markTop = y0 + 2f;
                float markBot = y1 - 2f;
                drawList.addLine(mx, markTop, mx, markBot, color, 2f);
                drawList.addCircleFilled(mx, (markTop + markBot) * 0.5f, 2.5f, color);
            }

            float hitPad = 6f;
            if (mouseX >= mx - hitPad && mouseX <= mx + hitPad && mouseY >= y0 - 2f && mouseY <= y1 + 8f) {
                hoveredIndex = i;
            }
        }

        if (hoveredIndex >= 0) {
            StationDraft hovered = drafts.get(hoveredIndex);
            String tip = formatRailLabel(
                hovered, chainageDisplay, lengthNetwork, lengthRoad, lengthCoordinates);
            String validation = validateDraft(hovered, drafts, hoveredIndex, roadLength);
            if (validation != null) {
                tip = tip + "\n" + validation;
            }
            UIUtils.setTooltip(tip);
            if (click && hoveredIndex != selectedIndex && !ImGui.isItemActive()) {
                // 避免与正在拖动的滑块抢交互。
                selectIndex(hoveredIndex, true);
            }
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

    private void selectIndex(int index, boolean syncPlayhead) {
        if (selectedIndex != index) {
            detailPanel.invalidate();
            cachedMutator = null;
            cachedMutatorDraft = null;
        }
        selectedIndex = index;
        if (syncPlayhead && index >= 0 && index < drafts.size()) {
            playheadStation = drafts.get(index).station;
        }
    }

    private void clampSelection() {
        if (drafts.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            selectedIndex = 0;
            playheadStation = drafts.get(0).station;
        }
    }

    /** 阶梯语义：不大于 chainage 的最近草稿下标；之前无桩号则 -1。 */
    private int controllingDraftIndex(float chainage) {
        int best = -1;
        float bestStation = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < drafts.size(); i++) {
            float s = drafts.get(i).station;
            if (s <= chainage + 1e-4f && s >= bestStation) {
                bestStation = s;
                best = i;
            }
        }
        return best;
    }

    private static String formatRailLabel(
            StationDraft draft,
            ChainageDisplayContext chainageDisplay,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        String stationText = RoadLengthDisplay.formatChainage(
            chainageDisplay, draft.station, network, road, coordinates);
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
        if (drafts.isEmpty()) {
            selectedIndex = -1;
            playheadStation = 0f;
        } else {
            selectedIndex = 0;
            playheadStation = drafts.get(0).station;
        }
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
            } catch (IllegalArgumentException e) {
                LOGGER.debug("跳过非法横断面草稿行: station={} ({})", draft.station, e.toString());
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
            return defaultEntry(road, config, roadLength, List.of(), Float.NaN);
        }

        /**
         * 新建桩号：优先播放头，否则落在现有最大桩号之后，并避开已占用位置。
         */
        static StationDraft defaultEntry(
                Road road,
                RoadSystemConfig config,
                double roadLength,
                List<StationDraft> existing,
                float preferredStation) {
            StationDraft draft = new StationDraft();
            draft.station = nextFreeStation(roadLength, existing, preferredStation);
            draft.crossSectionDraft = CrossSectionDraft.fromCrossSection(road.getCrossSection(), config);
            return draft;
        }

        static float nextFreeStation(
                double roadLength,
                List<StationDraft> existing,
                float preferredStation) {
            float len = (float) Math.max(0.0, roadLength);
            if (len <= 1e-6f) {
                return 0f;
            }
            float gap = Math.max(1f, Math.min(10f, len * 0.05f));
            List<StationDraft> occupied = existing != null ? existing : List.of();

            if (Float.isFinite(preferredStation)) {
                float preferred = Math.clamp(preferredStation, 0f, len);
                if (!isStationOccupied(occupied, preferred)) {
                    return preferred;
                }
            }

            float maxExisting = Float.NEGATIVE_INFINITY;
            for (StationDraft d : occupied) {
                if (Float.isFinite(d.station)) {
                    maxExisting = Math.max(maxExisting, d.station);
                }
            }
            if (maxExisting > Float.NEGATIVE_INFINITY) {
                float afterLast = Math.min(len, maxExisting + gap);
                if (!isStationOccupied(occupied, afterLast)) {
                    return afterLast;
                }
            } else {
                // 首个桩号：~25% / 至少 10，短路不超过路长。
                float seed = (float) Math.min(len, Math.max(10.0, len * 0.25));
                if (!isStationOccupied(occupied, seed)) {
                    return seed;
                }
            }

            for (float s = 0f; s <= len + 1e-3f; s += gap) {
                float candidate = Math.min(len, s);
                if (!isStationOccupied(occupied, candidate)) {
                    return candidate;
                }
            }
            // 极密时微调，尽量找到空隙。
            float base = Float.isFinite(preferredStation)
                ? Math.clamp(preferredStation, 0f, len)
                : 0f;
            for (int i = 1; i <= 10000; i++) {
                float candidate = Math.min(len, base + i * 0.01f);
                if (!isStationOccupied(occupied, candidate)) {
                    return candidate;
                }
            }
            return len;
        }

        private static boolean isStationOccupied(List<StationDraft> existing, float station) {
            for (StationDraft draft : existing) {
                if (Math.abs(draft.station - station) < 1e-6f) {
                    return true;
                }
            }
            return false;
        }
    }
}
