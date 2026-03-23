package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;

/**
 * 对齐辅助线
 * 
 * <p>用于表示对齐操作中的辅助线，包含起点、终点和类型信息。</p>
 * 
 * @author Plot Team
 * @version 1.0
 */
public class AlignmentGuide {
    
    private final Vec2d start;
    private final Vec2d end;
    private final String type;
    
    /**
     * 构造函数
     * 
     * @param start 起点
     * @param end 终点
     * @param type 辅助线类型
     */
    public AlignmentGuide(Vec2d start, Vec2d end, String type) {
        this.start = start;
        this.end = end;
        this.type = type;
    }
    
    /**
     * 获取起点
     * 
     * @return 起点坐标
     */
    public Vec2d getStart() {
        return start;
    }
    
    /**
     * 获取终点
     * 
     * @return 终点坐标
     */
    public Vec2d getEnd() {
        return end;
    }
    
    /**
     * 获取辅助线类型
     * 
     * @return 类型字符串
     */
    public String getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return String.format("AlignmentGuide{start=%s, end=%s, type='%s'}", start, end, type);
    }
}