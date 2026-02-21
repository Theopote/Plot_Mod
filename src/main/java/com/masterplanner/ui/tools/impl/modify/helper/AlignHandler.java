package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.ui.tools.impl.modify.constants.AlignConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对齐操作处理器
 * 
 * <p>专门处理图形对齐操作的逻辑，包括：</p>
 * <ul>
 *   <li>对齐模式计算（左对齐、右对齐、中心对齐等）</li>
 *   <li>参考对象确定（选择边界、第一个选中、最大图形等）</li>
 *   <li>分布计算（水平分布、垂直分布）</li>
 *   <li>预览图形生成</li>
 *   <li>对齐命令创建</li>
 *   <li>配置管理（单例模式）</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 对齐操作处理器
 */
public class AlignHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignHandler.class);
    
    // 单例实例
    private static final AtomicReference<AlignHandler> INSTANCE = new AtomicReference<>();
    
    // 缓存管理
    private final Map<Shape, BoundingBox> boundsCache = new ConcurrentHashMap<>();
    private final Map<Shape, Double> areaCache = new ConcurrentHashMap<>();

    // 配置字段
    private String alignMode = "LEFT";
    private final String referenceMode = "SELECTION_BOUNDS";
    private double distributeSpacing = AlignConstants.DEFAULT_DISTRIBUTE_SPACING;
    private boolean enhancedSnap = true;

    /**
     * 私有构造函数，防止外部实例化
     */
    private AlignHandler() {
        LOGGER.debug("AlignHandler 单例已创建");
    }
    
    /**
     * 获取单例实例
     * @return 对齐处理器实例
     */
    public static AlignHandler getInstance() {
        AlignHandler instance = INSTANCE.get();
        if (instance == null) {
            instance = new AlignHandler();
            if (!INSTANCE.compareAndSet(null, instance)) {
                instance = INSTANCE.get();
            }
        }
        return instance;
    }
    
    /**
     * 验证配置有效性
     * @return 配置是否有效
     */
    public boolean isValid() {
        return true;
    }

    @Override
    public ModifyType getModifyType() {
        return ModifyType.MOVE; // 对齐本质上是移动操作
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要对齐的图形");
        }
        
        // 检查最小选择数量
        int minCount = getMinimumSelectionCount(parameters);
        if (shapes.size() < minCount) {
            return ValidationResult.invalid(String.format("至少需要选择 %d 个图形", minCount));
        }
        
        // 检查必需参数
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation = concreteParams.validateRequired(AlignConstants.ALIGN_MODE, AlignConstants.REFERENCE_MODE);
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 首先验证参数
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            LOGGER.warn("对齐参数无效: {}", validation.getErrorMessage());
            return new ArrayList<>(shapes); // 返回原图形
        }
        
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            LOGGER.warn("参数类型不正确，返回原图形");
            return new ArrayList<>(shapes);
        }
        
        String alignMode = concreteParams.getString(AlignConstants.ALIGN_MODE, "LEFT");
        String referenceMode = concreteParams.getString(AlignConstants.REFERENCE_MODE, "SELECTION_BOUNDS");
        double distributeSpacing = concreteParams.getDouble(AlignConstants.DISTRIBUTE_SPACING, AlignConstants.DEFAULT_DISTRIBUTE_SPACING);
        
        List<Shape> modifiedShapes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // 确定参考对象
            Shape referenceShape = determineReferenceShape(shapes, referenceMode);
            BoundingBox referenceBounds = calculateReferenceBounds(shapes, referenceShape, referenceMode);
            
            // 根据对齐模式计算新位置
            for (Shape shape : shapes) {
                try {
                    if (shape == null) {
                        LOGGER.warn("跳过空图形");
                        continue;
                    }
                    
                    Vec2d currentPos = shape.getPosition();
                    Vec2d newPosition = calculateAlignPosition(shape, referenceBounds, alignMode, distributeSpacing, shapes);
                    
                    if (newPosition != null && !newPosition.equals(currentPos)) {
                        // 只有在位置发生变化时才克隆
                        Shape modifiedShape = shape.clone();
                        if (modifiedShape == null) {
                            LOGGER.error("克隆图形失败: {}", shape.getId());
                            errors.add("克隆图形失败: " + shape.getId());
                            continue;
                        }
                        modifiedShape.setPosition(newPosition);
                        modifiedShapes.add(modifiedShape);
                    } else {
                        // 位置未变化，直接使用原图形
                        modifiedShapes.add(shape);
                    }
                } catch (Exception e) {
                    if (shape != null) {
                        LOGGER.error("对齐图形 '{}' 失败: {}", shape.getId(), e.getMessage(), e);
                    }
                    if (shape != null) {
                        errors.add("对齐图形失败: " + shape.getId() + " - " + e.getMessage());
                    }
                }
            }
            
            if (!errors.isEmpty()) {
                LOGGER.warn("对齐操作中有 {} 个错误: {}", errors.size(), errors);
            }
            
        } catch (Exception e) {
            LOGGER.error("对齐操作失败: {}", e.getMessage(), e);
            return new ArrayList<>(shapes); // 返回原图形
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 设置预览样式
        for (Shape shape : modifiedShapes) {
            shape.setStyle(ShapeStyle.PREVIEW);
            
            // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
            shape.setSelected(false);
            shape.setHighlighted(false);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        try {
            // 使用通用 ModifyCommand 执行对齐产生的形状替换
            LOGGER.debug("创建对齐命令，原始图形: {}, 修改后图形: {}", 
                originalShapes.size(), modifiedShapes.size());
            
            return new com.masterplanner.core.command.commands.ModifyCommand(
                originalShapes,
                modifiedShapes,
                com.masterplanner.core.state.AppState.getInstance(),
                "对齐"
            );
        } catch (Exception e) {
            LOGGER.error("创建对齐命令失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                         IModifyHandler.ModifyConstraints constraints) {
        if (!(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }
        
        // 应用网格吸附约束
        if (constraints.isConstraintEnabled("snapToGrid")) {
            applyGridSnapConstraint(concreteParams);
        }
        
        // 应用增强吸附约束
        if (constraints.isConstraintEnabled("enhancedSnap")) {
            applyEnhancedSnapConstraint(concreteParams);
        }
        
        return concreteParams;
    }
    
    /**
     * 确定参考对象
     */
    private Shape determineReferenceShape(List<Shape> shapes, String referenceMode) {
        return switch (referenceMode) {
            case "LAST_SELECTED" -> shapes.getLast();
            case "LARGEST" -> findLargestShape(shapes);
            case "SELECTION_BOUNDS" -> null; // 使用边界，不需要参考对象
            default -> shapes.getFirst();
        };
    }
    
    /**
     * 查找最大的图形
     */
    private Shape findLargestShape(List<Shape> shapes) {
        Shape largest = null;
        double maxArea = 0;
        
        for (Shape shape : shapes) {
            try {
                double area = calculateShapeArea(shape);
                if (area > maxArea) {
                    maxArea = area;
                    largest = shape;
                }
            } catch (Exception e) {
                LOGGER.warn("计算图形面积失败: {}", e.getMessage());
            }
        }
        
        return largest != null ? largest : shapes.getFirst();
    }
    
    /**
     * 获取缓存的边界框
     */
    private BoundingBox getCachedBounds(Shape shape) {
        return boundsCache.computeIfAbsent(shape, s -> {
            try {
                BoundingBox bounds = s.getBoundingBox();
                if (bounds == null) {
                    LOGGER.warn("图形边界框为空: {}", s.getId());
                    return new BoundingBox(0, 0, 0, 0);
                }
                return bounds;
            } catch (Exception e) {
                LOGGER.warn("获取图形边界框失败: {}", e.getMessage());
                return new BoundingBox(0, 0, 0, 0);
            }
        });
    }
    
    /**
     * 计算图形面积（带缓存）
     */
    private double calculateShapeArea(Shape shape) {
        return areaCache.computeIfAbsent(shape, s -> {
            try {
                BoundingBox bounds = getCachedBounds(s);
                return bounds.getWidth() * bounds.getHeight();
            } catch (Exception e) {
                LOGGER.warn("计算图形面积失败: {}", e.getMessage());
                return 0.0;
            }
        });
    }
    
    /**
     * 计算参考边界
     */
    private BoundingBox calculateReferenceBounds(List<Shape> shapes, Shape referenceShape, String referenceMode) {
        if ("SELECTION_BOUNDS".equals(referenceMode)) {
            // 计算所有选中图形的边界
            return calculateSelectionBounds(shapes);
        } else if (referenceShape != null) {
            // 使用参考图形的边界
            return referenceShape.getBoundingBox();
        } else {
            // 回退到选择边界
            return calculateSelectionBounds(shapes);
        }
    }
    
    /**
     * 计算选择边界
     */
    private BoundingBox calculateSelectionBounds(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0);
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Shape shape : shapes) {
            try {
                BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    minX = Math.min(minX, bounds.getMinX());
                    minY = Math.min(minY, bounds.getMinY());
                    maxX = Math.max(maxX, bounds.getMaxX());
                    maxY = Math.max(maxY, bounds.getMaxY());
                }
            } catch (Exception e) {
                LOGGER.warn("获取图形边界框失败: {}", e.getMessage());
            }
        }
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 计算对齐位置
     */
    private Vec2d calculateAlignPosition(Shape shape, BoundingBox referenceBounds, 
                                       String alignMode, double distributeSpacing, List<Shape> allShapes) {
        try {
            if (shape == null) {
                LOGGER.warn("图形为空，无法计算对齐位置");
                return new Vec2d(0, 0);
            }
            
            Vec2d currentPos = shape.getPosition();
            if (currentPos == null) {
                LOGGER.warn("图形位置为空，无法计算对齐位置");
                return new Vec2d(0, 0);
            }
            
            // 使用策略模式计算新位置
            AlignPositionStrategy strategy = AlignPositionStrategies.getStrategy(alignMode);
            return strategy.calculate(shape, referenceBounds, currentPos, allShapes, distributeSpacing);
            
        } catch (Exception e) {
            LOGGER.error("计算对齐位置失败: {}", e.getMessage(), e);
            return shape.getPosition();
        }
    }
    

    
    /**
     * 获取最小选择数量
     */
    private int getMinimumSelectionCount(IModifyHandler.ModifyParameters parameters) {
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            String alignMode = concreteParams.getString(AlignConstants.ALIGN_MODE, "LEFT");
            return switch (alignMode) {
                case "DISTRIBUTE_H", "DISTRIBUTE_V" -> AlignConstants.MIN_SELECTION_FOR_DISTRIBUTE;
                default -> AlignConstants.MIN_SELECTION_FOR_ALIGN;
            };
        }
        return AlignConstants.MIN_SELECTION_FOR_ALIGN;
    }
    
    /**
     * 应用网格吸附约束
     */
    private void applyGridSnapConstraint(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters parameters) {
        // 实现网格吸附逻辑
        LOGGER.debug("应用网格吸附约束");
    }
    
    /**
     * 应用增强吸附约束
     */
    private void applyEnhancedSnapConstraint(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters parameters) {
        // 实现增强吸附逻辑
        LOGGER.debug("应用增强吸附约束");
    }
    
    // ====== 渲染数据计算方法 ======
    
    /**
     * 计算对齐辅助线
     * 
     * @param shapes 选中的图形列表
     * @param alignMode 对齐模式
     * @param referenceMode 参考模式
     * @return 辅助线列表，每个元素包含起点和终点
     */
    public List<AlignmentGuide> calculateAlignmentGuides(List<Shape> shapes, String alignMode, String referenceMode) {
        if (shapes.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<AlignmentGuide> guides = new ArrayList<>();
        
        try {
            // 确定参考对象和边界
            Shape referenceShape = determineReferenceShape(shapes, referenceMode);
            BoundingBox referenceBounds = calculateReferenceBounds(shapes, referenceShape, referenceMode);
            
            if (referenceBounds == null) {
                return guides;
            }
            
            // 根据对齐模式生成辅助线
            switch (alignMode) {
                case "LEFT" -> {
                    // 左对齐：绘制垂直线
                    double x = referenceBounds.getMinX();
                    guides.add(new AlignmentGuide(
                        new Vec2d(x, referenceBounds.getMinY() - 10),
                        new Vec2d(x, referenceBounds.getMaxY() + 10),
                        "LEFT"
                    ));
                }
                case "RIGHT" -> {
                    // 右对齐：绘制垂直线
                    double x = referenceBounds.getMaxX();
                    guides.add(new AlignmentGuide(
                        new Vec2d(x, referenceBounds.getMinY() - 10),
                        new Vec2d(x, referenceBounds.getMaxY() + 10),
                        "RIGHT"
                    ));
                }
                case "CENTER" -> {
                    // 中心对齐：绘制垂直线
                    double x = referenceBounds.getCenter().x;
                    guides.add(new AlignmentGuide(
                        new Vec2d(x, referenceBounds.getMinY() - 10),
                        new Vec2d(x, referenceBounds.getMaxY() + 10),
                        "CENTER"
                    ));
                }
                case "TOP" -> {
                    // 顶部对齐：绘制水平线
                    double y = referenceBounds.getMinY();
                    guides.add(new AlignmentGuide(
                        new Vec2d(referenceBounds.getMinX() - 10, y),
                        new Vec2d(referenceBounds.getMaxX() + 10, y),
                        "TOP"
                    ));
                }
                case "BOTTOM" -> {
                    // 底部对齐：绘制水平线
                    double y = referenceBounds.getMaxY();
                    guides.add(new AlignmentGuide(
                        new Vec2d(referenceBounds.getMinX() - 10, y),
                        new Vec2d(referenceBounds.getMaxX() + 10, y),
                        "BOTTOM"
                    ));
                }
                case "MIDDLE" -> {
                    // 中间对齐：绘制水平线
                    double y = referenceBounds.getCenter().y;
                    guides.add(new AlignmentGuide(
                        new Vec2d(referenceBounds.getMinX() - 10, y),
                        new Vec2d(referenceBounds.getMaxX() + 10, y),
                        "MIDDLE"
                    ));
                }
                case "DISTRIBUTE_H", "DISTRIBUTE_V" -> // 分布模式：生成多条分布线
                        guides.addAll(calculateDistributionGuides(shapes, alignMode, referenceBounds));
            }
            
        } catch (Exception e) {
            LOGGER.error("计算对齐辅助线失败: {}", e.getMessage(), e);
        }
        
        return guides;
    }
    
    /**
     * 计算分布辅助线
     */
    private List<AlignmentGuide> calculateDistributionGuides(List<Shape> shapes, String alignMode, BoundingBox referenceBounds) {
        List<AlignmentGuide> guides = new ArrayList<>();
        
        if (shapes.size() < 3) {
            return guides;
        }
        
        try {
            // 计算分布位置
            List<Double> positions = calculateDistributionPositions(shapes, alignMode);
            
            // 生成分布线
            for (Double pos : positions) {
                if ("DISTRIBUTE_H".equals(alignMode)) {
                    // 水平分布：绘制垂直线
                    guides.add(new AlignmentGuide(
                        new Vec2d(pos, referenceBounds.getMinY() - 10),
                        new Vec2d(pos, referenceBounds.getMaxY() + 10),
                        "DISTRIBUTE_H"
                    ));
                } else {
                    // 垂直分布：绘制水平线
                    guides.add(new AlignmentGuide(
                        new Vec2d(referenceBounds.getMinX() - 10, pos),
                        new Vec2d(referenceBounds.getMaxX() + 10, pos),
                        "DISTRIBUTE_V"
                    ));
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("计算分布辅助线失败: {}", e.getMessage(), e);
        }
        
        return guides;
    }
    
    /**
     * 计算分布位置
     */
    public List<Double> calculateDistributionPositions(List<Shape> shapes, String alignMode) {
        List<Double> positions = new ArrayList<>();
        
        if (shapes.size() < 3) {
            return positions;
        }
        
        try {
            List<Shape> sortedShapes = new ArrayList<>(shapes);
            
            if ("DISTRIBUTE_H".equals(alignMode)) {
                // 水平分布：按X坐标排序
                sortedShapes.sort((a, b) -> {
                    var boundsA = a.getBoundingBox();
                    var boundsB = b.getBoundingBox();
                    if (boundsA == null || boundsB == null) return 0;
                    return Double.compare(boundsA.getCenter().x, boundsB.getCenter().x);
                });
                
                // 计算分布位置
                double firstX = sortedShapes.getFirst().getBoundingBox().getCenter().x;
                double lastX = sortedShapes.getLast().getBoundingBox().getCenter().x;
                double spacing = (lastX - firstX) / (sortedShapes.size() - 1);
                
                for (int i = 0; i < sortedShapes.size(); i++) {
                    positions.add(firstX + i * spacing);
                }
            } else {
                // 垂直分布：按Y坐标排序
                sortedShapes.sort((a, b) -> {
                    var boundsA = a.getBoundingBox();
                    var boundsB = b.getBoundingBox();
                    if (boundsA == null || boundsB == null) return 0;
                    return Double.compare(boundsA.getCenter().y, boundsB.getCenter().y);
                });
                
                // 计算分布位置
                double firstY = sortedShapes.getFirst().getBoundingBox().getCenter().y;
                double lastY = sortedShapes.getLast().getBoundingBox().getCenter().y;
                double spacing = (lastY - firstY) / (sortedShapes.size() - 1);
                
                for (int i = 0; i < sortedShapes.size(); i++) {
                    positions.add(firstY + i * spacing);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("计算分布位置失败: {}", e.getMessage(), e);
        }
        
        return positions;
    }
    
    /**
     * 计算参考点信息
     * 
     * @param shapes 选中的图形列表
     * @param referenceMode 参考模式
     * @return 参考点信息
     */
    public ReferencePointInfo calculateReferencePointInfo(List<Shape> shapes, String referenceMode) {
        if (shapes.isEmpty()) {
            return null;
        }
        
        try {
            Shape referenceShape = determineReferenceShape(shapes, referenceMode);
            if (referenceShape == null) {
                return null;
            }
            
            BoundingBox bounds = referenceShape.getBoundingBox();
            if (bounds == null) {
                return null;
            }
            
            return new ReferencePointInfo(referenceShape, bounds, referenceMode);
            
        } catch (Exception e) {
            LOGGER.error("计算参考点信息失败: {}", e.getMessage(), e);
            return null;
        }
    }

} 