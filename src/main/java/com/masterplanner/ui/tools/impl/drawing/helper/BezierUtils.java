package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 贝塞尔曲线工具类
 * <p>
 * 提供贝塞尔曲线相关的数据转换、验证和计算功能。
 * <p>
 * 修复版本：数据格式与BezierCurveShape完全匹配，职责清晰分离
 */
public class BezierUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(BezierUtils.class);
    
    /**
     * 贝塞尔曲线数据容器
     * 修复：控制点类型改为List<Vec2d[]>，与BezierCurveShape构造函数匹配
     */
    public static class BezierData {
        private final List<Vec2d> anchors;
        private final List<Vec2d[]> controls;
        private final boolean shouldClose;
        
        public BezierData(List<Vec2d> anchors, List<Vec2d[]> controls, boolean shouldClose) {
            this.anchors = anchors;
            this.controls = controls;
            this.shouldClose = shouldClose;
        }
        
        public List<Vec2d> getAnchors() { return anchors; }
        public List<Vec2d[]> getControls() { return controls; }
        public boolean shouldClose() { return shouldClose; }
    }
    
    /**
     * 将点列表转换为贝塞尔曲线数据
     * 修复：直接返回List<Vec2d[]>格式的控制点，与BezierCurveShape构造函数匹配
     * 
     * @param points 点列表（格式：锚点1, 控制点1, 控制点2, 锚点2, ...）
     * @param shouldClose 是否闭合路径（注意：仅作为标志，不处理实际闭合逻辑）
     * @return 贝塞尔曲线数据
     */
    public static BezierData convertPointsToCurveData(List<Vec2d> points, boolean shouldClose) {
        List<Vec2d> anchors = new ArrayList<>();
        List<Vec2d[]> controls = new ArrayList<>();
        
        if (points.size() < 2) {
            LOGGER.warn("点数不足，无法创建贝塞尔曲线: {}", points.size());
            return new BezierData(anchors, controls, shouldClose);
        }
        
        // 检查点数是否符合贝塞尔曲线格式
        // 对于n个锚点，应该有(n-1)*2个控制点
        // 总点数应该是 1 + (n-1)*3 = 3n-2
        int totalPoints = points.size();
        int anchorCount = (totalPoints + 2) / 3;
        
        LOGGER.debug("转换贝塞尔曲线数据: 总点数={}, 计算锚点数={}", totalPoints, anchorCount);
        
        // 提取锚点
        for (int i = 0; i < anchorCount; i++) {
            int anchorIndex = i * 3;
            if (anchorIndex < points.size()) {
                anchors.add(points.get(anchorIndex));
            }
        }
        
        // 提取控制点，直接构造Vec2d[]数组
        for (int i = 0; i < anchorCount - 1; i++) {
            int control1Index = i * 3 + 1;
            int control2Index = i * 3 + 2;
            
            if (control1Index < points.size() && control2Index < points.size()) {
                Vec2d[] controlPair = new Vec2d[]{
                    points.get(control1Index), 
                    points.get(control2Index)
                };
                controls.add(controlPair);
            }
        }
        
        // 修复：移除闭合逻辑处理，让BezierCurveShape自己决定如何闭合
        // 这里只负责数据转换，不处理闭合的几何逻辑
        
        LOGGER.debug("贝塞尔曲线数据转换完成: 锚点数={}, 控制点对数={}, 闭合标志={}", 
                    anchors.size(), controls.size(), shouldClose);
        
        return new BezierData(anchors, controls, shouldClose);
    }
    
    /**
     * 验证贝塞尔曲线数据的有效性
     * 修复：适配List<Vec2d[]>格式的控制点
     * 
     * @param data 贝塞尔曲线数据
     * @return 是否有效
     */
    public static boolean validateBezierData(BezierData data) {
        if (data.getAnchors().size() < 2) {
            LOGGER.warn("锚点数不足: {}", data.getAnchors().size());
            return false;
        }
        
        // 检查控制点对数量：应该等于锚点数量-1
        int expectedControlPairCount = data.getAnchors().size() - 1;
        if (data.getControls().size() != expectedControlPairCount) {
            LOGGER.warn("控制点对数量不匹配: 期望={}, 实际={}", 
                       expectedControlPairCount, data.getControls().size());
            return false;
        }
        
        // 检查每个控制点对都包含两个点
        for (int i = 0; i < data.getControls().size(); i++) {
            Vec2d[] controlPair = data.getControls().get(i);
            if (controlPair == null || controlPair.length != 2) {
                LOGGER.warn("控制点对[{}]无效: {}", i, 
                    controlPair == null ? "null" : "长度=" + controlPair.length);
                return false;
            }
            if (controlPair[0] == null || controlPair[1] == null) {
                LOGGER.warn("控制点对[{}]包含null点", i);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 计算贝塞尔曲线的近似长度
     * 修复：适配List<Vec2d[]>格式的控制点
     * 
     * @param anchors 锚点列表
     * @param controls 控制点对列表
     * @return 近似长度
     */
    public static double calculateApproximateLength(List<Vec2d> anchors, List<Vec2d[]> controls) {
        if (anchors.size() < 2) {
            return 0.0;
        }
        
        double totalLength = 0.0;
        
        for (int i = 0; i < anchors.size() - 1; i++) {
            Vec2d anchor1 = anchors.get(i);
            Vec2d anchor2 = anchors.get(i + 1);
            
            if (i < controls.size() && controls.get(i) != null && controls.get(i).length == 2) {
                Vec2d control1 = controls.get(i)[0];
                Vec2d control2 = controls.get(i)[1];
                
                // 使用德卡斯特罗算法计算贝塞尔曲线长度
                totalLength += calculateBezierSegmentLength(anchor1, control1, control2, anchor2);
            } else {
                // 直线段
                totalLength += anchor1.distance(anchor2);
            }
        }
        
        return totalLength;
    }
    
    /**
     * 计算单个贝塞尔曲线段的近似长度
     * 
     * @param p0 起始点
     * @param p1 控制点1
     * @param p2 控制点2
     * @param p3 结束点
     * @return 近似长度
     */
    private static double calculateBezierSegmentLength(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3) {
        // 使用简单的线性近似
        double length = 0.0;
        int segments = 10; // 分段数
        
        for (int i = 0; i < segments; i++) {
            double t1 = (double) i / segments;
            double t2 = (double) (i + 1) / segments;
            
            Vec2d point1 = evaluateBezierPoint(p0, p1, p2, p3, t1);
            Vec2d point2 = evaluateBezierPoint(p0, p1, p2, p3, t2);
            
            length += point1.distance(point2);
        }
        
        return length;
    }
    
    /**
     * 计算贝塞尔曲线在参数t处的点
     * 
     * @param p0 起始点
     * @param p1 控制点1
     * @param p2 控制点2
     * @param p3 结束点
     * @param t 参数 (0-1)
     * @return 曲线上的点
     */
    private static Vec2d evaluateBezierPoint(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, double t) {
        double oneMinusT = 1.0 - t;
        double oneMinusT2 = oneMinusT * oneMinusT;
        double oneMinusT3 = oneMinusT2 * oneMinusT;
        double t2 = t * t;
        double t3 = t2 * t;
        
        double x = oneMinusT3 * p0.x + 3 * oneMinusT2 * t * p1.x + 
                   3 * oneMinusT * t2 * p2.x + t3 * p3.x;
        double y = oneMinusT3 * p0.y + 3 * oneMinusT2 * t * p1.y + 
                   3 * oneMinusT * t2 * p2.y + t3 * p3.y;
        
        return new Vec2d(x, y);
    }
    
    /**
     * 将控制点列表转换为Vec2d[]数组格式
     * @deprecated 不再需要此方法，BezierData现在直接提供List<Vec2d[]>格式
     * 保留此方法仅为向后兼容，建议使用convertPointsToCurveData()
     * 
     * @param controls 控制点列表
     * @return Vec2d[]数组列表
     */
    @Deprecated
    public static List<Vec2d[]> convertControlsToArrays(List<Vec2d> controls) {
        List<Vec2d[]> controlArrays = new ArrayList<>();
        
        // 每两个控制点组成一个Vec2d[]
        for (int i = 0; i < controls.size(); i += 2) {
            if (i + 1 < controls.size()) {
                Vec2d[] controlPair = new Vec2d[2];
                controlPair[0] = controls.get(i);
                controlPair[1] = controls.get(i + 1);
                controlArrays.add(controlPair);
            }
        }
        
        return controlArrays;
    }
    
    /**
     * 计算绘制贝塞尔曲线所需的步数
     * 
     * @param p0 起点
     * @param p1 第一个控制点
     * @param p2 第二个控制点
     * @param p3 终点
     * @param minSteps 最小步数
     * @param maxSteps 最大步数
     * @return 推荐的步数
     */
    public static int calculateOptimalSteps(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, 
                                          int minSteps, int maxSteps) {
        double approxLen = approximateBezierLength(p0, p1, p2, p3);
        return (int) Math.max(minSteps, Math.min(maxSteps, approxLen / 2));
    }
    
    /**
     * 计算三次贝塞尔曲线上的点
     * 
     * @param p0 起点
     * @param p1 第一个控制点
     * @param p2 第二个控制点
     * @param p3 终点
     * @param t 参数 (0.0 到 1.0)
     * @return 曲线上的点
     */
    public static Vec2d evaluateCubicBezier(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, double t) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;

        return p0.multiply(uuu)
                .add(p1.multiply(3 * uu * t))
                .add(p2.multiply(3 * u * tt))
                .add(p3.multiply(ttt));
    }
    
    /**
     * 计算贝塞尔曲线的近似长度
     * 
     * @param p0 起点
     * @param p1 第一个控制点
     * @param p2 第二个控制点
     * @param p3 终点
     * @return 近似长度
     */
    public static double approximateBezierLength(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3) {
        // 使用控制多边形的长度作为近似值
        return p0.distance(p1) + p1.distance(p2) + p2.distance(p3);
    }
    
    /**
     * 新增：直接从BezierData创建BezierCurveShape的便利方法
     * 消除手动传递参数的需要
     * 
     * @param data 贝塞尔曲线数据
     * @return 可以直接用于构造BezierCurveShape的参数组合
     */
    public static BezierConstructorParams createConstructorParams(BezierData data) {
        return new BezierConstructorParams(data.getAnchors(), data.getControls(), data.shouldClose());
    }
    
    /**
     * 构造函数参数容器
     * 方便将BezierData转换为BezierCurveShape构造函数所需的参数
     */
    public static class BezierConstructorParams {
        private final List<Vec2d> anchors;
        private final List<Vec2d[]> controls;
        private final boolean closed;
        
        public BezierConstructorParams(List<Vec2d> anchors, List<Vec2d[]> controls, boolean closed) {
            this.anchors = anchors;
            this.controls = controls;
            this.closed = closed;
        }
        
        public List<Vec2d> getAnchors() { return anchors; }
        public List<Vec2d[]> getControls() { return controls; }
        public boolean isClosed() { return closed; }
    }
    
    /**
     * 新增：简化的贝塞尔曲线创建方法
     * 从扁平点列表直接创建BezierData，无需额外转换步骤
     * 
     * @param points 扁平点列表
     * @param closed 是否闭合
     * @return 可直接用于BezierCurveShape构造的数据
     */
    public static BezierData createFromFlatPoints(List<Vec2d> points, boolean closed) {
        return convertPointsToCurveData(points, closed);
    }
    
    /**
     * 新增：从锚点和分离的控制点创建BezierData
     * 
     * @param anchors 锚点列表
     * @param flatControls 扁平控制点列表（control1, control2, control1, control2, ...）
     * @param closed 是否闭合
     * @return 贝塞尔曲线数据
     */
    public static BezierData createFromSeparatePoints(List<Vec2d> anchors, List<Vec2d> flatControls, boolean closed) {
        List<Vec2d[]> controls = new ArrayList<>();
        
        // 将扁平控制点转换为控制点对
        for (int i = 0; i < flatControls.size(); i += 2) {
            if (i + 1 < flatControls.size()) {
                controls.add(new Vec2d[]{flatControls.get(i), flatControls.get(i + 1)});
            }
        }
        
        return new BezierData(anchors, controls, closed);
    }
}