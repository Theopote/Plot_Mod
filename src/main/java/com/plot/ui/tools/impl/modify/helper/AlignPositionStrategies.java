package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 对齐位置计算策略实现类
 * 
 * <p>提供所有对齐模式的具体实现：</p>
 * <ul>
 *   <li>基础对齐策略（左对齐、右对齐、中心对齐等）</li>
 *   <li>分布对齐策略（水平分布、垂直分布）</li>
 *   <li>缓存优化的计算逻辑</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 对齐位置计算策略实现
 */
public class AlignPositionStrategies {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignPositionStrategies.class);
    
    /**
     * 左对齐策略
     */
    public static class LeftAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double offsetX = referenceBounds.getMinX() - shapeBounds.getMinX();
                return new Vec2d(currentPos.x + offsetX, currentPos.y);
            } catch (Exception e) {
                LOGGER.error("左对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "左对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形左边缘对齐到参考边界";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "LEFT".equals(alignMode);
        }
    }
    
    /**
     * 右对齐策略
     */
    public static class RightAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double offsetX = referenceBounds.getMaxX() - shapeBounds.getMaxX();
                return new Vec2d(currentPos.x + offsetX, currentPos.y);
            } catch (Exception e) {
                LOGGER.error("右对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "右对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形右边缘对齐到参考边界";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "RIGHT".equals(alignMode);
        }
    }
    
    /**
     * 中心对齐策略
     */
    public static class CenterAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double referenceCenterX = (referenceBounds.getMinX() + referenceBounds.getMaxX()) / 2;
                double shapeCenterX = (shapeBounds.getMinX() + shapeBounds.getMaxX()) / 2;
                double offsetX = referenceCenterX - shapeCenterX;
                return new Vec2d(currentPos.x + offsetX, currentPos.y);
            } catch (Exception e) {
                LOGGER.error("中心对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "中心对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形水平中心对齐到参考中心";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "CENTER".equals(alignMode);
        }
    }
    
    /**
     * 顶部对齐策略
     */
    public static class TopAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double offsetY = referenceBounds.getMaxY() - shapeBounds.getMaxY();
                return new Vec2d(currentPos.x, currentPos.y + offsetY);
            } catch (Exception e) {
                LOGGER.error("顶部对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "顶部对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形顶部边缘对齐到参考边界";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "TOP".equals(alignMode);
        }
    }
    
    /**
     * 底部对齐策略
     */
    public static class BottomAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double offsetY = referenceBounds.getMinY() - shapeBounds.getMinY();
                return new Vec2d(currentPos.x, currentPos.y + offsetY);
            } catch (Exception e) {
                LOGGER.error("底部对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "底部对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形底部边缘对齐到参考边界";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "BOTTOM".equals(alignMode);
        }
    }
    
    /**
     * 中间对齐策略
     */
    public static class MiddleAlignStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    LOGGER.warn("图形边界框为空，返回当前位置");
                    return currentPos;
                }
                
                double referenceCenterY = (referenceBounds.getMinY() + referenceBounds.getMaxY()) / 2;
                double shapeCenterY = (shapeBounds.getMinY() + shapeBounds.getMaxY()) / 2;
                double offsetY = referenceCenterY - shapeCenterY;
                return new Vec2d(currentPos.x, currentPos.y + offsetY);
            } catch (Exception e) {
                LOGGER.error("中间对齐计算失败: {}", e.getMessage());
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "中间对齐";
        }
        
        @Override
        public String getStrategyDescription() {
            return "将图形垂直中心对齐到参考中心";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "MIDDLE".equals(alignMode);
        }
    }
    
    /**
     * 水平分布策略
     */
    public static class HorizontalDistributeStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                if (allShapes == null || allShapes.size() < 2) { // 分布至少需要2个图形
                    return currentPos;
                }

                // 1. 按X坐标中心排序
                List<Shape> sortedShapes = new ArrayList<>(allShapes);
                sortedShapes.sort(Comparator.comparingDouble(s -> s.getBoundingBox().getCenter().x));

                int index = sortedShapes.indexOf(shape);
                if (index == -1) return currentPos;

                // 2. 获取第一个和最后一个图形的中心
                double firstCenterX = sortedShapes.getFirst().getBoundingBox().getCenter().x;
                double lastCenterX = sortedShapes.getLast().getBoundingBox().getCenter().x;

                // 3. 计算每个中心点之间的等距间隔
                if (sortedShapes.size() <= 1) return currentPos; // 避免除零
                double gap = (lastCenterX - firstCenterX) / (sortedShapes.size() - 1);

                // 4. 计算当前图形的目标中心X坐标
                double newCenterX = firstCenterX + index * gap;

                // 5. 根据目标中心计算新的图形位置
                double oldCenterX = shape.getBoundingBox().getCenter().x;
                double offsetX = newCenterX - oldCenterX;

                return new Vec2d(currentPos.x + offsetX, currentPos.y);

            } catch (Exception e) {
                LOGGER.error("水平分布计算失败: {}", e.getMessage(), e);
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "水平分布";
        }
        
        @Override
        public String getStrategyDescription() {
            return "在水平方向均匀分布图形";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "DISTRIBUTE_H".equals(alignMode);
        }
    }
    
    /**
     * 垂直分布策略
     */
    public static class VerticalDistributeStrategy implements AlignPositionStrategy {
        @Override
        public Vec2d calculate(Shape shape, BoundingBox referenceBounds, Vec2d currentPos, 
                             List<Shape> allShapes, double spacing) {
            try {
                if (allShapes == null || allShapes.size() < 2) { // 分布至少需要2个图形
                    return currentPos;
                }

                // 1. 按Y坐标中心排序
                List<Shape> sortedShapes = new ArrayList<>(allShapes);
                sortedShapes.sort(Comparator.comparingDouble(s -> s.getBoundingBox().getCenter().y));

                int index = sortedShapes.indexOf(shape);
                if (index == -1) return currentPos;

                // 2. 获取第一个和最后一个图形的中心
                double firstCenterY = sortedShapes.getFirst().getBoundingBox().getCenter().y;
                double lastCenterY = sortedShapes.getLast().getBoundingBox().getCenter().y;

                // 3. 计算每个中心点之间的等距间隔
                if (sortedShapes.size() <= 1) return currentPos; // 避免除零
                double gap = (lastCenterY - firstCenterY) / (sortedShapes.size() - 1);

                // 4. 计算当前图形的目标中心Y坐标
                double newCenterY = firstCenterY + index * gap;

                // 5. 根据目标中心计算新的图形位置
                double oldCenterY = shape.getBoundingBox().getCenter().y;
                double offsetY = newCenterY - oldCenterY;

                return new Vec2d(currentPos.x, currentPos.y + offsetY);

            } catch (Exception e) {
                LOGGER.error("垂直分布计算失败: {}", e.getMessage(), e);
                return currentPos;
            }
        }
        
        @Override
        public String getStrategyName() {
            return "垂直分布";
        }
        
        @Override
        public String getStrategyDescription() {
            return "在垂直方向均匀分布图形";
        }
        
        @Override
        public boolean isApplicable(String alignMode) {
            return "DISTRIBUTE_V".equals(alignMode);
        }
    }
    
    /**
     * 策略工厂方法
     * @param alignMode 对齐模式
     * @return 对应的策略实例
     */
    public static AlignPositionStrategy getStrategy(String alignMode) {
        if (alignMode == null) {
            LOGGER.warn("对齐模式为空，返回左对齐策略");
            return new LeftAlignStrategy();
        }
        
        return switch (alignMode) {
            case "LEFT" -> new LeftAlignStrategy();
            case "RIGHT" -> new RightAlignStrategy();
            case "CENTER" -> new CenterAlignStrategy();
            case "TOP" -> new TopAlignStrategy();
            case "BOTTOM" -> new BottomAlignStrategy();
            case "MIDDLE" -> new MiddleAlignStrategy();
            case "DISTRIBUTE_H" -> new HorizontalDistributeStrategy();
            case "DISTRIBUTE_V" -> new VerticalDistributeStrategy();
            default -> {
                LOGGER.warn("未知的对齐模式: {}，返回左对齐策略", alignMode);
                yield new LeftAlignStrategy();
            }
        };
    }
} 