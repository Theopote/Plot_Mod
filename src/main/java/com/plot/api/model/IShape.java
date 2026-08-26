package com.plot.api.model;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * API 层图形抽象，避免接口直接依赖 core 的 Shape 实现。
 */
public interface IShape {
    /**
     * 图形唯一标识。
     */
    String getId();

    /**
     * 图形是否已被标记删除。
     */
    boolean isDeleted();

    /**
     * 判断点是否命中图形。
     */
    boolean containsPoint(Vec2d point, double tolerance);

    /**
     * 获取图形端点（如线段首尾点、可延伸端点）。
     */
    List<Vec2d> getEndpoints();

    /**
     * 获取指定点处切线方向（用于延伸/捕捉）。
     */
    Vec2d getTangentAt(Vec2d point);
}
