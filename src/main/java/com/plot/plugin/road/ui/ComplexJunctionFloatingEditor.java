package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.ui.editor.RoadFloatingEditor;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorShell;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 复杂多路交叉口独立浮动工具窗：附近合并、道路配对与处置引导。
 * 操作直接写 live（合并等），无草稿脏态。
 */
public final class ComplexJunctionFloatingEditor {
    private static final int WINDOW_FLAGS = RoadFloatingEditorShell.WINDOW_FLAGS;

    private final ImBoolean open = new ImBoolean(false);
    private boolean focusPending;
    private String nodeId = "";
    private ComplexJunctionGuidePanel guidePanel;

    public boolean isOpen() {
        return open.get();
    }

    public String nodeId() {
        return nodeId;
    }

    public void open(RoadUiContext ctx, String nodeId) {
        if (ctx == null || nodeId == null || nodeId.isBlank()) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network == null || network.getNode(nodeId) == null) {
            return;
        }
        if (guidePanel == null) {
            guidePanel = new ComplexJunctionGuidePanel(ctx);
        }
        this.nodeId = nodeId;
        this.focusPending = true;
        this.open.set(true);
    }

    public void close() {
        open.set(false);
        focusPending = false;
        nodeId = "";
    }

    public void focus() {
        if (isOpen()) {
            focusPending = true;
        }
    }

    /** 无草稿；始终可关闭。 */
    public RoadFloatingEditor.FinishResult finishOrPrompt(RoadUiContext ctx) {
        if (!isOpen()) {
            return RoadFloatingEditor.FinishResult.CLOSED;
        }
        close();
        return RoadFloatingEditor.FinishResult.CLOSED;
    }

    public void render(RoadUiContext ctx) {
        if (ctx == null || !open.get()) {
            return;
        }
        if (guidePanel == null) {
            guidePanel = new ComplexJunctionGuidePanel(ctx);
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadNode node = network != null ? network.getNode(nodeId) : null;
        if (node == null) {
            close();
            return;
        }

        boolean focus = focusPending;
        focusPending = false;
        RoadFloatingEditorShell.prepareWindow(
            "##complex_junction_floating",
            open,
            focus,
            RoadFloatingEditorShell.cascadeAfterHost(
                RoadFloatingEditorShell.CASCADE_AFTER_HOST_COMPLEX_JUNCTION));

        boolean visible = ImGui.begin(
            PlotI18n.tr("plugin.road.complex_junction_designer_title") + "##complex_junction_floating",
            open,
            WINDOW_FLAGS);
        try {
            if (!visible) {
                return;
            }
            float footer = RoadFloatingEditorShell.footerReserveHeight(false);
            if (ImGui.beginChild("##complex_junction_body", 0f, -footer, false, 0)) {
                int roadCount = network.getDistinctRoadIdsAtNode(node.getId()).size();
                ImGui.textColored(
                    PluginUiColors.ACCENT_BLUE,
                    PlotI18n.tr("plugin.road.complex_junction_designer_heading", roadCount));
                ImGui.spacing();
                guidePanel.renderDesignerBody(network, node);
            }
            ImGui.endChild();
            RoadFloatingEditorShell.renderReadOnlyCloseFooter(this::close);
        } finally {
            ImGui.end();
        }

        if (!open.get()) {
            close();
        }
    }
}
