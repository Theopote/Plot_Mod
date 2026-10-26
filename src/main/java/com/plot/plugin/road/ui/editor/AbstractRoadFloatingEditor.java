package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.ui.RoadUiContext;
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
        return session != null && session.isDirty();
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
        if (windowOpen.get() && session != null && session.isDirty()) {
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
        RoadFloatingEditorShell.prepareWindow(windowImGuiId(), windowOpen, focusOnOpen);
        focusOnOpen = false;
        if (!ImGui.begin(
                PlotI18n.tr(windowTitleKey()) + windowImGuiId(),
                windowOpen,
                RoadFloatingEditorShell.WINDOW_FLAGS)) {
            handleWindowCloseRequest(ctx);
            session = null;
            return;
        }
        try {
            renderWindowBody(ctx);
            ImGui.spacing();
            RoadFloatingEditorShell.renderFooter(
                ctx,
                isDirty(),
                () -> restoreSession(),
                () -> discard(ctx),
                () -> confirm(ctx));
        } finally {
            ImGui.end();
        }
        renderCloseConfirmDialog(ctx);
        if (!windowOpen.get()) {
            session = null;
        }
    }

    @Override
    public FinishResult finishOrPrompt(RoadUiContext ctx) {
        if (!windowOpen.get()) {
            return FinishResult.CLOSED;
        }
        if (session != null && session.isDirty()) {
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
    }

    protected void restoreSession() {
        if (session != null) {
            session.restore();
        }
    }

    protected void confirm(RoadUiContext ctx) {
        if (session == null) {
            return;
        }
        long liveRevision = ctx.networkManager().getNetworkRevision();
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            liveRevision,
            (open, working) -> commitWithHistory(ctx, () -> applySessionToLive(ctx)));
        switch (result) {
            case NO_CHANGES -> discard(ctx);
            case APPLIED -> {
                ctx.requestOverlayRefresh();
                discard(ctx);
            }
            case CONFLICT -> ctx.status().warning(
                PlotI18n.tr("plugin.road.floating_editor.confirm_conflict"));
            default -> { }
        }
    }

    private void handleWindowCloseRequest(RoadUiContext ctx) {
        if (session != null && session.isDirty()) {
            windowOpen.set(true);
            closeConfirmPending = true;
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
                confirm(ctx);
                closeConfirmPending = false;
                scopeBlocked = false;
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
        ctx.beginNetworkEdit(confirmChangeKind());
        mutation.run();
        ctx.finishNetworkEdit();
    }
}
