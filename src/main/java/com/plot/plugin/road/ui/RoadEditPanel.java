package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编辑 Tab：道路类型、横断面、材质与高级道路设计（围绕当前选择）。
 * <p>
 * 交叉口完整编辑留在路径 Tab 与属性侧栏，此处不嵌套路口编辑器。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadEditWorkspace editWorkspace;
    private final RoadFloatingEditorHost floatingEditorHost;
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private String lastRenderedRoadScopeKey = "";
    private final LinkedHashSet<String> lastSelectedRoadIds = new LinkedHashSet<>();

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.floatingEditorHost = floatingEditorHost;
        this.editWorkspace = new RoadEditWorkspace(
            ctx, defaultParamsPanel, floatingEditorHost);
    }

    public RoadFloatingEditorHost floatingEditorHost() {
        return floatingEditorHost;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        reconcileRoadScope();

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> editWorkspace.renderNoSelectionDefaults();
            case SINGLE -> {
                var road = ctx.networkManager().getPrimarySelectedRoad();
                if (road != null) {
                    editWorkspace.renderSingle(network, road);
                }
            }
            case MULTI -> editWorkspace.renderMulti();
        }
        openPendingFloatingEditors();
    }

    private void openPendingFloatingEditors() {
        openPendingEditor(ctx.consumePendingCenterlineRoadId(), RoadFloatingEditorKind.CENTERLINE);
        openPendingEditor(ctx.consumePendingCrossSectionRoadId(), RoadFloatingEditorKind.CROSS_SECTION);
    }

    private void openPendingEditor(String roadId, RoadFloatingEditorKind kind) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        if (ctx.networkManager().getNetwork().getRoad(roadId) == null) {
            return;
        }
        floatingEditorHost.openOrFocus(ctx, kind, roadId);
    }

    /**
     * 画布拾取等在编辑 Tab 外改选择后调用：脏窗挡住则回滚到上一选择。
     */
    public void reconcileRoadScope() {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        String scopeKey = switch (mode) {
            case NONE -> "none";
            case SINGLE -> {
                var road = ctx.networkManager().getPrimarySelectedRoad();
                yield road != null ? "single:" + road.getId() : "single:none";
            }
            case MULTI -> "multi:" + String.join(",", ctx.networkManager().getSelectedRoadIds());
        };
        LinkedHashSet<String> selectedRoadIds =
            new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());

        if (!lastRenderedRoadScopeKey.isEmpty() && !lastRenderedRoadScopeKey.equals(scopeKey)) {
            if (finishPendingEditsForScope(selectedRoadIds)) {
                restoreRoadSelection(lastSelectedRoadIds);
                return;
            }
        }
        lastRenderedRoadScopeKey = scopeKey;
        lastSelectedRoadIds.clear();
        lastSelectedRoadIds.addAll(selectedRoadIds);
    }

    /**
     * 离开编辑 Tab：干净关闭浮动窗，脏则弹确认。
     *
     * @return true 若应阻止切走
     */
    public boolean finishPendingEdits() {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        boolean blocked = floatingEditorHost.finishOrPromptAll(ctx);
        blocked |= TunnelStyleControls.finishOrPromptLeaving(ctx);
        return blocked;
    }

    /** 已有未保存确认等待用户时，同样视为挡住离开。 */
    public boolean hasLeaveBlocked() {
        return floatingEditorHost.hasInteractionBlocked()
            || TunnelStyleControls.isInteractionBlocked();
    }

    /**
     * 尝试离开编辑 Tab：已有弹窗或脏草稿都会阻止。
     *
     * @return true 若应留在编辑 Tab
     */
    public boolean tryLeaveEdit() {
        if (hasLeaveBlocked()) {
            return true;
        }
        return finishPendingEdits();
    }

    private boolean finishPendingEditsForScope(Set<String> selectedRoadIds) {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        boolean blocked = floatingEditorHost.finishOrPromptOutsideScope(ctx, selectedRoadIds);
        blocked |= TunnelStyleControls.finishOrPromptOutsideScope(ctx, selectedRoadIds);
        return blocked;
    }

    private void restoreRoadSelection(Set<String> roadIds) {
        var networkManager = ctx.networkManager();
        networkManager.clearEdgeSelection();
        if (roadIds == null || roadIds.isEmpty()) {
            return;
        }
        boolean first = true;
        for (String roadId : roadIds) {
            networkManager.selectRoad(roadId, !first);
            first = false;
        }
    }
}
