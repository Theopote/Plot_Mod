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
 * Compact tunnel style entry points for Generate and profile workspace panels.
 */
public final class TunnelStyleControls {
    private static final String GLOBAL_POPUP = "##tunnel_style_editor_global";

    private TunnelStyleControls() {
    }

    public static void renderGlobal(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        TunnelStyle style = config.getTunnelStyle();
        renderCompactRow(style, GLOBAL_POPUP);
        renderEditorPopup(ctx, GLOBAL_POPUP, style, () -> {
            style.clamp();
            config.markCustom();
            ctx.onGenerationConfigChanged();
        });
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
        String popupId = "##tunnel_style_editor_" + road.getId();
        if (inherits) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.inherits_global"));
            renderSummaryText(TunnelStyle.resolve(road, config));
            return;
        }
        TunnelStyle style = road.getStoredTunnelStyle();
        renderCompactRow(style, popupId);
        renderEditorPopup(ctx, popupId, style, () -> {
            style.clamp();
            if (onHistory != null) {
                onHistory.run();
            }
            ctx.onGenerationConfigChanged();
        });
    }

    private static void renderCompactRow(TunnelStyle style, String popupId) {
        if (style == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.label"));
        ImGui.sameLine();
        renderSummaryText(ResolvedTunnelStyle.from(style));
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.tunnel.edit") + "###edit" + popupId)) {
            ImGui.openPopup(popupId);
        }
    }

    private static void renderSummaryText(ResolvedTunnelStyle style) {
        ImGui.textDisabled(PlotI18n.tr(
            "plugin.road.tunnel.compact_summary",
            PlotI18n.tr(shapeKey(style.shape())),
            style.clearHeight(),
            style.lightSpacing()));
    }

    private static void renderEditorPopup(
            RoadUiContext ctx,
            String popupId,
            TunnelStyle style,
            Runnable onChanged) {
        if (style == null) {
            return;
        }
        if (ImGui.beginPopup(popupId)) {
            TunnelStyleEditor.render(ctx, style, onChanged);
            ImGui.endPopup();
        }
    }

    private static String shapeKey(TunnelShape shape) {
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case RECTANGULAR -> "plugin.road.tunnel.shape.rectangular";
            case ARCH -> "plugin.road.tunnel.shape.arch";
            case HORSESHOE -> "plugin.road.tunnel.shape.horseshoe";
        };
    }
}
