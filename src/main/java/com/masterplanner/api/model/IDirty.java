package com.masterplanner.api.model;

/**
 * 脏标记接口
 * 用于标记对象状态是否发生变化
 */
public interface IDirty {
    /**
     * 检查对象是否有未保存的更改
     * @return true 如果对象状态已改变
     */
    boolean isDirty();

    /**
     * 清除对象的脏标记
     */
    void clearDirty();

    /**
     * 标记对象为已修改状态
     */
    void markDirty();
} 