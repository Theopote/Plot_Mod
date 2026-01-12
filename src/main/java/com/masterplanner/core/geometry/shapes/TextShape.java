package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.style.TextStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.FillStyle;
import com.masterplanner.api.graphics.ITextStyle;
import com.masterplanner.api.graphics.ILineStyle;
import com.masterplanner.api.graphics.IFillStyle;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;

import com.masterplanner.core.graphics.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import com.masterplanner.core.geometry.GeometryUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.geom.PathIterator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文本形状类
 * 支持高性能缓存和精确的矢量路径转换
 * 
 * <p><strong>重要架构限制说明：</strong></p>
 * <ul>
 * <li><strong>平台依赖性：</strong>本类完全依赖于Java AWT库（Graphics2D、Font、FontMetrics、GlyphVector等），
 *     这使得它无法在非AWT环境中运行，如Android、纯服务器端环境等。</li>
 * <li><strong>几何计算限制：</strong>大部分几何查询方法（如contains、getClosestPoint、intersects等）
 *     都基于轴对齐包围盒进行简化计算，而非精确的文本轮廓，可能导致不准确的交互结果。</li>
 * <li><strong>功能完整性：</strong>部分Shape接口方法未完全实现，某些操作可能抛出UnsupportedOperationException。</li>
 * </ul>
 * 
 * <p><strong>设计权衡：</strong></p>
 * <p>本类通过深度集成AWT实现了强大的文本处理功能（如文字转矢量路径），
 * 但为此牺牲了平台独立性和部分几何计算的精确性。这是架构层面的权衡决策。</p>
 * 
 * <p><strong>建议使用场景：</strong></p>
 * <ul>
 * <li>桌面应用程序中的文本渲染和编辑</li>
 * <li>需要将文本转换为可编辑几何路径的场景</li>
 * <li>对文本渲染性能有高要求的应用</li>
 * </ul>
 * 
 * <p><strong>不建议使用场景：</strong></p>
 * <ul>
 * <li>跨平台应用程序（特别是移动端）</li>
 * <li>需要精确几何计算的场景</li>
 * <li>纯服务器端环境</li>
 * </ul>
 */
public class TextShape extends Shape {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextShape.class);
    
    // 常量定义
    private static final String DEFAULT_TEXT = "";
    private static final double DEFAULT_ROTATION = 0.0;
    private static final double TOLERANCE = 1e-10;
    
    // JSON序列化支持
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 静态Graphics2D缓存，避免重复创建重量级对象
    private static final ThreadLocal<Graphics2D> G2D_CACHE = ThreadLocal.withInitial(() -> {
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return tempImage.createGraphics();
    });

    // 核心字段
    private Vec2d position;
    private String text;
    private TextStyle textStyle;
    private double rotation;
    private double width;
    private double height;
    private String originalText; // 用于取消编辑时恢复

    // 缓存机制
    private FontMetrics cachedFontMetrics;
    private String lastText;
    private TextStyle lastTextStyle;
    private boolean boundsDirty = true;

    public TextShape(Vec2d position, String text) {
        super(validatePosition(position));
        this.position = validatePosition(position);
        this.text = text != null ? text : DEFAULT_TEXT;
        this.textStyle = new TextStyle();
        this.rotation = DEFAULT_ROTATION;
        updateBounds();
    }

    /**
     * 验证位置参数
     */
    private static Vec2d validatePosition(Vec2d position) {
        if (position == null) {
            throw new IllegalArgumentException("位置不能为空");
        }
        return position;
    }

    @Override
    public void translate(Vec2d offset) {
        if (offset == null) {
            throw new IllegalArgumentException("偏移量不能为空");
        }
        position = position.add(offset);
        boundsDirty = true;
    }

    @Override
    public void rotate(double angle, Vec2d center) {
        if (center == null) {
            throw new IllegalArgumentException("旋转中心不能为空");
        }
        // 正确的旋转：先平移到原点，旋转，再平移回原位置
        position = GeometryUtils.rotate(position.subtract(center), angle).add(center);
        rotation += angle;
        boundsDirty = true;
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换位置
        this.position = transformMatrix.transform(this.position);
        
        // 更新旋转角度
        this.rotation += transformMatrix.getRotation();
        
        // 从矩阵行列式获取平均缩放因子
        double avgScale = Math.sqrt(Math.abs(transformMatrix.getDeterminant()));
        
        // 缩放字体大小
        if (textStyle != null) {
            textStyle.setFontSize((float)(textStyle.getFontSize() * avgScale));
        }
        
        boundsDirty = true;
        return this; // 文本变换后仍然是文本
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : DEFAULT_TEXT;
        boundsDirty = true;
        updateBounds();
    }

    public Vec2d getPosition() {
        return position;
    }

    public void setPosition(Vec2d position) {
        this.position = validatePosition(position);
        boundsDirty = true;
    }

    /**
     * 获取文本样式的不可变副本，保护内部状态
     */
    public ITextStyle getTextStyle() {
        return this.textStyle.clone();
    }

    public void setTextStyle(ITextStyle style) {
        if (style != null) {
            this.textStyle = (TextStyle)style;
            boundsDirty = true;
            updateBounds();
        }
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
        boundsDirty = true;
    }

    public void saveOriginalText() {
        this.originalText = this.text;
    }

    public void restoreOriginalText() {
        if (this.originalText != null) {
            this.text = this.originalText;
            boundsDirty = true;
            updateBounds();
        }
    }

    /**
     * 优化的边界更新方法，使用缓存机制
     */
    private void updateBounds() {
        if (text == null || text.isEmpty() || textStyle == null) {
            width = 0;
            height = 0;
            cachedFontMetrics = null;
            boundsDirty = false;
            return;
        }

        // 检查缓存是否有效
        if (!boundsDirty && cachedFontMetrics != null && 
            text.equals(lastText) && textStyle.equals(lastTextStyle)) {
            return; // 使用缓存的边界
        }

        try {
            // 使用缓存的Graphics2D
            Graphics2D g2d = getGraphics2D();
            cachedFontMetrics = g2d.getFontMetrics();
            
            // 计算文本边界
            width = cachedFontMetrics.stringWidth(text);
            height = cachedFontMetrics.getHeight();
            
            // 更新缓存状态
            lastText = text;
            lastTextStyle = (TextStyle) textStyle.clone();
            boundsDirty = false;
            
        } catch (Exception e) {
            // 如果FontMetrics计算失败，使用估算方法
            width = text.length() * textStyle.getFontSize() * 0.6;
            height = textStyle.getFontSize();
            cachedFontMetrics = null;
            boundsDirty = false;
        }
    }

    /**
     * 使用缓存的Graphics2D实例，避免重复创建重量级对象
     */
    private @NotNull Graphics2D getGraphics2D() {
        Graphics2D g2d = G2D_CACHE.get(); // 从缓存中获取

        // 创建字体
        int fontStyle = Font.PLAIN;
        if (textStyle.isBold()) fontStyle |= Font.BOLD;
        if (textStyle.isItalic()) fontStyle |= Font.ITALIC;

        Font font = new Font(textStyle.getFontFamily(), fontStyle, (int)textStyle.getFontSize());
        g2d.setFont(font);
        
        // 注意：不再调用 g2d.dispose()，因为我们要复用它
        return g2d;
    }

    @Override
    public void draw(DrawContext context) {
        if (text == null || text.isEmpty()) return;

        // 应用变换
        Vec2d transformedPos = transform.transform(position);
        
        // 选中/高亮时使用统一的画布样式颜色，并在选中时加粗
        Color textColor = getColor(context);

        // 获取字体样式信息
        String fontFamily = textStyle.getFontFamily();
        float fontSize = textStyle.getFontSize();
        // 选中时加粗，提供与其它图形一致的“加粗高亮”反馈
        boolean bold = textStyle.isBold() || isSelected();
        boolean italic = textStyle.isItalic();
        
        // 如果有旋转，需要特殊处理
        // 文本内容
        // 位置（Vec2d）
        // 颜色
        // 字体族
        // 字体大小
        // 粗体
        // 斜体
        context.drawText(
            text,            // 文本内容
            transformedPos,  // 位置（Vec2d）
            textColor,       // 颜色
            fontFamily,      // 字体族
            fontSize,        // 字体大小
            bold,           // 粗体
            italic          // 斜体
        );
    }

    private Color getColor(DrawContext context) {
        ShapeStyle activeStyle = context.getCurrentStyle();
        Color textColor = null;
        if (isSelected() || isHighlighted()) {
            if (activeStyle != null && activeStyle.getLineStyle() != null && activeStyle.getLineStyle().getColor() != null) {
                textColor = activeStyle.getLineStyle().getColor();
            }
        }
        if (textColor == null) {
            // 常规情况下优先文字样式颜色，再退化到当前样式颜色
            textColor = textStyle.getColor();
            if (textColor == null && activeStyle != null && activeStyle.getLineStyle() != null) {
                textColor = activeStyle.getLineStyle().getColor();
            }
            if (textColor == null) {
                textColor = Color.BLACK; // 最后的回退
            }
        }
        return textColor;
    }

    @Override
    public BoundingBox getBoundingBox() {
        if (boundsDirty) {
            updateBounds();
        }
        
        if (Math.abs(rotation) < TOLERANCE) {
            // 无旋转时使用简单的矩形边界
            return new BoundingBox(
                position.x,
                position.y,
                position.x + width,
                position.y + height
            );
        } else {
            // 有旋转时计算旋转后的边界框
            return calculateRotatedBoundingBox();
        }
    }

    private BoundingBox calculateRotatedBoundingBox() {
        // 计算文本框的四个角点
        Vec2d[] corners = {
            position, // 左上角
            new Vec2d(position.x + width, position.y), // 右上角
            new Vec2d(position.x + width, position.y + height), // 右下角
            new Vec2d(position.x, position.y + height) // 左下角
        };
        
        // 旋转所有角点
        Vec2d[] rotatedCorners = new Vec2d[4];
        for (int i = 0; i < 4; i++) {
            rotatedCorners[i] = GeometryUtils.rotate(corners[i].subtract(position), rotation).add(position);
        }
        
        // 计算旋转后边界框的最小和最大坐标
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Vec2d corner : rotatedCorners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    @Override
    public boolean contains(Vec2d point) {
        if (point == null) {
            return false;
        }
        
        // 首先检查是否在包围盒内（快速排除）
        if (!getBoundingBox().contains(point)) {
            return false;
        }
        
        // 对于精确检测，尝试使用文本轮廓
        try {
            return containsPointPrecise(point, 0.0);
        } catch (Exception e) {
            LOGGER.warn("精确文本包含检测失败，回退到包围盒检测: {}", e.getMessage());
            return getBoundingBox().contains(point);
        }
    }
    
    /**
     * 精确的文本包含检测，基于文本轮廓
     * @param point 要检测的点
     * @param tolerance 容差
     * @return 是否包含该点
     */
    private boolean containsPointPrecise(Vec2d point, double tolerance) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        try {
            // 将文本转换为图形路径进行精确检测
            List<Shape> textGraphics = convertToGraphics();
            for (Shape graphic : textGraphics) {
                if (graphic.containsPoint(point, tolerance)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("文本轮廓检测失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        if (point == null) {
            return false;
        }
        
        // 首先检查是否在扩展的包围盒内（快速排除）
        BoundingBox bbox = getBoundingBox();
        BoundingBox expandedBbox = new BoundingBox(
            bbox.getMinX() - tolerance,
            bbox.getMinY() - tolerance,
            bbox.getMaxX() + tolerance,
            bbox.getMaxY() + tolerance
        );
        
        if (!expandedBbox.contains(point)) {
            return false;
        }
        
        // 尝试精确检测
        try {
            return containsPointPrecise(point, tolerance);
        } catch (Exception e) {
            LOGGER.warn("精确文本包含检测失败，回退到包围盒检测: {}", e.getMessage());
            // 回退到简单的包围盒检测
            if (Math.abs(rotation) < TOLERANCE) {
                return expandedBbox.contains(point);
            } else {
                return containsPointRotated(point, tolerance);
            }
        }
    }

    private boolean containsPointRotated(Vec2d point, double tolerance) {
        // 将点转换到文本的局部坐标系
        Vec2d localPoint = GeometryUtils.rotate(point.subtract(position), -rotation);
        
        // 检查点是否在旋转后的矩形内（考虑容差）
        return localPoint.x >= -tolerance && 
               localPoint.x <= width + tolerance && 
               localPoint.y >= -tolerance && 
               localPoint.y <= height + tolerance;
    }

    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (point == null) {
            return position;
        }
        
        try {
            // 尝试使用文本轮廓进行精确计算
            return getClosestPointPrecise(point);
        } catch (Exception e) {
            LOGGER.warn("精确最近点计算失败，回退到包围盒计算: {}", e.getMessage());
            // 回退到包围盒计算
            return getBoundingBox().getClosestPoint(point);
        }
    }
    
    /**
     * 精确的最近点计算，基于文本轮廓
     * @param point 目标点
     * @return 文本轮廓上最近的点
     */
    private Vec2d getClosestPointPrecise(Vec2d point) {
        if (text == null || text.isEmpty()) {
            return position;
        }
        
        try {
            // 将文本转换为图形路径进行精确计算
            List<Shape> textGraphics = convertToGraphics();
            if (textGraphics.isEmpty()) {
                return position;
            }
            
            Vec2d closestPoint = null;
            double minDistance = Double.MAX_VALUE;
            
            for (Shape graphic : textGraphics) {
                Vec2d graphicClosest = graphic.getClosestPoint(point);
                double distance = point.distance(graphicClosest);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = graphicClosest;
                }
            }
            
            return closestPoint != null ? closestPoint : position;
        } catch (Exception e) {
            LOGGER.warn("文本轮廓最近点计算失败: {}", e.getMessage());
            return position;
        }
    }

    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(position);
        points.add(new Vec2d(position.x + width, position.y));
        points.add(new Vec2d(position.x + width, position.y + height));
        points.add(new Vec2d(position.x, position.y + height));
        return points;
    }

    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index == 0) {
            position = point;
            boundsDirty = true;
        }
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        if (other == null || text == null || text.isEmpty()) {
            return intersections;
        }
        
        try {
            // 将文本转换为图形路径进行精确交点计算
            List<Shape> textGraphics = convertToGraphics();
            for (Shape graphic : textGraphics) {
                intersections.addAll(graphic.getIntersectionPoints(other));
            }
        } catch (Exception e) {
            LOGGER.warn("文本交点计算失败: {}", e.getMessage());
        }
        
        return intersections;
    }

    @Override
    public boolean intersects(Shape other) {
        if (other == null) {
            return false;
        }
        
        // 首先进行包围盒相交检测（快速排除）
        if (!getBoundingBox().intersects(other.getBoundingBox())) {
            return false;
        }
        
        // 尝试精确相交检测
        try {
            return intersectsPrecise(other);
        } catch (Exception e) {
            LOGGER.warn("精确文本相交检测失败，回退到包围盒检测: {}", e.getMessage());
            return getBoundingBox().intersects(other.getBoundingBox());
        }
    }
    
    /**
     * 精确的相交检测，基于文本轮廓
     * @param other 另一个形状
     * @return 是否相交
     */
    private boolean intersectsPrecise(Shape other) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        try {
            // 将文本转换为图形路径进行精确检测
            List<Shape> textGraphics = convertToGraphics();
            for (Shape graphic : textGraphics) {
                if (graphic.intersects(other)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("文本轮廓相交检测失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        // 实现获取与其他形状的交点
        return getIntersectionPoints(other);
    }

    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 文本形状不支持延伸操作
        throw new UnsupportedOperationException("文本形状不支持延伸交点计算");
    }

    @Override
    public List<Vec2d> getEndpoints() {
        // 返回文本框的四个角点
        return getControlPoints();
    }

    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 文本形状不支持切线计算
        throw new UnsupportedOperationException("文本形状不支持切线计算");
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        // 计算点到文本边界框的距离
        return point.distance(position);
    }

    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        // 文本形状不支持分割操作
        throw new UnsupportedOperationException("文本形状不支持分割操作");
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        // 文本形状不支持延伸操作
        throw new UnsupportedOperationException("文本形状不支持延伸操作");
    }

    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 文本形状不支持延伸操作
        throw new UnsupportedOperationException("文本形状不支持延伸操作");
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        // 文本形状不支持修剪操作
        throw new UnsupportedOperationException("文本形状不支持修剪操作");
    }

    @Override
    public Shape createOffset(double distance) {
        // 文本形状不支持偏移操作
        throw new UnsupportedOperationException("文本形状不支持偏移操作");
    }

    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> intersections = new ArrayList<>();
        
        if (points == null || points.isEmpty() || text == null || text.isEmpty()) {
            return intersections;
        }
        
        try {
            // 将文本转换为图形路径进行精确交点计算
            List<Shape> textGraphics = convertToGraphics();
            for (Shape graphic : textGraphics) {
                intersections.addAll(graphic.getIntersectionsWithPolyline(points));
            }
        } catch (Exception e) {
            LOGGER.warn("文本与折线交点计算失败: {}", e.getMessage());
        }
        
        return intersections;
    }

    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        if (points == null || points.isEmpty() || text == null || text.isEmpty()) {
            return false;
        }
        
        try {
            // 将文本转换为图形路径进行精确相交检测
            List<Shape> textGraphics = convertToGraphics();
            for (Shape graphic : textGraphics) {
                if (graphic.intersectsPolyline(points)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("文本与折线相交检测失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String serialize() {
        try {
            TextShapeData data = new TextShapeData();
            data.positionX = position.x;
            data.positionY = position.y;
            data.text = text;
            data.rotation = rotation;
            data.width = width;
            data.height = height;
            data.fontFamily = textStyle != null ? textStyle.getFontFamily() : null;
            data.fontSize = textStyle != null ? textStyle.getFontSize() : 0;
            data.bold = textStyle != null && textStyle.isBold();
            data.italic = textStyle != null && textStyle.isItalic();
            data.color = textStyle != null ? textStyle.getColor() : null;
            
            return GSON.toJson(data);
        } catch (Exception e) {
            LOGGER.error("序列化文本形状时发生错误: {}", e.getMessage(), e);
            // 回退到简单格式
            return String.format("%f,%f,%s", position.x, position.y, text);
        }
    }

    @Override
    public void deserialize(String data) {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("序列化数据不能为空");
        }
        
        try {
            // 首先尝试JSON格式
            if (data.trim().startsWith("{")) {
                TextShapeData shapeData = GSON.fromJson(data, TextShapeData.class);
                this.position = new Vec2d(shapeData.positionX, shapeData.positionY);
                this.text = shapeData.text;
                this.rotation = shapeData.rotation;
                this.width = shapeData.width;
                this.height = shapeData.height;
                
                // 重建文本样式
                if (shapeData.fontFamily != null) {
                    this.textStyle = new TextStyle();
                    this.textStyle.setFontFamily(shapeData.fontFamily);
                    this.textStyle.setFontSize(shapeData.fontSize);
                    this.textStyle.setBold(shapeData.bold);
                    this.textStyle.setItalic(shapeData.italic);
                    this.textStyle.setColor(shapeData.color);
                }
            } else {
                // 回退到旧格式
                deserializeLegacyFormat(data);
            }
            
            this.boundsDirty = true;
            updateBounds();
            
        } catch (JsonSyntaxException e) {
            LOGGER.warn("JSON反序列化失败，尝试旧格式: {}", e.getMessage());
            try {
                deserializeLegacyFormat(data);
                this.boundsDirty = true;
                updateBounds();
            } catch (Exception ex) {
                LOGGER.error("反序列化文本形状时发生错误: {}", ex.getMessage(), ex);
                throw new RuntimeException("反序列化失败", ex);
            }
        } catch (Exception e) {
            LOGGER.error("反序列化文本形状时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("反序列化失败", e);
        }
    }
    
    /**
     * 旧格式的反序列化方法（向后兼容）
     */
    private void deserializeLegacyFormat(String data) {
        String[] parts = data.split(",", 3);
        if (parts.length == 3) {
            try {
                position = new Vec2d(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1])
                );
                text = parts[2];
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("反序列化失败：数值格式错误", e);
            }
        } else {
            throw new IllegalArgumentException("序列化格式错误：需要3个部分，实际：" + parts.length);
        }
    }
    
    /**
     * 文本形状数据的JSON序列化类
     */
    private static class TextShapeData {
        public double positionX;
        public double positionY;
        public String text;
        public double rotation;
        public double width;
        public double height;
        public String fontFamily;
        public float fontSize;
        public boolean bold;
        public boolean italic;
        public Color color;
    }

    @Override
    public List<Vec2d> getPoints() {
        List<Vec2d> points = new ArrayList<>();
        
        // 获取变换后的位置
        Vec2d transformedPos = transform.transform(position);
        
        // 添加文本框的四个角点
        points.add(transformedPos); // 左上角
        points.add(new Vec2d(transformedPos.x + width, transformedPos.y)); // 右上角
        points.add(new Vec2d(transformedPos.x + width, transformedPos.y + height)); // 右下角
        points.add(new Vec2d(transformedPos.x, transformedPos.y + height)); // 左下角
        points.add(transformedPos); // 闭合回到左上角
        
        return points;
    }

    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor,
                       imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        visitor.render(this, drawList, camera);
    }

    @Override
    public void scale(Vec2d scale, Vec2d center) {
        if (scale == null || center == null) {
            throw new IllegalArgumentException("缩放参数不能为空");
        }
        
        // 缩放位置
        position = center.add(position.subtract(center).multiply(scale));
        
        // 缩放字体大小
        if (textStyle != null) {
            float currentFontSize = textStyle.getFontSize();
            float newFontSize = currentFontSize * (float) Math.sqrt(scale.x * scale.y); // 使用几何平均值
            textStyle = textStyle.withFontSize(newFontSize);
        }
        
        // 更新边界
        boundsDirty = true;
        updateBounds();
    }

    /**
     * 设置字体大小
     * @param fontSize 新的字体大小
     */
    public void setFontSize(float fontSize) {
        if (textStyle != null) {
            textStyle = textStyle.withFontSize(fontSize);
            boundsDirty = true;
            updateBounds();
        }
    }

    /**
     * 获取字体大小
     * @return 当前字体大小
     */
    public float getFontSize() {
        return textStyle != null ? textStyle.getFontSize() : TextStyle.DEFAULT_FONT_SIZE;
    }

    /**
     * 将AWT Shape转换为PathShape或PolylineShape
     * @param awtShape AWT的Shape对象
     * @return 转换后的形状，如果转换失败则返回null
     */
    private PolylineShape convertAwtShapeToPathShape(java.awt.Shape awtShape) {
        if (awtShape == null) {
            return null;
        }
        
        PathIterator pathIterator = awtShape.getPathIterator(null); // 使用默认平坦度
        List<Vec2d> points = new ArrayList<>();
        double[] coords = new double[6];

        while (!pathIterator.isDone()) {
            int segmentType = pathIterator.currentSegment(coords);
            switch (segmentType) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    points.add(new Vec2d(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_CLOSE:
                    if (!points.isEmpty()) {
                        points.add(points.getFirst()); // 闭合路径
                    }
                    break;
                case PathIterator.SEG_QUADTO:
                    // 二次贝塞尔曲线，需要平坦化
                    // 这里简化处理，只取控制点和终点
                    points.add(new Vec2d(coords[0], coords[1])); // 控制点
                    points.add(new Vec2d(coords[2], coords[3])); // 终点
                    break;
                case PathIterator.SEG_CUBICTO:
                    // 三次贝塞尔曲线，需要平坦化
                    // 这里简化处理，只取两个控制点和终点
                    points.add(new Vec2d(coords[0], coords[1])); // 第一个控制点
                    points.add(new Vec2d(coords[2], coords[3])); // 第二个控制点
                    points.add(new Vec2d(coords[4], coords[5])); // 终点
                    break;
            }
            pathIterator.next();
        }
        
        if (points.isEmpty()) {
            return null;
        }
        
        return new PolylineShape(points, false); // 字符路径通常不闭合
    }

    /**
     * 优化的转换为图形路径方法，使用GlyphVector提高精度
     * 将文字转换为可编辑的图形路径
     * @return 转换后的图形列表
     */
    public List<com.masterplanner.core.model.Shape> convertToGraphics() {
        List<com.masterplanner.core.model.Shape> graphics = new ArrayList<>();
        
        if (text == null || text.isEmpty() || textStyle == null) {
            return graphics;
        }
        
        try {
            // 使用缓存的Graphics2D
            Graphics2D g2d = getGraphics2D();
            
            // 使用GlyphVector获取精确的字符路径
            GlyphVector glyphVector = g2d.getFont().createGlyphVector(g2d.getFontRenderContext(), text);
            double currentX = position.x;
            double currentY = position.y;
            
            for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
                java.awt.Shape glyphShape = glyphVector.getGlyphOutline(i, (float) currentX, (float) currentY);
                
                if (glyphShape != null && !glyphShape.getBounds().isEmpty()) {
                    // 将AWT Shape转换为我们的PathShape
                    PolylineShape path = convertAwtShapeToPathShape(glyphShape);
                    if (path != null) {
                        // 应用文字样式
                        ShapeStyle charStyle = new ShapeStyle();
                        charStyle.setLineStyle((ILineStyle) new LineStyle(LineStyle.LineType.SOLID, 1.0f).withColor(textStyle.getColor()));
                        charStyle.setFillStyle((IFillStyle) new FillStyle(textStyle.getColor(), 1.0f));
                        path.setStyle(charStyle);
                        
                        graphics.add(path);
                    }
                }
                
                currentX += glyphVector.getGlyphMetrics(i).getAdvanceX();
            }
            
        } catch (Exception e) {
            // 如果GlyphVector转换失败，创建简单的矩形作为回退
            RectangleShape textRect = new RectangleShape(position, width, height, 0.0);
            ShapeStyle rectStyle = new ShapeStyle();
            rectStyle.setLineStyle((ILineStyle) new LineStyle(LineStyle.LineType.SOLID, 1.0f).withColor(textStyle.getColor()));
            textRect.setStyle(rectStyle);
            graphics.add(textRect);
        }
        
        return graphics;
    }
    
    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 文字形状的打断比较复杂，这里提供一个简化实现
        // 将文字转换为图形路径，然后进行打断
        List<Shape> textGraphics = convertToGraphics();
        
        for (Shape graphic : textGraphics) {
            List<Shape> brokenGraphics = graphic.breakShape(firstBreakPoint, secondBreakPoint, breakMode);
            newShapes.addAll(brokenGraphics);
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到文字形状的距离
        // 使用边界框作为简化计算
        BoundingBox bbox = getBoundingBox();
        if (bbox == null) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToBoundingBox(bbox.getMin(), bbox.getMax(), point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的文字属性（保留用于未来扩展）
            // Vec2d transformedPosition = getTransform().transform(position);
            
            // 转换到屏幕坐标（保留用于未来扩展）
            // Vec2d screenPosition = camera.worldToScreen(transformedPosition);
            
            // 获取文字样式（保留用于未来扩展）
            // Color textColor = textStyle != null ? textStyle.getColor() : Color.BLACK;
            // int color = textColor.getRGB();
            
            // 对于文字，我们使用简单的矩形边界框来表示
            // 因为ImGui的文字渲染比较复杂，这里提供一个简化的实现
            BoundingBox bbox = getBoundingBox();
            if (bbox != null) {
                Vec2d transformedMin = getTransform().transform(bbox.getMin());
                Vec2d transformedMax = getTransform().transform(bbox.getMax());
                
                Vec2d screenMin = camera.worldToScreen(transformedMin);
                Vec2d screenMax = camera.worldToScreen(transformedMax);
                
                // 绘制文字边界框
                drawList.addRect(
                    (float) screenMin.x, (float) screenMin.y,
                    (float) screenMax.x, (float) screenMax.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
                
                // 在边界框中心绘制一个点表示文字位置
                float centerX = (float) ((screenMin.x + screenMax.x) / 2);
                float centerY = (float) ((screenMin.y + screenMax.y) / 2);
                drawList.addCircleFilled(
                    centerX, centerY, 2.0f,
                    0x80FFFFFF // 白色，半透明
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染文字ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
} 