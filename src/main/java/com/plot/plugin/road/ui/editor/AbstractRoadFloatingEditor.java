package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 带 {@link RoadNetworkEditorSession} 的浮动编辑器基类。
 */
abstract class AbstractRoadFloatingEditor implements RoadFloatingEditor {

    protected final ImBoolean windowOpen = new ImBoolean(false);
    protected String roadId = "";
    protected RoadNetworkEditorSession session;
    protected boolean closeConfirmPending;
    protected boolean focusOnOpen;
    protected boolean scopeBlocked;
    protected boolean revisionConflictPending;
    protected boolean reloadConfirmPending;

    protected abstract RoadFloatingEditorKind editorKind();

    protected abstract String windowTitleKey();

    protected abstract String windowImGuiId();

    protected abstract String closeConfirmPopupId();

    protected abstract RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId);

    protected abstract void renderWindowBody(RoadUiContext ctx);

    protected abstract RoadChangeKind confirmChangeKind();

    protected abstract void applySessionToLive(RoadUiContext ctx);

    @Override
    public final RoadFloatingEditorKind kind() {
        return editorKind();
    }

    @Override
    public final String roadId() {
        return roadId;
    }

    @Override
    public final boolean isOpen() {
        return windowOpen.get();
    }

    @Override
    public final boolean isDirty() {
        return session != null && (session.isDirty() || hasInvalidDrafts());
    }

    protected boolean hasInvalidDrafts() { return false; }

    /** 只读壳：无还原/确认页脚，关闭即丢弃会话。 */
    protected boolean isReadOnlyShell() {
        return false;
    }

    @Override
    public void open(RoadUiContext ctx, String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        if (windowOpen.get() && this.roadId.equals(roadId) && session != null) {
            focus();
            return;
        }
        if (windowOpen.get() && isDirty()) {
            closeConfirmPending = true;
            scopeBlocked = true;
            return;
        }
        beginSession(ctx, roadId);
    }

    @Override
    public void focus() {
        focusOnOpen = true;
    }

    @Override
    public void render(RoadUiContext ctx) {
        if (!windowOpen.get()) {
            return;
        }
        if (session == null) {
            session = createSession(ctx, roadId);
        }
        refreshCleanSession(ctx);
        RoadFloatingEditorShell.prepareWindow(
            windowImGuiId(), windowOpen, focusOnOpen, editorKind().ordinal());
        focusOnOpen = false;

        boolean visible = ImGui.begin(
            PlotI18n.tr(windowTitleKey()) + windowImGuiId(),
            windowOpen,
            RoadFloatingEditorShell.WINDOW_FLAGS);
        try {
            if (visible) {
                float footerReserve = isReadOnlyShell()
                    ? RoadFloatingEditorShell.FOOTER_RESERVE_HEIGHT
                    : RoadFloatingEditorShell.footerReserveHeight(revisionConflictPending);
                // 负高度：把底部精确留给页脚，避免固定窗高时确认键下方再空一截。
                ImGui.beginChild("##floating_editor_body", 0f, -footerReserve, false, 0);
                if (!isReadOnlyShell()) {
                    renderRevisionConflictBanner(ctx);
                }
                renderWindowBody(ctx);
                ImGui.endChild();
                if (isReadOnlyShell()) {
                    RoadFloatingEditorShell.renderReadOnlyCloseFooter(() -> discard(ctx));
                } else {
                    RoadFloatingEditorShell.renderFooter(
                        ctx,
                        isDirty(),
                        this::restoreSession,
                        () -> discard(ctx),
                        () -> confirm(ctx));
                }
            }
        } finally {
            ImGui.end();
        }

        if (!windowOpen.get()) {
            handleWindowCloseRequest(ctx);
        }

        renderCloseConfirmDialog(ctx);
        renderReloadConfirmDialog(ctx);

        if (!windowOpen.get()) {
            session = null;
        }
    }

    @Override
    public FinishResult finishOrPrompt(RoadUiContext ctx) {
        if (!windowOpen.get()) {
            return FinishResult.CLOSED;
        }
        if (isDirty()) {
            closeConfirmPending = true;
            scopeBlocked = true;
            return FinishResult.PROMPT_SHOWN;
        }
        discard(ctx);
        return FinishResult.CLOSED;
    }

    @Override
    public void discard(RoadUiContext ctx) {
        windowOpen.set(false);
        closeConfirmPending = false;
        scopeBlocked = false;
        revisionConflictPending = false;
        reloadConfirmPending = false;
        if (session != null) {
            session.cancel();
        }
        session = null;
        roadId = "";
    }

    protected void beginSession(RoadUiContext ctx, String roadId) {
        this.roadId = roadId;
        session = createSession(ctx, roadId);
        windowOpen.set(true);
        focusOnOpen = true;
        closeConfirmPending = false;
        scopeBlocked = false;
        revisionConflictPending = false;
        reloadConfirmPending = false;
    }

    /** 作用域切换等场景：是否有未保存确认或版本冲突待处理。 */
    boolean isInteractionBlocked() {
        return scopeBlocked || closeConfirmPending || revisionConflictPending || reloadConfirmPending;
    }

    protected void restoreSession() {
        if (session != null) {
            session.restore();
        }
    }

    /**
     * @return true 若已应用、无变更关闭，或成功丢弃；false 若版本冲突等导致未写回
     */
    protected boolean confirm(RoadUiContext ctx) {
        if (session == null) {
            return false;
        }
        if (hasInvalidDrafts()) {
            ctx.status().warning(PlotI18n.tr("plugin.road.floating_editor.invalid_draft"));
            return false;
        }
        long liveRevision = ctx.networkManager().getNetworkRevision();
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            liveRevision,
            (open, working) -> commitWithHistory(ctx, () -> applySessionToLive(ctx)));
        return switch (result) {
            case NO_CHANGES -> {
                discard(ctx);
                yield true;
            }
            case APPLIED -> {
                ctx.requestOverlayRefresh();
                discard(ctx);
                yield true;
            }
            case CONFLICT -> {
                revisionConflictPending = true;
                ctx.status().warning(
                    PlotI18n.tr("plugin.road.floating_editor.confirm_conflict"));
                yield false;
            }
        };
    }

    private void handleWindowCloseRequest(RoadUiContext ctx) {
        if (isDirty()) {
            windowOpen.set(true);
            closeConfirmPending = true;
            scopeBlocked = true;
            return;
        }
        discard(ctx);
    }

    private void renderCloseConfirmDialog(RoadUiContext ctx) {
        RoadFloatingEditorShell.renderCloseConfirmDialog(
            closeConfirmPopupId(),
            closeConfirmPending,
            () -> closeConfirmPending = false,
            () -> {
                boolean applied = confirm(ctx);
                if (applied) {
                    closeConfirmPending = false;
                    scopeBlocked = false;
                }
                return applied;
            },
            () -> {
                discard(ctx);
                closeConfirmPending = false;
                scopeBlocked = false;
            },
            () -> {
                closeConfirmPending = false;
                scopeBlocked = false;
            });
    }

    protected void commitWithHistory(RoadUiContext ctx, Runnable mutation) {
        ctx.commitNetworkEdit(confirmChangeKind(), mutation);
    }

    protected void resyncSessionFromLive(RoadUiContext ctx) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        session = createSession(ctx, roadId);
        revisionConflictPending = false;
        ctx.status().info(PlotI18n.tr("plugin.road.floating_editor.reload_from_live_done"));
    }

    /** Read-only/unchanged windows follow live edits; dirty windows retain their conflict protection. */
    void refreshCleanSession(RoadUiContext ctx) {
        if (session != null && session.openRevision() != ctx.networkManager().getNetworkRevision() && !isDirty()) {
            session = createSession(ctx, roadId);
            revisionConflictPending = false;
        }
    }

    private void renderRevisionConflictBanner(RoadUiContext ctx) {
        if (!revisionConflictPending) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.floating_editor.confirm_conflict"));
        if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.reload_from_live") + "##reload_live")) {
            reloadConfirmPending = true;
        }
        ImGui.separator();
    }

    private void renderReloadConfirmDialog(RoadUiContext ctx) {
        RoadFloatingEditorShell.renderReloadConfirmDialog(
            windowImGuiId() + "##reload_confirm",
            reloadConfirmPending,
            () -> reloadConfirmPending = false,
            () -> {
                resyncSessionFromLive(ctx);
                reloadConfirmPending = false;
            },
            () -> reloadConfirmPending = false);
    }

    /** 包内测试：确认写回 live。 */
    boolean confirmEdits(RoadUiContext ctx) {
        return confirm(ctx);
    }

    /** 包内测试：从 live 重建 session。 */
    void resyncFromLive(RoadUiContext ctx) {
        resyncSessionFromLive(ctx);
    }

    /** 包内测试：当前 session。 */
    RoadNetworkEditorSession editorSession() {
        return session;
    }

    boolean hasRevisionConflict() {
        return revisionConflictPending;
    }
}
