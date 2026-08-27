package com.plot.plugin.road.ui;

/**
 * 横断面字段编辑的可选钩子：单条道路编辑时展示继承提示与「恢复继承」。
 */
public interface CrossSectionDraftFieldHooks {

    CrossSectionDraftFieldHooks NONE = new CrossSectionDraftFieldHooks() {
    };

    default void onItemActivated() {
    }

    /** 材质/方块选择器是否在打开选择时 pushHistory。 */
    default boolean pushHistoryOnPicker() {
        return false;
    }

    default void afterField(String fieldId, boolean inherited, String inheritLabel, Runnable reset) {
    }
}
