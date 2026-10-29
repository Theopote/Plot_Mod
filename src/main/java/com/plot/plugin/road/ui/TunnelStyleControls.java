package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * Compact tunnel style entry points for Edit tab；完整编辑走 {@link TunnelStyleFloatingEditor}。
 */
public final class TunnelStyleControls {
    private static final TunnelStyleFloatingEditor FLOATING = new TunnelStyleFloatingEditor();

    private TunnelStyleControls() {
    }

    public static TunnelStyleFloatingEditor floatingEditor() {
        return FLOATING;
    }

    public static void renderGlobal(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        TunnelStyle style = config.getTunnelStyle();
        renderCompactRow(style, () -> FLOATING.openGlobal(ctx));
    }

    public static void renderRoadOverride(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        boolean inherits = road.getStoredTunnelStyle() == null;
        ImBoolean useGlobal = new ImBoolean(inherits);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.tunnel.use_global_default"), useGlobal)) {
            if (onHistory != null) {
                onHistory.run();
            }
            if (useGlobal.get()) {
                road.setTunnelStyle(null);
            } else {
                road.setTunnelStyle(TunnelStyle.fromResolved(TunnelStyle.resolve(road, config)));
            }
            ctx.onGenerationConfigChanged();
        }
        if (inherits) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.inherits_global"));
            renderSummaryText(TunnelStyle.resolve(road, config));
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.tunnel.edit") + "###tunnel_edit_inherit_" + road.getId())) {
                // 打开编辑即转为显式覆盖草稿；取消则道路仍保持继承（未写回）。
                FLOATING.openRoad(ctx, road, onHistory);
            }
            return;
        }
        TunnelStyle style = road.getStoredTunnelStyle();
        renderCompactRow(style, () -> FLOATING.openRoad(ctx, road, onHistory));
    }

    /** 由 {@link com.plot.plugin.road.manager.RoadUIManager} 每帧调用。 */
    public static void renderFloatingWindow(RoadUiContext ctx) {
        FLOATING.render(ctx);
    }

    private static void renderCompactRow(TunnelStyle style, Runnable onEdit) {
        if (style == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.label"));
        ImGui.sameLine();
        renderSummaryText(ResolvedTunnelStyle.from(style));
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.tunnel.edit") + "###tunnel_edit")) {
            if (onEdit != null) {
                onEdit.run();
            }
        }
    }

    private static void renderSummaryText(ResolvedTunnelStyle style) {
        ImGui.textDisabled(PlotI18n.tr(
            "plugin.road.tunnel.compact_summary",
            PlotI18n.tr(shapeKey(style.shape())),
            style.clearHeight(),
            style.lightSpacing()));
    }

    private static String shapeKey(TunnelShape shape) {
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case RECTANGULAR -> "plugin.road.tunnel.shape.rectangular";
            case ARCH -> "plugin.road.tunnel.shape.arch";
            case HORSESHOE -> "plugin.road.tunnel.shape.horseshoe";
        };
    }
}
