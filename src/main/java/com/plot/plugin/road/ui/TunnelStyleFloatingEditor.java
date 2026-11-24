package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.road.ui.editor.RoadFloatingEditor;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorShell;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 隧道样式浮动窗口：Style 级草稿 + 确认写回，由 {@link com.plot.plugin.road.ui.editor.RoadFloatingEditorHost}
 * 统一渲染 / 脏门禁 / discard；支持全局默认、单路与批量。
 */
public final class TunnelStyleFloatingEditor implements RoadFloatingEditor {
    private enum Target {
        GLOBAL,
        ROAD
    }

    private static final int WINDOW_FLAGS = RoadFloatingEditorShell.WINDOW_FLAGS;

    private final ImBoolean open = new ImBoolean(false);
    private boolean focusPending;
    private boolean closeConfirmPending;
    private boolean lossyBatchConfirmPending;
    private boolean lossyBatchAcknowledged;
    private boolean revisionConflictPending;
    private boolean reloadConfirmPending;
    private long openRevision = Long.MIN_VALUE;
    private Target target = Target.GLOBAL;
    private String roadId = "";
    /** 批量写入目标（含主路）；单路时为空或仅含 {@link #roadId}。 */
    private final LinkedHashSet<String> batchRoadIds = new LinkedHashSet<>();
    /** 打开时道路是否尚无显式覆盖（确认前不写回）。 */
    private boolean roadWasInheriting;
    /** 道路/批量目标：确认时写回 null 以恢复继承全局。 */
    private boolean inheritGlobal;
    private TunnelStyle draft;
    private TunnelStyle baseline;
    /** 撤销用镜像：在编辑激活帧压入上一稳定稿。 */
    private TunnelStyle editMirror;
    private final Deque<TunnelStyle> tunnelUndo = new ArrayDeque<>();
    private final Deque<TunnelStyle> tunnelRedo = new ArrayDeque<>();
    private static final int MAX_TUNNEL_UNDO = 32;
    private final TunnelStyleEditor.PresetSelection presetSelection = new TunnelStyleEditor.PresetSelection();
    private final TunnelStyleEditor.PreviewSession previewSession = new TunnelStyleEditor.PreviewSession();
    private Runnable onHistory;

    @Override
    public RoadFloatingEditorKind kind() {
        return RoadFloatingEditorKind.TUNNEL_STYLE;
    }

    @Override
    public String roadId() {
        return roadId;
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void open(RoadUiContext ctx, String roadId) {
        if (ctx == null || roadId == null || roadId.isBlank()) {
            return;
        }
        Road road = ctx.networkManager().getNetwork().getRoad(roadId);
        if (road == null) {
            return;
        }
        openRoad(ctx, road, ctx::pushRoadEditHistory);
    }

    @Override
    public void focus() {
        if (isOpen()) {
            focusPending = true;
            open.set(true);
        }
    }

    @Override
    public void discard(RoadUiContext ctx) {
        close();
    }

    public void openGlobal(RoadUiContext ctx) {
        if (ctx == null || ctx.networkManager().getConfig() == null) {
            return;
        }
        TunnelStyle live = ctx.networkManager().getConfig().getTunnelStyle();
        beginSession(
            Target.GLOBAL,
            "",
            false,
            live != null ? live.copy() : new TunnelStyle(),
            null,
            List.of(),
            ctx);
    }

    public void openRoad(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (ctx == null || road == null) {
            return;
        }
        openRoads(ctx, List.of(road.getId()), onHistory);
    }

    /** 多选：以第一条道路为草稿种子，确认后写入全部目标道路。 */
    public void openRoads(RoadUiContext ctx, Collection<String> roadIds, Runnable onHistory) {
        if (ctx == null || roadIds == null || roadIds.isEmpty()) {
            return;
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (String id : roadIds) {
            if (id != null && !id.isBlank() && ctx.networkManager().getNetwork().getRoad(id) != null) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        String primaryId = targets.getFirst();
        Road road = ctx.networkManager().getNetwork().getRoad(primaryId);
        RoadSystemConfig config = ctx.networkManager().getConfig();
        boolean inheriting = road.getStoredTunnelStyle() == null;
        TunnelStyle seed = inheriting
            ? TunnelStyle.fromResolved(TunnelStyle.resolve(road, config))
            : road.getStoredTunnelStyle().copy();
        beginSession(Target.ROAD, primaryId, inheriting, seed, onHistory, targets, ctx);
    }

    public void close() {
        open.set(false);
        focusPending = false;
        closeConfirmPending = false;
        lossyBatchConfirmPending = false;
        lossyBatchAcknowledged = false;
        revisionConflictPending = false;
        reloadConfirmPending = false;
        openRevision = Long.MIN_VALUE;
        draft = null;
        baseline = null;
        presetSelection.clear();
        onHistory = null;
        roadId = "";
        batchRoadIds.clear();
        roadWasInheriting = false;
        inheritGlobal = false;
    }

    public int batchTargetCount() {
        return batchRoadIds.isEmpty() ? (roadId.isBlank() ? 0 : 1) : batchRoadIds.size();
    }

    /** 测试用：当前草稿是否相对打开时基线有改动。 */
    boolean isDirtyForTest() {
        return isDirty();
    }

    @Override
    public boolean isDirty() {
        // 勾选「使用全局」：仅当道路当前有显式覆盖时算脏（确认后清除覆盖）。
        if (target == Target.ROAD && inheritGlobal) {
            return !roadWasInheriting;
        }
        return draft != null && baseline != null && !draft.equals(baseline);
    }

    @Override
    public boolean isInteractionBlocked() {
        return closeConfirmPending
            || lossyBatchConfirmPending
            || revisionConflictPending
            || reloadConfirmPending;
    }

    @Override
    public boolean matchesScope(Set<String> selectedRoadIds) {
        if (target != Target.ROAD) {
            return true;
        }
        Set<String> targets = batchRoadIds.isEmpty()
            ? (roadId.isBlank() ? Set.of() : Set.of(roadId))
            : batchRoadIds;
        if (targets.isEmpty()) {
            return true;
        }
        if (selectedRoadIds == null || selectedRoadIds.isEmpty()) {
            return false;
        }
        for (String id : targets) {
            if (!selectedRoadIds.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 离开编辑 Tab 或作用域切换：干净则关闭，脏则弹出确认。
     *
     * @return {@link FinishResult#PROMPT_SHOWN} 时应阻止切换
     */
    @Override
    public FinishResult finishOrPrompt(RoadUiContext ctx) {
        if (!isOpen()) {
            return FinishResult.CLOSED;
        }
        if (isDirty()) {
            closeConfirmPending = true;
            open.set(true);
            return FinishResult.PROMPT_SHOWN;
        }
        close();
        return FinishResult.CLOSED;
    }

    /** 测试用：修改草稿字段后走确认写回。 */
    void mutateDraftForTest(java.util.function.Consumer<TunnelStyle> mutator) {
        if (draft == null || mutator == null) {
            return;
        }
        mutator.accept(draft);
        draft.clamp();
    }

    void confirmForTest(RoadUiContext ctx) {
        confirm(ctx);
    }

    void cancelForTest() {
        discardAndClose();
    }

    @Override
    public void render(RoadUiContext ctx) {
        if (ctx == null || !open.get() || draft == null || baseline == null) {
            return;
        }
        refreshCleanSession(ctx);
        boolean focus = focusPending;
        focusPending = false;
        // 排在 Host 专业窗之后，避免与横断面等完全重叠
        RoadFloatingEditorShell.prepareWindow(
            "##tunnel_style_floating",
            open,
            focus,
            RoadFloatingEditorShell.cascadeAfterHost(
                RoadFloatingEditorShell.CASCADE_AFTER_HOST_TUNNEL));

        boolean visible = ImGui.begin(
            windowTitle(ctx) + "##tunnel_style_floating",
            open,
            WINDOW_FLAGS);
        try {
            if (!visible) {
                return;
            }
            float footer = RoadFloatingEditorShell.footerReserveHeight(revisionConflictPending);
            ImGui.beginChild("##tunnel_style_body", 0f, -footer, false, 0);
            try {
                renderRevisionConflictBanner(ctx);
                if (target == Target.ROAD && batchTargetCount() > 1) {
                    renderBatchBanner(ctx);
                }
                if (target == Target.ROAD) {
                    renderInheritToggle();
                    if (!inheritGlobal) {
                        // 与「使用全局隧道默认」同一行：风格预设下拉。
                        ImGui.sameLine(0f, ImGui.getStyle().getItemSpacingX());
                        ImGui.setNextItemWidth(Math.max(120f, ImGui.getContentRegionAvailX()));
                        TunnelStyleEditor.renderStylePresetCombo(
                            draft, this::onTunnelDraftEdited, presetSelection);
                    }
                }
                if (target == Target.ROAD && inheritGlobal) {
                    renderInheritSummary(ctx);
                } else {
                    if (target == Target.ROAD) {
                        ImGui.spacing();
                    }
                    TunnelStyleEditor.render(
                        ctx,
                        draft,
                        this::onTunnelDraftEdited,
                        presetSelection,
                        target == Target.ROAD ? roadId : null,
                        target != Target.ROAD,
                        previewSession);
                }
            } finally {
                ImGui.endChild();
            }

            boolean dirty = isDirty();
            RoadFloatingEditorShell.renderFooter(
                ctx,
                dirty,
                !tunnelUndo.isEmpty(),
                !tunnelRedo.isEmpty(),
                this::undoTunnelStep,
                this::redoTunnelStep,
                this::restoreBaseline,
                this::requestCancel,
                () -> confirm(ctx));
            RoadFloatingEditorShell.applyPendingContentWidthGrow();
        } finally {
            ImGui.end();
        }

        // 标题栏 ×：有未保存改动时拦截并弹出确认
        if (!open.get() && !closeConfirmPending && !lossyBatchConfirmPending && !revisionConflictPending) {
            if (isDirty()) {
                open.set(true);
                closeConfirmPending = true;
            } else {
                close();
            }
        }

        RoadFloatingEditorShell.renderCloseConfirmDialog(
            "##tunnel_style_close_confirm",
            closeConfirmPending,
            () -> closeConfirmPending = false,
            () -> {
                confirm(ctx);
                // 若仍需批量二次确认 / 冲突，仅关掉关闭确认框。
                return !lossyBatchConfirmPending && !revisionConflictPending;
            },
            this::discardAndClose,
            () -> {
                closeConfirmPending = false;
                open.set(true);
            });

        renderLossyBatchConfirmDialog(ctx);
        renderReloadConfirmDialog(ctx);
    }

    private void refreshCleanSession(RoadUiContext ctx) {
        if (target != Target.ROAD || isDirty() || revisionConflictPending) {
            return;
        }
        long liveRevision = ctx.networkManager().getNetworkRevision();
        if (liveRevision == openRevision) {
            return;
        }
        resyncSessionFromLive(ctx, false);
    }

    private void renderRevisionConflictBanner(RoadUiContext ctx) {
        if (!revisionConflictPending) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.floating_editor.confirm_conflict"));
        if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.reload_from_live")
                + "##tunnel_reload_live")) {
            reloadConfirmPending = true;
        }
        ImGui.separator();
    }

    private void renderReloadConfirmDialog(RoadUiContext ctx) {
        RoadFloatingEditorShell.renderReloadConfirmDialog(
            "##tunnel_style_reload_confirm",
            reloadConfirmPending,
            () -> reloadConfirmPending = false,
            () -> {
                resyncSessionFromLive(ctx, true);
                reloadConfirmPending = false;
            },
            () -> reloadConfirmPending = false);
    }

    private void resyncSessionFromLive(RoadUiContext ctx, boolean notify) {
        if (ctx == null) {
            return;
        }
        if (target == Target.GLOBAL) {
            TunnelStyle live = ctx.networkManager().getConfig() != null
                ? ctx.networkManager().getConfig().getTunnelStyle()
                : null;
            beginSession(
                Target.GLOBAL,
                "",
                false,
                live != null ? live.copy() : new TunnelStyle(),
                onHistory,
                List.of(),
                ctx);
        } else {
            LinkedHashSet<String> targets = new LinkedHashSet<>(batchRoadIds);
            if (targets.isEmpty() && !roadId.isBlank()) {
                targets.add(roadId);
            }
            if (targets.isEmpty()) {
                close();
                return;
            }
            String primaryId = targets.getFirst();
            Road road = ctx.networkManager().getNetwork().getRoad(primaryId);
            if (road == null) {
                close();
                return;
            }
            RoadSystemConfig config = ctx.networkManager().getConfig();
            boolean inheriting = road.getStoredTunnelStyle() == null;
            TunnelStyle seed = inheriting
                ? TunnelStyle.fromResolved(TunnelStyle.resolve(road, config))
                : road.getStoredTunnelStyle().copy();
            beginSession(Target.ROAD, primaryId, inheriting, seed, onHistory, targets, ctx);
        }
        if (notify) {
            ctx.status().info(PlotI18n.tr("plugin.road.floating_editor.reload_from_live_done"));
            RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
        }
    }

    private String windowTitle(RoadUiContext ctx) {
        if (target == Target.GLOBAL) {
            return PlotI18n.tr("plugin.road.tunnel.editor_window_global");
        }
        if (batchTargetCount() > 1) {
            return PlotI18n.tr("plugin.road.tunnel.editor_window_batch");
        }
        String base = PlotI18n.tr("plugin.road.tunnel.editor_window");
        String roadLabel = primaryRoadLabel(ctx);
        if (roadLabel == null || roadLabel.isBlank() || roadLabel.equals(roadId)) {
            return base;
        }
        return base + " (" + roadLabel + ")";
    }

    private void renderBatchBanner(RoadUiContext ctx) {
        String primaryLabel = primaryRoadLabel(ctx);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.tunnel.batch_scope_hint", primaryLabel, batchTargetCount()));
        if (isBatchHeterogeneous(ctx)) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.tunnel.batch_heterogeneous_hint"));
        }
        ImGui.spacing();
    }

    private String primaryRoadLabel(RoadUiContext ctx) {
        if (ctx == null || ctx.networkManager() == null || roadId.isBlank()) {
            return roadId;
        }
        Road road = ctx.networkManager().getNetwork().getRoad(roadId);
        if (road == null) {
            return roadId;
        }
        return RoadEdgeListHelper.formatRoadLabel(ctx.networkManager().getNetwork(), road);
    }

    /** 批量目标当前解析样式是否彼此不一致。 */
    boolean isBatchHeterogeneous(RoadUiContext ctx) {
        if (ctx == null || batchTargetCount() <= 1) {
            return false;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedTunnelStyle first = null;
        for (String id : batchRoadIds) {
            Road road = network.getRoad(id);
            if (road == null) {
                continue;
            }
            ResolvedTunnelStyle resolved = TunnelStyle.resolve(road, config);
            if (first == null) {
                first = resolved;
            } else if (!first.equals(resolved)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyTargetHasExplicitOverride(RoadUiContext ctx) {
        if (ctx == null) {
            return false;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        for (String id : batchRoadIds.isEmpty() ? List.of(roadId) : batchRoadIds) {
            Road road = network.getRoad(id);
            if (road != null && road.getStoredTunnelStyle() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean needsLossyBatchAck(RoadUiContext ctx) {
        if (batchTargetCount() <= 1) {
            return false;
        }
        if (inheritGlobal) {
            return anyTargetHasExplicitOverride(ctx);
        }
        return isBatchHeterogeneous(ctx);
    }

    private void renderLossyBatchConfirmDialog(RoadUiContext ctx) {
        if (!RoadUiWidgets.beginDeferredPopupModal(
                "##tunnel_style_lossy_batch_confirm",
                lossyBatchConfirmPending,
                () -> lossyBatchConfirmPending = false)) {
            return;
        }
        try {
            ImGui.text(PlotI18n.tr("plugin.road.tunnel.batch_lossy_confirm_message"));
            ImGui.separator();
            float buttonWidth = 140f;
            if (ImGui.button(
                    PlotI18n.tr("plugin.road.floating_editor.batch_mapping.lossy_confirm_proceed")
                        + "##tunnel_lossy_ok",
                    buttonWidth,
                    0)) {
                lossyBatchConfirmPending = false;
                lossyBatchAcknowledged = true;
                confirm(ctx);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(
                    PlotI18n.tr("plugin.road.floating_editor.continue_edit") + "##tunnel_lossy_cancel",
                    buttonWidth,
                    0)) {
                lossyBatchConfirmPending = false;
                lossyBatchAcknowledged = false;
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private void beginSession(
            Target target,
            String roadId,
            boolean roadWasInheriting,
            TunnelStyle initial,
            Runnable onHistory,
            Collection<String> batchTargets,
            RoadUiContext ctx) {
        this.target = target;
        this.roadId = roadId != null ? roadId : "";
        this.roadWasInheriting = roadWasInheriting;
        // 打开即为编辑草稿；「使用全局」需用户显式勾选以清除道路覆盖。
        this.inheritGlobal = false;
        this.onHistory = onHistory;
        this.batchRoadIds.clear();
        if (batchTargets != null) {
            for (String id : batchTargets) {
                if (id != null && !id.isBlank()) {
                    this.batchRoadIds.add(id);
                }
            }
        }
        if (target == Target.ROAD && this.batchRoadIds.isEmpty() && !this.roadId.isBlank()) {
            this.batchRoadIds.add(this.roadId);
        }
        TunnelStyle seed = initial != null ? initial : new TunnelStyle();
        seed.ensureConfigDefaults();
        this.baseline = seed.copy();
        this.draft = seed.copy();
        this.editMirror = this.draft.copy();
        this.tunnelUndo.clear();
        this.tunnelRedo.clear();
        this.presetSelection.clear();
        this.presetSelection.detectFromStyle(this.draft);
        this.focusPending = true;
        this.closeConfirmPending = false;
        this.lossyBatchConfirmPending = false;
        this.lossyBatchAcknowledged = false;
        this.revisionConflictPending = false;
        this.reloadConfirmPending = false;
        this.openRevision = ctx != null && ctx.networkManager() != null
            ? ctx.networkManager().getNetworkRevision()
            : Long.MIN_VALUE;
        this.open.set(true);
    }

    private void onTunnelDraftEdited() {
        if (editMirror != null && ImGui.isItemActivated()) {
            tunnelUndo.push(editMirror.copy());
            tunnelRedo.clear();
            while (tunnelUndo.size() > MAX_TUNNEL_UNDO) {
                tunnelUndo.removeLast();
            }
        }
        draft.clamp();
        editMirror = draft.copy();
        presetSelection.detectFromStyle(draft);
    }

    private void undoTunnelStep() {
        if (tunnelUndo.isEmpty() || draft == null) {
            return;
        }
        tunnelRedo.push(draft.copy());
        draft = tunnelUndo.pop();
        editMirror = draft.copy();
        presetSelection.detectFromStyle(draft);
    }

    private void redoTunnelStep() {
        if (tunnelRedo.isEmpty() || draft == null) {
            return;
        }
        tunnelUndo.push(draft.copy());
        draft = tunnelRedo.pop();
        editMirror = draft.copy();
        presetSelection.detectFromStyle(draft);
    }

    private void renderInheritToggle() {
        ImBoolean useGlobal = new ImBoolean(inheritGlobal);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.tunnel.use_global_default"), useGlobal)) {
            inheritGlobal = useGlobal.get();
            if (!inheritGlobal && draft != null) {
                draft.ensureConfigDefaults();
                presetSelection.detectFromStyle(draft);
            }
        }
        if (batchTargetCount() > 1 && inheritGlobal) {
            ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.tunnel.batch_inherit_hint", batchTargetCount()));
        }
    }

    private void renderInheritSummary(RoadUiContext ctx) {
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.inherits_global"));
        Road road = ctx != null && ctx.networkManager() != null
            ? ctx.networkManager().getNetwork().getRoad(roadId)
            : null;
        RoadSystemConfig config = ctx != null && ctx.networkManager() != null
            ? ctx.networkManager().getConfig()
            : null;
        if (road == null || config == null) {
            return;
        }
        var resolved = TunnelStyle.resolve(road, config);
        ImGui.textDisabled(PlotI18n.tr(
            "plugin.road.tunnel.compact_summary",
            PlotI18n.tr(shapeTranslationKey(resolved.shape())),
            resolved.clearHeight(),
            resolved.lightSpacing()));
    }

    private void restoreBaseline() {
        if (baseline == null) {
            return;
        }
        inheritGlobal = false;
        draft = baseline.copy();
        draft.ensureConfigDefaults();
        editMirror = draft.copy();
        tunnelUndo.clear();
        tunnelRedo.clear();
        presetSelection.clear();
        presetSelection.detectFromStyle(draft);
    }

    private void requestCancel() {
        if (isDirty()) {
            closeConfirmPending = true;
            open.set(true);
            return;
        }
        discardAndClose();
    }

    private void discardAndClose() {
        close();
    }

    private void confirm(RoadUiContext ctx) {
        if (ctx == null || draft == null) {
            return;
        }
        draft.clamp();
        if (target == Target.GLOBAL) {
            RoadSystemConfig config = ctx.networkManager().getConfig();
            if (config == null || config.getTunnelStyle() == null) {
                return;
            }
            if (isDirty()) {
                config.getTunnelStyle().replaceWith(draft);
                config.getTunnelStyle().clamp();
                config.markCustom();
                ctx.onGenerationConfigChanged();
            }
            close();
            return;
        }

        List<String> targets = new ArrayList<>(
            batchRoadIds.isEmpty() ? List.of(roadId) : batchRoadIds);
        if (targets.isEmpty() || targets.getFirst().isBlank()) {
            // 面板作用域提示：不压在主面板标签顶栏。
            ctx.status().warning(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
            close();
            return;
        }
        if (!isDirty()) {
            close();
            return;
        }
        if (ctx.networkManager().getNetworkRevision() != openRevision) {
            revisionConflictPending = true;
            open.set(true);
            closeConfirmPending = false;
            return;
        }
        if (needsLossyBatchAck(ctx) && !lossyBatchAcknowledged) {
            lossyBatchConfirmPending = true;
            open.set(true);
            return;
        }
        if (onHistory != null) {
            onHistory.run();
        }
        var network = ctx.networkManager().getNetwork();
        int written = 0;
        for (String targetRoadId : targets) {
            Road road = network.getRoad(targetRoadId);
            if (road == null) {
                continue;
            }
            if (inheritGlobal) {
                road.setTunnelStyle(null);
                written++;
                continue;
            }
            TunnelStyle live = road.getStoredTunnelStyle();
            if (live == null) {
                road.setTunnelStyle(draft.copy());
            } else {
                live.replaceWith(draft);
                live.clamp();
            }
            written++;
        }
        if (written == 0) {
            ctx.status().warning(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            RoadPanelScopedStatus.clearFromToolbarIfPresent(ctx.status());
            close();
            return;
        }
        ctx.onGenerationConfigChanged();
        ctx.requestOverlayRefresh();
        close();
    }

    private static String shapeTranslationKey(com.plot.plugin.road.tunnel.TunnelShape shape) {
        return switch (shape != null ? shape : com.plot.plugin.road.tunnel.TunnelShape.ARCH) {
            case D_SHAPE -> "plugin.road.tunnel.shape.d_shape";
            case RECTANGULAR -> "plugin.road.tunnel.shape.rectangular";
            case HORSESHOE -> "plugin.road.tunnel.shape.horseshoe";
            case ELLIPTICAL -> "plugin.road.tunnel.shape.elliptical";
            case CIRCULAR -> "plugin.road.tunnel.shape.circular";
            case ARCH -> "plugin.road.tunnel.shape.arch";
            case PENTAGONAL -> "plugin.road.tunnel.shape.pentagonal";
        };
    }

    /** 测试用：切换道路级「继承全局」意图。 */
    void setInheritGlobalForTest(boolean inherit) {
        this.inheritGlobal = inherit;
    }

    boolean isInheritGlobalForTest() {
        return inheritGlobal;
    }

    void restoreBaselineForTest() {
        restoreBaseline();
    }

    com.plot.plugin.road.tunnel.TunnelPreset appliedPresetForTest() {
        return presetSelection.applied();
    }

    void acknowledgeLossyBatchForTest() {
        lossyBatchAcknowledged = true;
        lossyBatchConfirmPending = false;
    }
}
