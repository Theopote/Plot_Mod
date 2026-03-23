package com.plot.ui.tools.impl.modify.helper;

import com.plot.core.geometry.BoundingBox;
import com.plot.core.model.Shape;

/**
 * 参考点信息
 * 
 * <p>用于表示对齐操作中的参考对象信息，包含参考图形、边界和参考模式。</p>
 * 
 * @author Plot Team
 * @version 1.0
 */
public class ReferencePointInfo {
    
    private final Shape referenceShape;
    private final BoundingBox bounds;
    private final String referenceMode;
    
    /**
     * 构造函数
     * 
     * @param referenceShape 参考图形
     * @param bounds 参考边界
     * @param referenceMode 参考模式
     */
    public ReferencePointInfo(Shape referenceShape, BoundingBox bounds, String referenceMode) {
        this.referenceShape = referenceShape;
        this.bounds = bounds;
        this.referenceMode = referenceMode;
    }

    /**
     * 获取参考边界
     * 
     * @return 参考边界
     */
    public BoundingBox getBounds() {
        return bounds;
    }

    @Override
    public String toString() {
        return String.format("ReferencePointInfo{referenceShape=%s, bounds=%s, referenceMode='%s'}", 
                           referenceShape, bounds, referenceMode);
    }
}