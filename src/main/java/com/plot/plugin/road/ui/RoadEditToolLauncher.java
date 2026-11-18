package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 横断面工作区专业工具卡片启动器。
 * <p>
 * 单路：详细断面 / 变截面 / 设施 / 诊断 / 隧道（中心线已迁至路线工作区）。
 * 多选：批量横断面 / 隧道 / 变截面 / 设施 / 诊断。
 * 诊断入口仅在有问题时显示；诊断窗已打开时保留，便于关闭。
 */
final class RoadEditToolLauncher {

    private static final float CARD_MIN_WIDTH = 108f;
    private static final float CARD_HEIGHT = 56f;

    private RoadEditToolLauncher() {
    }

    static void renderSingle(RoadUiContext ctx, RoadFloatingEditorHost host, String roadId) {
        RoadUiSections.section("plugin.road.edit.tools.section");
        renderGrid(ctx, host, roadId, singleTools(ctx, host, roadId));
    }

    static void renderMulti(RoadUiContext ctx, RoadFloatingEditorHost host, Collection<String> roadIds) {
        RoadUiSections.section("plugin.road.edit.tools.section");
        if (roadIds.isEmpty()) {
            return;
        }
        String primaryRoadId = roadIds.iterator().next();
        if (roadIds.size() == 1) {
            renderGrid(ctx, host, primaryRoadId, singleTools(ctx, host, primaryRoadId));
            return;
        }
        float gap = ImGui.getStyle().getItemSpacingX();
        float half = Math.max(1f, (ImGui.getContentRegionAvailX() - gap) / 2f);
        int count = roadIds.size();
        renderBatchHostCard(
            ctx, host, primaryRoadId, count, half,
            RoadFloatingEditorKind.BATCH_CROSS_SECTION,
            "batch_cross_section");
        ImGui.sameLine(0f, gap);
        renderBatchTunnelPrimary(ctx, roadIds, half);
        renderBatchHostCard(
            ctx, host, primaryRoadId, count, half,
            RoadFloatingEditorKind.VARIABLE_CROSS_SECTION,
            "batch_variable_cross_section");
        ImGui.sameLine(0f, gap);
        renderBatchHostCard(
            ctx, host, primaryRoadId, count, half,
            RoadFloatingEditorKind.STATION_FACILITIES,
            "batch_station_facilities");
        if (shouldShowDiagnosticsTool(ctx, host, roadIds)) {
            renderBatchHostCard(
                ctx, host, primaryRoadId, count, half,
                RoadFloatingEditorKind.DIAGNOSTICS,
                "batch_diagnostics");
        }
    }

    private static void renderBatchHostCard(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String primaryRoadId,
            int selectedCount,
            float width,
            RoadFloatingEditorKind kind,
            String keySuffix) {
        boolean active = host.isOpen(kind);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        String label = PlotI18n.tr("plugin.road.edit.tools." + keySuffix + ".title")
            + "\n"
            + PlotI18n.tr("plugin.road.edit.tools." + keySuffix + ".multi_hint", selectedCount);
        if (ImGui.button(label + "##tool_batch_" + kind.name(), width, CARD_HEIGHT)) {
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

    private static List<ToolSpec> singleTools(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            String roadId) {
        // 中心线编辑入口已迁至路线工作区。
        List<ToolSpec> tools = new ArrayList<>(List.of(
            ToolSpec.host(RoadFloatingEditorKind.CROSS_SECTION, "cross_section"),
            ToolSpec.host(RoadFloatingEditorKind.VARIABLE_CROSS_SECTION, "variable_cross_section"),
            ToolSpec.host(RoadFloatingEditorKind.STATION_FACILITIES, "station_facilities")));
        if (shouldShowDiagnosticsTool(ctx, host, List.of(roadId))) {
            tools.add(ToolSpec.host(RoadFloatingEditorKind.DIAGNOSTICS, "diagnostics"));
        }
        tools.add(ToolSpec.tunnel());
        return tools;
    }

    /** 有问题，或诊断窗已打开（便于关闭）时显示诊断入口。 */
    private static boolean shouldShowDiagnosticsTool(
            RoadUiContext ctx,
            RoadFloatingEditorHost host,
            Collection<String> roadIds) {
        if (host != null && host.isOpen(RoadFloatingEditorKind.DIAGNOSTICS)) {
            return true;
        }
        return countIssues(ctx, roadIds) > 0;
    }

    private static int countIssues(RoadUiContext ctx, Collection<String> roadIds) {
        if (ctx == null || roadIds == null || roadIds.isEmpty()) {
            return 0;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network == null) {
            return 0;
        }
        int total = 0;
        for (String id : roadIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            Road road = network.getRoad(id);
            if (road != null) {
                total += RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
            }
        }
        return total;
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
