package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.ui.RoadUiContext;

/** 编辑 Tab 专业浮动编辑器。 */
public interface RoadFloatingEditor {

    enum FinishResult {
        /** 已关闭或无需提示 */
        CLOSED,
        /** 已弹出未保存确认，等待用户选择 */
        PROMPT_SHOWN,
        /** 用户选择继续编辑，阻止作用域切换 */
        BLOCKED
    }

    RoadFloatingEditorKind kind();

    String roadId();

    default boolean matchesScope(java.util.Set<String> selectedRoadIds) {
        return selectedRoadIds.contains(roadId());
    }

    boolean isOpen();

    boolean isDirty();

    void open(RoadUiContext ctx, String roadId);

    void focus();

    void render(RoadUiContext ctx);

    /** 作用域切换或插件停用时：干净则关闭，脏则弹出确认。 */
    FinishResult finishOrPrompt(RoadUiContext ctx);

    /** 强制关闭并丢弃未保存修改。 */
    void discard(RoadUiContext ctx);
}
