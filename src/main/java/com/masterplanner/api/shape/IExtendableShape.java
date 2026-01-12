package com.masterplanner.api.shape;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;

/**
 * 可延伸图形接口
 * 
 * <p>定义支持延伸操作的图形必须实现的接口。这个接口提供了类型安全的延伸操作，
 * 避免了使用反射检查图形是否支持延伸，提高了性能和代码可读性。</p>
 * 
 * <p><strong>设计优势：</strong></p>
 * <ul>
 *   <li><strong>类型安全</strong>：编译时检查，避免运行时错误</li>
 *   <li><strong>性能优化</strong>：避免反射调用，提高执行效率</li>
 *   <li><strong>设计清晰</strong>：明确表达"可延伸"这一概念</li>
 *   <li><strong>易于维护</strong>：接口变更时编译器会提示所有实现类</li>
 * </ul>
 * 
 * <p><strong>实现要求：</strong></p>
 * <ul>
 *   <li>所有支持延伸的图形类都应实现此接口</li>
 *   <li>extend方法应该返回延伸后的新图形，不修改原图形</li>
 *   <li>如果延伸失败，应返回null或抛出适当的异常</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 初始版本
 * @since 1.21.4
 */
public interface IExtendableShape {
    
    /**
     * 延伸图形到指定目标点
     * 
     * <p>将图形从指定的延伸点延伸到目标点。具体的延伸行为取决于图形类型：</p>
     * <ul>
     *   <li><strong>直线</strong>：延长直线到目标点</li>
     *   <li><strong>多段线</strong>：延长首段或末段到目标点</li>
     *   <li><strong>圆弧</strong>：沿切线方向延伸圆弧到目标点</li>
     * </ul>
     * 
     * @param extendPoint 延伸起始点（图形端点）
     * @param targetPoint 延伸目标点
     * @return 延伸后的新图形，如果延伸失败则返回null
     * @throws IllegalArgumentException 如果参数无效
     * @throws UnsupportedOperationException 如果图形类型不支持延伸
     */
    Shape extend(Vec2d extendPoint, Vec2d targetPoint);
    
    /**
     * 检查图形是否支持从指定点延伸
     * 
     * <p>验证延伸点是否为图形的有效端点，以及是否可以执行延伸操作。</p>
     * 
     * @param extendPoint 要检查的延伸点
     * @param tolerance 端点检测容差
     * @return true如果支持延伸，false否则
     */
    default boolean canExtendFrom(Vec2d extendPoint, double tolerance) {
        if (extendPoint == null || tolerance <= 0) {
            return false;
        }
        
        // 获取图形的端点
        java.util.List<Vec2d> endpoints = ((Shape) this).getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            return false;
        }
        
        // 检查延伸点是否在图形端点附近
        return endpoints.stream()
                .anyMatch(endpoint -> extendPoint.distance(endpoint) <= tolerance);
    }
    
    /**
     * 获取图形支持延伸的方向
     * 
     * <p>返回从指定端点延伸的方向向量。方向向量应该是单位向量。</p>
     * 
     * @param extendPoint 延伸点（图形端点）
     * @return 延伸方向向量，如果无法确定则返回null
     */
    default Vec2d getExtendDirection(Vec2d extendPoint) {
        if (extendPoint == null) {
            return null;
        }
        
        // 默认实现：尝试使用图形的切线方向
        return ((Shape) this).getTangentAt(extendPoint);
    }
}
