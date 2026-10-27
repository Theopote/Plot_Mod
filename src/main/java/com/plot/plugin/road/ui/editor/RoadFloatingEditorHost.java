package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 编辑 Tab 浮动编辑器注册表：按类型复用窗口实例，统一渲染与作用域清理。
 */
public final class RoadFloatingEditorHost {

    private final Map<RoadFloatingEditorKind, RoadFloatingEditor> editors =
        new EnumMap<>(RoadFloatingEditorKind.class);

    public RoadFloatingEditorHost() {
        editors.put(RoadFloatingEditorKind.CROSS_SECTION, new CrossSectionDesignerWindow());
        editors.put(RoadFloatingEditorKind.VARIABLE_CROSS_SECTION, new VariableCrossSectionDesignerWindow());
        editors.put(RoadFloatingEditorKind.CENTERLINE, new CenterlineDesignerWindow());
        editors.put(RoadFloatingEditorKind.STATION_FACILITIES, new StationFacilityDesignerWindow());
        editors.put(RoadFloatingEditorKind.DIAGNOSTICS, new DiagnosticsDesignerWindow());
        editors.put(RoadFloatingEditorKind.BATCH_CROSS_SECTION, new BatchCrossSectionDesignerWindow());
    }

    public void openOrFocus(RoadUiContext ctx, RoadFloatingEditorKind kind, String roadId) {
        Objects.requireNonNull(ctx, "ctx");
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        RoadFloatingEditor editor = editors.get(kind);
        if (editor == null) {
            ctx.status().info(PlotI18n.tr("plugin.road.edit.tools.coming_soon"));
            return;
        }
        editor.open(ctx, roadId);
    }

    public boolean isOpen(RoadFloatingEditorKind kind) {
        RoadFloatingEditor editor = editors.get(kind);
        return editor != null && editor.isOpen();
    }

    public void renderAll(RoadUiContext ctx) {
        for (RoadFloatingEditor editor : activeEditors()) {
            editor.render(ctx);
        }
    }

    /**
     * 作用域切换或离开编辑 Tab：脏窗口弹出确认，不静默写回其他道路。
     *
     * @return true 若用户选择继续编辑并应阻止作用域切换
     */
    public boolean finishOrPromptAll(RoadUiContext ctx) {
        boolean blocked = false;
        for (RoadFloatingEditor editor : activeEditors()) {
            RoadFloatingEditor.FinishResult result = editor.finishOrPrompt(ctx);
            if (result == RoadFloatingEditor.FinishResult.PROMPT_SHOWN) {
                blocked = true;
            }
        }
        return blocked;
    }

    /**
     * 当前选择不再包含编辑器绑定的道路时，提示保存或丢弃。
     */
    public boolean finishOrPromptOutsideScope(RoadUiContext ctx, Set<String> selectedRoadIds) {
        boolean blocked = false;
        for (RoadFloatingEditor editor : activeEditors()) {
            String boundRoadId = editor.roadId();
            if (boundRoadId == null || boundRoadId.isBlank()) {
                continue;
            }
            if (selectedRoadIds.contains(boundRoadId)) {
                continue;
            }
            RoadFloatingEditor.FinishResult result = editor.finishOrPrompt(ctx);
            if (result == RoadFloatingEditor.FinishResult.PROMPT_SHOWN) {
                blocked = true;
            }
        }
        return blocked;
    }

    public void discardAll(RoadUiContext ctx) {
        for (RoadFloatingEditor editor : activeEditors()) {
            editor.discard(ctx);
        }
    }

    /** 包内测试：按类型取编辑器实例。 */
    RoadFloatingEditor editorForKind(RoadFloatingEditorKind kind) {
        return editors.get(kind);
    }

    /** 是否有浮动编辑器正在等待未保存或版本冲突处理。 */
    public boolean hasInteractionBlocked() {
        for (RoadFloatingEditor editor : editors.values()) {
            if (editor instanceof AbstractRoadFloatingEditor floating && floating.isInteractionBlocked()) {
                return true;
            }
        }
        return false;
    }

    private List<RoadFloatingEditor> activeEditors() {
        List<RoadFloatingEditor> active = new ArrayList<>();
        for (RoadFloatingEditor editor : editors.values()) {
            if (editor.isOpen()) {
                active.add(editor);
            }
        }
        return active;
    }
}
