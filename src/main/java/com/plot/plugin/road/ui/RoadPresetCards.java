package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.ui.RoadUiContext.PresetLibraryTarget;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 道路类型预设卡片（配置默认 / 单条道路 / 批量道路共用）。 */
public final class RoadPresetCards {

    private static final float CARD_MIN_WIDTH = 96f;
    private static final float CARD_PADDING_X = 4f;
    private static final float CARD_PADDING_TOP = 8f;
    private static final float CARD_PADDING_BOTTOM = 2f;
    private static final float PREVIEW_GAP = 1f;
    private static final float PREVIEW_HEIGHT = 48f;
    /** 预设库浮动窗：与专业设计器 {@link com.plot.plugin.road.ui.editor.RoadFloatingEditorShell} 同尺寸。 */
    private static final float FLOATING_WIDTH_OF_SCREEN =
        com.plot.plugin.road.ui.editor.RoadFloatingEditorShell.FIXED_WIDTH_OF_SCREEN;
    private static final float FLOATING_HEIGHT_OF_SCREEN =
        com.plot.plugin.road.ui.editor.RoadFloatingEditorShell.FIXED_HEIGHT_OF_SCREEN;
    private static final int FLOATING_FLAGS =
        com.plot.plugin.road.ui.editor.RoadFloatingEditorShell.WINDOW_FLAGS;

    private RoadPresetCards() {
    }

    public static void renderConfigCompact(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderCompactPicker(
            ctx,
            PresetLibraryTarget.CONFIG,
            "config",
            null,
            null,
            config.getSelectedPreset(),
            true,
            null);
    }

    public static void renderForRoadCompact(RoadUiContext ctx, Road road, Runnable onChanged) {
        if (road == null) {
            return;
        }
        String selectedId = road.getStyleId();
        boolean customSelected = selectedId == null || selectedId.isBlank();
        renderCompactPicker(
            ctx,
            PresetLibraryTarget.ROAD,
            "road:" + road.getId(),
            road.getId(),
            null,
            selectedId,
            customSelected,
            onChanged);
    }

    public static void renderForRoadsCompact(
            RoadUiContext ctx,
            Collection<String> roadIds,
            Runnable onChanged) {
        if (roadIds == null || roadIds.isEmpty()) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        String selectedId = resolveSharedStyleId(network, roadIds);
        boolean customSelected = selectedId == null || selectedId.isBlank();
        String selectionKey = "roads:" + String.join(",", roadIds);
        renderCompactPicker(
            ctx,
            PresetLibraryTarget.ROADS,
            selectionKey,
            null,
            roadIds,
            selectedId,
            customSelected,
            onChanged);
    }

    /** 独立预设库浮动窗口；由 {@code RoadUIManager} 每帧调用。 */
    public static void renderFloatingWindow(RoadUiContext ctx) {
        if (ctx == null || !ctx.presetLibraryOpen()) {
            return;
        }
        ImBoolean open = ctx.presetLibraryOpenRef();
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        float w = sw * FLOATING_WIDTH_OF_SCREEN;
        float h = sh * FLOATING_HEIGHT_OF_SCREEN;
        if (ctx.consumePresetLibraryFocusPending()) {
            ImGui.setNextWindowSize(w, h, ImGuiCond.Appearing);
            ImGui.setNextWindowPos(sw * 0.5f - w * 0.5f, sh * 0.5f - h * 0.5f, ImGuiCond.Appearing);
            ImGui.setNextWindowFocus();
        }
        // 固定大小：约束上下限相同 + NoResize，避免用户拖边框改尺寸。
        ImGui.setNextWindowSizeConstraints(w, h, w, h);

        if (!ImGui.begin(
                PlotI18n.tr("plugin.road.preset.library_window") + "##road_preset_library",
                open,
                FLOATING_FLAGS)) {
            ImGui.end();
            if (!open.get()) {
                ctx.closePresetLibrary();
            }
            return;
        }
        try {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.preset.library_hint"));
            ImGui.spacing();
            renderFloatingContent(ctx);
        } finally {
            ImGui.end();
        }
        if (!open.get()) {
            ctx.closePresetLibrary();
        }
    }

    public static void renderConfig(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderSectionHeader(config.getSelectedPreset(), true);
        renderGrid(
            ctx,
            config.getStyles(),
            config.getSelectedPreset(),
            style -> {
                config.applyStyle(style);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                ctx.onGenerationConfigChanged();
            },
                config::markCustom);
    }

    public static void renderForRoad(RoadUiContext ctx, Road road, Runnable onChanged) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        String selectedId = road.getStyleId();
        String themeId = road.getEffectiveThemeId(config);
        renderSectionHeader(selectedId, selectedId == null || selectedId.isBlank());
        renderGrid(
            ctx,
            config.getStyles(),
            selectedId,
            style -> {
                ctx.networkManager().mutateNetwork(() -> road.applyStyle(style, themeId));
                if (onChanged != null) {
                    onChanged.run();
                }
                ctx.requestOverlayRefresh();
            },
            null);
    }

    private static void renderCompactPicker(
            RoadUiContext ctx,
            PresetLibraryTarget target,
            String selectionKey,
            String roadId,
            Collection<String> roadIds,
            String selectedId,
            boolean customSelected,
            Runnable onChanged) {
        ctx.syncPresetLibrarySelection(selectionKey);
        ImGui.text(PlotI18n.tr("plugin.road.preset_section"));
        if (!customSelected && selectedId != null && !selectedId.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.ACCENT_BLUE, "— " + PlotI18n.tr("preset.road." + selectedId));
        } else {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, "— " + PlotI18n.tr("plugin.road.preset_custom"));
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.preset.change") + "##road_preset_change")) {
            if (ctx.presetLibraryOpen() && selectionKey.equals(ctx.presetLibrarySelectionKeyOrEmpty())) {
                ctx.closePresetLibrary();
            } else {
                ctx.openPresetLibrary(target, selectionKey, roadId, roadIds, onChanged);
            }
        }
        ImGui.spacing();
    }

    private static void renderFloatingContent(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetwork network = ctx.networkManager().getNetwork();
        Runnable onChanged = ctx.presetLibraryOnChanged();
        switch (ctx.presetLibraryTarget()) {
            case CONFIG -> renderGrid(
                ctx,
                config.getStyles(),
                config.getSelectedPreset(),
                style -> {
                    config.applyStyle(style);
                    ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                    ctx.onGenerationConfigChanged();
                    ctx.closePresetLibrary();
                },
                () -> {
                    config.markCustom();
                    ctx.closePresetLibrary();
                });
            case ROAD -> {
                Road road = network.getRoad(ctx.presetLibraryRoadId());
                if (road == null) {
                    ImGui.textColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.floating_editor.road_missing"));
                    return;
                }
                String themeId = road.getEffectiveThemeId(config);
                renderGrid(
                    ctx,
                    config.getStyles(),
                    road.getStyleId(),
                    style -> {
                        ctx.networkManager().mutateNetwork(() -> road.applyStyle(style, themeId));
                        if (onChanged != null) {
                            onChanged.run();
                        }
                        ctx.requestOverlayRefresh();
                        ctx.closePresetLibrary();
                    },
                    null);
            }
            case ROADS -> {
                Collection<String> roadIds = ctx.presetLibraryRoadIds();
                if (roadIds == null || roadIds.isEmpty()) {
                    ImGui.textColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.floating_editor.road_missing"));
                    return;
                }
                String selectedId = resolveSharedStyleId(network, roadIds);
                String themeId = resolveSharedThemeId(network, roadIds, config);
                renderGrid(
                    ctx,
                    config.getStyles(),
                    selectedId,
                    style -> {
                        String applyTheme = themeId != null ? themeId : config.getRoadThemeId();
                        ctx.networkManager().mutateNetwork(() -> {
                            for (String roadId : roadIds) {
                                Road road = network.getRoad(roadId);
                                if (road != null) {
                                    road.applyStyle(style, applyTheme);
                                }
                            }
                        });
                        if (onChanged != null) {
                            onChanged.run();
                        }
                        ctx.requestOverlayRefresh();
                        ctx.closePresetLibrary();
                    },
                    null);
            }
        }
    }

    private static void renderSectionHeader(String selectedId, boolean customSelected) {
        ImGui.text(PlotI18n.tr("plugin.road.preset_section"));
        if (!customSelected && selectedId != null && !selectedId.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.ACCENT_BLUE, "— " + PlotI18n.tr("preset.road." + selectedId));
        } else {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, "— " + PlotI18n.tr("plugin.road.preset_custom"));
        }
        ImGui.spacing();
    }

    private static void renderGrid(
            RoadUiContext ctx,
            List<RoadStyle> styles,
            String selectedId,
            java.util.function.Consumer<RoadStyle> onSelect,
            Runnable onCustom) {
        float gap = CARD_PADDING_X;
        float avail = ImGui.getContentRegionAvail().x;
        int columns = avail >= CARD_MIN_WIDTH * 2f + gap ? 2 : 1;
        float cardWidth = columns == 2 ? (avail - gap) * 0.5f : avail;
        boolean customSelected = selectedId == null || selectedId.isBlank();

        List<CardLayout> layouts = new ArrayList<>(styles.size());
        for (RoadStyle style : styles) {
            layouts.add(buildLayout(style, cardWidth));
        }

        for (int index = 0; index < layouts.size(); index++) {
            if (index > 0 && index % columns == 0) {
                ImGui.dummy(0f, gap);
            }
            if (index % columns != 0) {
                ImGui.sameLine(0, gap);
            }
            int rowEnd = Math.min(index + columns, layouts.size());
            float rowHeight = 0f;
            for (int rowIndex = index; rowIndex < rowEnd; rowIndex++) {
                rowHeight = Math.max(rowHeight, layouts.get(rowIndex).height());
            }
            CardLayout layout = layouts.get(index);
            if (renderCard(layout, cardWidth, rowHeight, layout.style().id.equals(selectedId))) {
                onSelect.accept(layout.style());
            }
        }

        if (onCustom != null) {
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.road.preset_custom") + "##road_preset_custom")) {
                onCustom.run();
            }
            if (customSelected) {
                ImGui.sameLine();
                ImGui.textColored(PluginUiColors.ACCENT_BLUE, "●");
            }
            ImGui.spacing();
        }
    }

    private static String resolveSharedStyleId(RoadNetwork network, Collection<String> roadIds) {
        String shared = null;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            String styleId = road.getStyleId();
            if (shared == null) {
                shared = styleId;
            } else if (!ObjectsEqual(shared, styleId)) {
                return null;
            }
        }
        return shared;
    }

    private static String resolveSharedThemeId(
            RoadNetwork network,
            Collection<String> roadIds,
            RoadSystemConfig config) {
        String shared = null;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            String themeId = road.getEffectiveThemeId(config);
            if (shared == null) {
                shared = themeId;
            } else if (!ObjectsEqual(shared, themeId)) {
                return null;
            }
        }
        return shared;
    }

    private static boolean ObjectsEqual(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null || b.isBlank();
        }
        return a.equals(b);
    }

    private static CardLayout buildLayout(RoadStyle style, float cardWidth) {
        RoadCrossSectionPreviewRenderer.CrossSectionLayout sectionLayout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(style);
        String presetName = PlotI18n.tr("preset.road." + style.id);
        String caption = presetName + " ("
            + RoadCrossSectionPreviewRenderer.formatPresetCaption(style, sectionLayout) + ")";
        float innerWidth = Math.max(1f, cardWidth - CARD_PADDING_X * 2f);
        float captionHeight = RoadUiWidgets.wrappedTextHeight(caption, innerWidth);
        float height = CARD_PADDING_TOP
            + PREVIEW_HEIGHT
            + PREVIEW_GAP
            + captionHeight
            + CARD_PADDING_BOTTOM;
        return new CardLayout(style, sectionLayout, caption, height);
    }

    private static boolean renderCard(
            CardLayout layout,
            float width,
            float height,
            boolean selected) {
        ImGui.pushID(layout.style().id);
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Border, PluginUiColors.ACCENT_BLUE);
        }
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, CARD_PADDING_X, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.beginChild(
            "##preset_card",
            width,
            height,
            true,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        ImGui.dummy(0f, CARD_PADDING_TOP);
        float contentWidth = ImGui.getContentRegionAvail().x;
        ImVec2 pos = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout.sectionLayout(),
            pos.x,
            pos.y,
            contentWidth,
            PREVIEW_HEIGHT,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.presetCard());
        ImGui.dummy(contentWidth, PREVIEW_HEIGHT);

        ImGui.dummy(0f, PREVIEW_GAP);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + contentWidth);
        ImGui.text(layout.caption());
        ImGui.popTextWrapPos();

        boolean clicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(0);
        ImGui.endChild();
        ImGui.popStyleVar(2);
        if (selected) {
            ImGui.popStyleColor();
        }
        ImGui.popID();
        return clicked;
    }

    private record CardLayout(
            RoadStyle style,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout sectionLayout,
            String caption,
            float height) {
    }
}
