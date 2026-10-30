package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Collection;
import java.util.List;

/**
 * 编辑 Tab 专业工具卡片启动器。
 * <p>
 * 单路：六张卡（含隧道），列数随可用宽度在 2–3 间自适应。
 * 多选：批量横断面 + 批量隧道；其余设计器需单选。
 */
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
        float gap = ImGui.getStyle().getItemSpacingX();
        float half = Math.max(1f, (ImGui.getContentRegionAvailX() - gap) / 2f);
        renderBatchCrossSectionPrimary(ctx, host, primaryRoadId, roadIds.size(), half);
        ImGui.sameLine(0f, gap);
        renderBatchTunnelPrimary(ctx, roadIds, half);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.edit.tools.multi_single_only_hint"));
    }

    /** 多选批量横断面入口。 */
    private static void renderBatchCrossSectionPrimary(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String primaryRoadId,
            int selectedCount,
            float width) {
        RoadFloatingEditorKind kind = RoadFloatingEditorKind.BATCH_CROSS_SECTION;
        boolean active = host.isOpen(kind);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr("plugin.road.edit.tools.batch_cross_section.title")
            + "\n"
            + PlotI18n.tr("plugin.road.edit.tools.batch_cross_section.multi_hint", selectedCount);
        if (ImGui.button(label + "##tool_" + kind.name(), width, CARD_HEIGHT)) {
            host.openOrFocus(ctx, kind, primaryRoadId);
        }
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private static void renderBatchTunnelPrimary(
            RoadUiContext ctx,
            Collection<String> roadIds,
            float width) {
        boolean active = TunnelStyleControls.floatingEditor().isOpen();
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr("plugin.road.edit.tools.batch_tunnel.title")
            + "\n"
            + PlotI18n.tr("plugin.road.edit.tools.batch_tunnel.multi_hint", roadIds.size());
        if (ImGui.button(label + "##tool_BATCH_TUNNEL", width, CARD_HEIGHT)) {
            TunnelStyleControls.floatingEditor().openRoads(ctx, roadIds, ctx::pushRoadEditHistory);
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
        int columns = 1;
        if (avail >= CARD_MIN_WIDTH * 3f + gap * 2f) {
            columns = 3;
        } else if (avail >= CARD_MIN_WIDTH * 2f + gap) {
            columns = 2;
        }
        float cardWidth = Math.max(1f, (avail - gap * (columns - 1)) / columns);

        for (int i = 0; i < tools.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, gap);
            }
            renderToolCard(ctx, host, primaryRoadId, tools.get(i), cardWidth);
        }
    }

    private static void renderToolCard(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String roadId,
            ToolSpec tool,
            float width) {
        boolean active = tool.isActive(host);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr(tool.titleKey()) + "\n" + PlotI18n.tr(tool.subtitleKey());
        if (ImGui.button(label + "##tool_" + tool.id(), width, CARD_HEIGHT)) {
            tool.open(ctx, host, roadId);
        }
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private static List<ToolSpec> singleTools() {
        return List.of(
            ToolSpec.host(RoadFloatingEditorKind.CROSS_SECTION, "cross_section"),
            ToolSpec.host(RoadFloatingEditorKind.VARIABLE_CROSS_SECTION, "variable_cross_section"),
            ToolSpec.host(RoadFloatingEditorKind.CENTERLINE, "centerline"),
            ToolSpec.host(RoadFloatingEditorKind.STATION_FACILITIES, "station_facilities"),
            ToolSpec.host(RoadFloatingEditorKind.DIAGNOSTICS, "diagnostics"),
            ToolSpec.tunnel());
    }

    private record ToolSpec(
            String id,
            RoadFloatingEditorKind kind,
            String titleKey,
            String subtitleKey) {

        static ToolSpec host(RoadFloatingEditorKind kind, String keySuffix) {
            return new ToolSpec(
                kind.name(),
                kind,
                "plugin.road.edit.tools." + keySuffix + ".title",
                "plugin.road.edit.tools." + keySuffix + ".subtitle");
        }

        static ToolSpec tunnel() {
            return new ToolSpec(
                "TUNNEL",
                null,
                "plugin.road.edit.tools.tunnel.title",
                "plugin.road.edit.tools.tunnel.subtitle");
        }

        boolean isActive(RoadFloatingEditorHost host) {
            if (kind != null) {
                return host.isOpen(kind);
            }
            return TunnelStyleControls.floatingEditor().isOpen();
        }

        void open(RoadUiContext ctx, RoadFloatingEditorHost host, String roadId) {
            if (kind != null) {
                host.openOrFocus(ctx, kind, roadId);
                return;
            }
            Road road = ctx.networkManager().getNetwork().getRoad(roadId);
            if (road == null) {
                return;
            }
            TunnelStyleControls.floatingEditor().openRoad(ctx, road, ctx::pushRoadEditHistory);
        }
    }
}
