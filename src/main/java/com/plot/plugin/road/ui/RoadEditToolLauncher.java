package com.plot.plugin.road.ui;

import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Collection;
import java.util.List;

/** 编辑 Tab 专业工具卡片启动器（2×3 网格）。 */
final class RoadEditToolLauncher {

    private static final float CARD_MIN_WIDTH = 108f;
    private static final float CARD_HEIGHT = 56f;

    private RoadEditToolLauncher() {
    }

    static void renderSingle(RoadUiContext ctx, RoadFloatingEditorHost host, String roadId) {
        RoadUiSections.section("plugin.road.edit.tools.section");
        renderGrid(ctx, host, roadId, singleTools());
    }

    static void renderMulti(RoadUiContext ctx, RoadFloatingEditorHost host, Collection<String> roadIds) {
        RoadUiSections.section("plugin.road.edit.tools.section");
        if (roadIds.isEmpty()) {
            return;
        }
        String primaryRoadId = roadIds.iterator().next();
        if (roadIds.size() == 1) {
            renderGrid(ctx, host, primaryRoadId, singleTools());
            return;
        }
        renderBatchCrossSectionPrimary(ctx, host, primaryRoadId, roadIds.size());
    }

    /** 多选仅展示可用的批量横断面入口，不渲染单路专用灰锁卡片。 */
    private static void renderBatchCrossSectionPrimary(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String primaryRoadId,
            int selectedCount) {
        RoadFloatingEditorKind kind = RoadFloatingEditorKind.BATCH_CROSS_SECTION;
        boolean active = host.isOpen(kind);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr("plugin.road.edit.tools.batch_cross_section.title")
            + "\n"
            + PlotI18n.tr("plugin.road.edit.tools.batch_cross_section.multi_hint", selectedCount);
        float width = ImGui.getContentRegionAvailX();
        if (ImGui.button(label + "##tool_" + kind.name(), width, CARD_HEIGHT)) {
            host.openOrFocus(ctx, kind, primaryRoadId);
        }
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private static void renderGrid(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String primaryRoadId,
            List<ToolSpec> tools) {
        float avail = ImGui.getContentRegionAvailX();
        float gap = ImGui.getStyle().getItemSpacingX();
        int columns = avail >= CARD_MIN_WIDTH * 2f + gap ? 2 : 1;
        float cardWidth = Math.max(1f, (avail - gap * (columns - 1)) / columns);

        for (int i = 0; i < tools.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, gap);
            }
            ToolSpec tool = tools.get(i);
            renderToolCard(ctx, host, primaryRoadId, tool, cardWidth);
        }
    }

    private static void renderToolCard(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String roadId,
            ToolSpec tool,
            float width) {
        boolean active = tool.enabled && host.isOpen(tool.kind);
        if (!tool.enabled) {
            ImGui.beginDisabled();
        }
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr(tool.titleKey) + "\n" + PlotI18n.tr(tool.subtitleKey);
        if (ImGui.button(label + "##tool_" + tool.kind.name(), width, CARD_HEIGHT)) {
            if (tool.enabled) {
                host.openOrFocus(ctx, tool.kind, roadId);
            } else {
                ctx.status().info(PlotI18n.tr("plugin.road.edit.tools.coming_soon"));
            }
        }
        if (active) {
            ImGui.popStyleColor();
        }
        if (!tool.enabled) {
            ImGui.endDisabled();
        }
    }

    private static List<ToolSpec> singleTools() {
        return List.of(
            enabled(RoadFloatingEditorKind.CROSS_SECTION, "cross_section"),
            enabled(RoadFloatingEditorKind.VARIABLE_CROSS_SECTION, "variable_cross_section"),
            enabled(RoadFloatingEditorKind.CENTERLINE, "centerline"),
            enabled(RoadFloatingEditorKind.STATION_FACILITIES, "station_facilities"),
            enabled(RoadFloatingEditorKind.DIAGNOSTICS, "diagnostics"));
    }

    private static ToolSpec enabled(RoadFloatingEditorKind kind, String keySuffix) {
        return new ToolSpec(
            kind,
            "plugin.road.edit.tools." + keySuffix + ".title",
            "plugin.road.edit.tools." + keySuffix + ".subtitle",
            true);
    }

    private record ToolSpec(
            RoadFloatingEditorKind kind,
            String titleKey,
            String subtitleKey,
            boolean enabled) {
    }
}
