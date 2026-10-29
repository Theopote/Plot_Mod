package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorShell;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 隧道样式独立浮动窗口：标题栏 + 关闭 + 确定/取消，编辑草稿后确认写回。
 */
public final class TunnelStyleFloatingEditor {
    private enum Target {
        GLOBAL,
        ROAD
    }

    private static final int WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings;

    private final ImBoolean open = new ImBoolean(false);
    private boolean focusPending;
    private boolean closeConfirmPending;
    private Target target = Target.GLOBAL;
    private String roadId = "";
    /** 打开时道路是否尚无显式覆盖（确认前不写回）。 */
    private boolean roadWasInheriting;
    private TunnelStyle draft;
    private TunnelStyle baseline;
    private Runnable onHistory;

    public boolean isOpen() {
        return open.get();
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
            null);
    }

    public void openRoad(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (ctx == null || road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        boolean inheriting = road.getStoredTunnelStyle() == null;
        TunnelStyle seed = inheriting
            ? TunnelStyle.fromResolved(TunnelStyle.resolve(road, config))
            : road.getStoredTunnelStyle().copy();
        beginSession(Target.ROAD, road.getId(), inheriting, seed, onHistory);
    }

    public void close() {
        open.set(false);
        focusPending = false;
        closeConfirmPending = false;
        draft = null;
        baseline = null;
        onHistory = null;
        roadId = "";
        roadWasInheriting = false;
    }

    /** 测试用：当前草稿是否相对打开时基线有改动。 */
    boolean isDirtyForTest() {
        return isDirty();
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

    public void render(RoadUiContext ctx) {
        if (ctx == null || !open.get() || draft == null || baseline == null) {
            return;
        }
        boolean focus = focusPending;
        focusPending = false;
        RoadFloatingEditorShell.prepareWindow("##tunnel_style_floating", open, focus);

        boolean visible = ImGui.begin(
            PlotI18n.tr("plugin.road.tunnel.editor_window") + "##tunnel_style_floating",
            open,
            WINDOW_FLAGS);
        try {
            if (!visible) {
                return;
            }
            float footer = RoadFloatingEditorShell.footerReserveHeight(false);
            if (ImGui.beginChild(
                    "##tunnel_style_body",
                    0f,
                    -footer,
                    false,
                    ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                TunnelStyleEditor.render(ctx, draft, () -> draft.clamp());
            }
            ImGui.endChild();

            boolean dirty = isDirty();
            RoadFloatingEditorShell.renderFooter(
                ctx,
                dirty,
                null,
                this::requestCancel,
                () -> confirm(ctx));
        } finally {
            ImGui.end();
        }

        // 标题栏 ×：有未保存改动时拦截并弹出确认
        if (!open.get() && !closeConfirmPending) {
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
                return true;
            },
            this::discardAndClose,
            () -> {
                closeConfirmPending = false;
                open.set(true);
            });
    }

    private void beginSession(
            Target target,
            String roadId,
            boolean roadWasInheriting,
            TunnelStyle initial,
            Runnable onHistory) {
        this.target = target;
        this.roadId = roadId != null ? roadId : "";
        this.roadWasInheriting = roadWasInheriting;
        this.onHistory = onHistory;
        TunnelStyle seed = initial != null ? initial : new TunnelStyle();
        seed.ensureConfigDefaults();
        this.baseline = seed.copy();
        this.draft = seed.copy();
        this.focusPending = true;
        this.closeConfirmPending = false;
        this.open.set(true);
    }

    private boolean isDirty() {
        return draft != null && baseline != null && !draft.equals(baseline);
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

        Road road = ctx.networkManager().getNetwork().getRoad(roadId);
        if (road == null) {
            ctx.status().warning(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            close();
            return;
        }
        if (!isDirty()) {
            close();
            return;
        }
        if (onHistory != null) {
            onHistory.run();
        }
        TunnelStyle live = road.getStoredTunnelStyle();
        if (live == null) {
            // 从「继承全局」打开并改过参数：确认后写入道路级覆盖
            road.setTunnelStyle(draft.copy());
        } else {
            live.replaceWith(draft);
            live.clamp();
        }
        ctx.onGenerationConfigChanged();
        ctx.requestOverlayRefresh();
        close();
    }
}
