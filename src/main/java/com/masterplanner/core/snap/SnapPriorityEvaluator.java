package com.masterplanner.core.snap;

import com.masterplanner.api.geometry.Vec2d;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 吸附优先级评估器
 * 负责根据不同策略对吸附候选点进行排序
 */
public class SnapPriorityEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapPriorityEvaluator.class);

    /**
     * 吸附点类型
     */
    public enum SnapType {
        NONE,           // 无吸附
        END_POINT,      // 端点
        INTERSECTION,   // 交点
        CENTER_POINT,   // 圆心
        CENTROID,       // 中心点
        VERTEX,         // 角点
        MID_POINT,      // 中点
        QUADRANT,        // 象限点
        GRID_POINT,      // 网格点
        PERPENDICULAR,   // 垂足
        EXTENSION,       // 延长线
        CONTROL_POINT,   // 控制点
        TANGENT,         // 切点
        NEAREST_POINT,    // 最近点
        HORIZONTAL,       // 水平约束
        VERTICAL,         // 竖直约束
        PARALLEL          // 平行约束
    }

    // 类型优先级权重
    private static final Map<SnapType, Integer> TYPE_WEIGHTS = new EnumMap<>(SnapType.class);
    static {
        TYPE_WEIGHTS.put(SnapType.END_POINT, 100);       // 端点优先级最高
        TYPE_WEIGHTS.put(SnapType.INTERSECTION, 90);     // 交点次之
        TYPE_WEIGHTS.put(SnapType.CENTER_POINT, 80);     // 圆心
        TYPE_WEIGHTS.put(SnapType.CENTROID, 75);
        TYPE_WEIGHTS.put(SnapType.VERTEX, 70);
        TYPE_WEIGHTS.put(SnapType.MID_POINT, 65);        // 中点
        TYPE_WEIGHTS.put(SnapType.QUADRANT, 60);         // 象限点
        TYPE_WEIGHTS.put(SnapType.PERPENDICULAR, 50);    // 垂足
        TYPE_WEIGHTS.put(SnapType.GRID_POINT, 42);
        TYPE_WEIGHTS.put(SnapType.CONTROL_POINT, 41);
        TYPE_WEIGHTS.put(SnapType.TANGENT, 40);
        TYPE_WEIGHTS.put(SnapType.NEAREST_POINT, 35);    // 最近点优先级最低
        TYPE_WEIGHTS.put(SnapType.NONE, 0);
        TYPE_WEIGHTS.put(SnapType.HORIZONTAL, 48);
        TYPE_WEIGHTS.put(SnapType.VERTICAL, 47);
        TYPE_WEIGHTS.put(SnapType.PARALLEL, 46);
        TYPE_WEIGHTS.put(SnapType.EXTENSION, 45);
    }

    private final boolean isTypeFirst;  // true = 类型优先, false = 距离优先

    public SnapPriorityEvaluator(boolean isTypeFirst) {
        this.isTypeFirst = isTypeFirst;
    }

    /**
     * 评估并排序吸附候选点
     * @param candidates 候选点列表
     * @throws IllegalArgumentException 如果候选点列表为 null
     */
    public void evaluateAndSort(List<SnapCandidate> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("候选点列表不能为 null");
        }

        if (candidates.isEmpty()) {
            return;  // 空列表无需排序
        }

        try {
            if (isTypeFirst) {
                // 类型优先策略：先按类型权重排序，同类型内按距离排序
                candidates.sort((a, b) -> {
                    if (a == null || b == null) {
                        throw new IllegalArgumentException("候选点不能为 null");
                    }
                    int typeCompare = Integer.compare(
                            TYPE_WEIGHTS.getOrDefault(b.type, 0),
                            TYPE_WEIGHTS.getOrDefault(a.type, 0)
                    );
                    if (typeCompare == 0) {
                        int priorityCompare = Double.compare(b.priority, a.priority);
                        if (priorityCompare == 0) {
                            int distCompare = Double.compare(a.distance, b.distance);
                            return distCompare != 0 ? distCompare : Integer.compare(a.index, b.index);
                        }
                        return priorityCompare;
                    }
                    return typeCompare;
                });
            } else {
                // 距离优先策略：先按距离排序，相同距离内按类型权重排序
                candidates.sort((a, b) -> {
                    if (a == null || b == null) {
                        throw new IllegalArgumentException("候选点不能为 null");
                    }
                    int distCompare = Double.compare(a.distance, b.distance);
                    if (distCompare == 0) {
                        int typeCompare = Integer.compare(
                                TYPE_WEIGHTS.getOrDefault(b.type, 0),
                                TYPE_WEIGHTS.getOrDefault(a.type, 0)
                        );
                        return typeCompare != 0 ? typeCompare : Integer.compare(a.index, b.index);
                    }
                    return distCompare;
                });
            }
        } catch (Exception e) {
            LOGGER.error("排序候选点时发生错误", e);
            // 保持原有顺序
        }
    }

    /**
     * 吸附候选点
     */
    public static class SnapCandidate {
        public final Vec2d position;
        public final SnapType type;
        public final double distance;
        public final double priority;  // 上下文优先级 (0-1)：
        // 1.0 = 当前选中/活动对象的特征点
        // 0.8 = 当前图层的特征点
        // 0.5 = 其他可见图层的特征点
        // 0.0 = 默认优先级
        public final int index;  // 添加索引确保排序稳定性

        /**
         * 创建吸附候选点
         * @param position 位置
         * @param type 吸附类型
         * @param distance 到目标点的距离
         * @param priority 上下文优先级 (0-1)
         * @param index 候选点索引
         */
        public SnapCandidate(Vec2d position, SnapType type, double distance, double priority, int index) {
            this.position = position;
            this.type = type;
            this.distance = distance;
            // 确保优先级在有效范围内
            this.priority = Math.max(0.0, Math.min(1.0, priority));
            this.index = index;
        }

        /**
         * 创建默认优先级的候选点
         */
        public static SnapCandidate create(Vec2d position, SnapType type, double distance, int index) {
            return new SnapCandidate(position, type, distance, 0.0, index);
        }

        /**
         * 创建选中对象的候选点
         */
        public static SnapCandidate createFromSelected(Vec2d position, SnapType type, double distance, int index) {
            return new SnapCandidate(position, type, distance, 1.0, index);
        }

        /**
         * 创建当前图层的候选点
         */
        public static SnapCandidate createFromCurrentLayer(Vec2d position, SnapType type, double distance, int index) {
            return new SnapCandidate(position, type, distance, 0.8, index);
        }

        public Vec2d getPoint() {
            return position;
        }

        public SnapType getType() {
            return type;
        }
    }
} 