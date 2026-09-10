package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;

/** 杆塔设计器打开时的草稿基线，用于判断是否有未保存修改。 */
final class PoleDesignerDraftBaseline {
    private PoleDesignerDraftBaseline() {
    }

    static String capture(PoleDesign draft) {
        return draft != null ? draft.toJson() : "";
    }

    static boolean isDirty(PoleDesign draft, String baselineJson) {
        if (draft == null || baselineJson == null) {
            return false;
        }
        return !draft.toJson().equals(baselineJson);
    }
}
