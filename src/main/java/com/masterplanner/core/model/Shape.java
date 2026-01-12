package com.masterplanner.core.model;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.Matrix3d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.FillStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.canvas.CanvasRenderer;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.api.model.IDirty;
import com.masterplanner.api.graphics.IShapeStyle;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;
import com.masterplanner.core.geometry.AffineTransform;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/**
 * 基础形状接口
 */
public abstract class Shape implements Cloneable, IDirty {
    protected final String id;
    protected Matrix3d transform;
    protected IShapeStyle style;
    protected boolean selected;
    protected boolean visible;
    protected boolean highlighted;
    protected boolean deleted;
    
    // 组相关属性
    protected String groupId;    // 所属组的ID，null表示不属于任何组
    protected boolean isGroup;   // 是否是组本身（组的代表图形）

    // 预览状态
    protected Vec2d previewOffset;
    protected double previewRotation;
    protected Vec2d previewRotationCenter;
    protected Vec2d previewScale;
    protected Vec2d previewScaleCenter;
    protected Vec2d previewMirrorStart;
    protected Vec2d previewMirrorEnd;
    protected double previewOffsetDistance;
    protected Vec2d previewFilletCenter;
    protected double previewFilletRadius;

    // 变换相关的属性
    protected double rotation = 0.0;
    protected Vec2d scale = new Vec2d(1.0, 1.0);

    private Vec2d position;
    private boolean dirty = false;

    /**
     * 构造函数
     * @param position 图形的初始位置
     */
    protected Shape(Vec2d position) {
        this.id = UUID.randomUUID().toString();
        this.transform = new Matrix3d();
        this.style = new ShapeStyle(new LineStyle(), new FillStyle());
        this.selected = false;
        this.visible = true;
        this.highlighted = false;
        this.deleted = false;
        resetPreviews();
        this.position = position != null ? position : new Vec2d(0, 0);
    }

    // 基本属性
    public String getId() {
        return id;
    }

    public Matrix3d getTransform() {
        return transform;
    }

    public void setTransform(Matrix3d transform) {
        this.transform = transform;
    }

    public IShapeStyle getStyle() {
        return style;
    }

    public void setStyle(IShapeStyle style) {
        if (style != null) {
            this.style = style;
            markDirty();
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    // 预览状态管理
    protected void resetPreviews() {
        previewOffset = null;
        previewRotation = 0;
        previewRotationCenter = null;
        previewScale = null;
        previewScaleCenter = null;
        previewMirrorStart = null;
        previewMirrorEnd = null;
        previewOffsetDistance = 0;
        previewFilletCenter = null;
        previewFilletRadius = 0;
    }

    // 修改工具所需的操作
    public void move(Vec2d offset) {
        if (offset != null) {
            Matrix3d translation = new Matrix3d();
            translation.setTranslation(offset.x, offset.y);
            transform = transform.multiply(translation);
        }
    }

    public void rotate(Vec2d center, double angle) {
        if (center != null) {
            Matrix3d toOrigin = new Matrix3d();
            toOrigin.setTranslation(-center.x, -center.y);

            Matrix3d rotation = new Matrix3d();
            rotation.setRotation(angle);

            Matrix3d fromOrigin = new Matrix3d();
            fromOrigin.setTranslation(center.x, center.y);

            Matrix3d temp = transform.multiply(toOrigin);
            temp = temp.multiply(rotation);
            transform = temp.multiply(fromOrigin);
        }
    }

    /**
     * 缩放形状（使用浮点数）
     *
     * @param sx X方向缩放因子
     * @param sy Y方向缩放因子
     */
    public void scale(float sx, float sy) {
        scale(new Vec2d(sx, sy), getPosition());
    }

    /**
     * 统一缩放形状
     *
     * @param s 统一缩放因子
     */
    public void scale(float s) {
        scale(s, s);
    }

    /**
     * 缩放形状（使用向量）
     *
     * @param scale  缩放向量
     * @param center 缩放中心点
     */
    public void scale(Vec2d scale, Vec2d center) {
        if (center != null && scale != null) {
            Matrix3d toOrigin = new Matrix3d();
            toOrigin.setTranslation(-center.x, -center.y);

            Matrix3d scaling = new Matrix3d();
            scaling.setScale(scale.x, scale.y);

            Matrix3d fromOrigin = new Matrix3d();
            fromOrigin.setTranslation(center.x, center.y);

            Matrix3d temp = transform.multiply(toOrigin);
            temp = temp.multiply(scaling);
            transform = temp.multiply(fromOrigin);

            // 更新缩放属性
            this.scale = new Vec2d(this.scale.x * scale.x, this.scale.y * scale.y);
        }
    }

    public void mirror(Vec2d point1, Vec2d point2) {
        if (point1 != null && point2 != null) {
            Vec2d v = point2.subtract(point1);
            double angle = Math.atan2(v.y, v.x);

            Matrix3d toOrigin = new Matrix3d();
            toOrigin.setTranslation(-point1.x, -point1.y);

            Matrix3d rotation = new Matrix3d();
            rotation.setRotation(-angle);

            Matrix3d scale = new Matrix3d();
            scale.setScale(1, -1);

            Matrix3d rotationBack = new Matrix3d();
            rotationBack.setRotation(angle);

            Matrix3d fromOrigin = new Matrix3d();
            fromOrigin.setTranslation(point1.x, point1.y);

            Matrix3d temp = transform.multiply(toOrigin);
            temp = temp.multiply(rotation);
            temp = temp.multiply(scale);
            temp = temp.multiply(rotationBack);
            transform = temp.multiply(fromOrigin);
        }
    }

    public void delete() {
        deleted = true;
        visible = false;
    }

    public void restore() {
        deleted = false;
        visible = true;
    }

    public void setPreviewMirror(Vec2d start, Vec2d end) {
        previewMirrorStart = start;
        previewMirrorEnd = end;
    }

    public void setPreviewOffset(double distance) {
        previewOffsetDistance = distance;
    }

    public void clearPreviewMirror() {
        previewMirrorStart = null;
        previewMirrorEnd = null;
    }

    public void clearPreviewOffset() {
        previewOffsetDistance = 0;
    }

    // 绘制方法
    public abstract void draw(DrawContext context);

    /**
     * 渲染形状到画布
     * @param renderer 画布渲染器
     * @param camera 相机
     * @param opacity 不透明度
     */
    public void render(CanvasRenderer renderer, CanvasCamera camera, float opacity) {
        if (!isVisible() || isDeleted()) {
            return;
        }
        
        // 创建一个DrawContext对象供draw方法使用
        DrawContext context = new DrawContext();
        context.setRenderer(renderer);
        context.setCamera(camera);
        context.setOpacity(opacity);
        
        // 根据选中状态和高亮状态设置样式
        if (isSelected()) {
            context.setStyle(ShapeStyle.SELECTED);
        } else if (isHighlighted()) {
            context.setStyle(ShapeStyle.HIGHLIGHTED);
        } else if (style != null) {
            context.setStyle((ShapeStyle)style);
        }
        
        // 调用子类的draw方法进行实际绘制
        draw(context);
    }
    
    /**
     * 使用DrawContext直接渲染形状
     * @param context 绘制上下文
     */
    public void render(DrawContext context) {
        if (!isVisible() || isDeleted()) {
            return;
        }
        
        // 保存当前上下文样式
        ShapeStyle originalStyle = context.getCurrentStyle();
        
        // 根据选中状态和高亮状态设置样式
        if (isSelected()) {
            context.setStyle(ShapeStyle.SELECTED);
        } else if (isHighlighted()) {
            context.setStyle(ShapeStyle.HIGHLIGHTED);
        } else if (style != null) {
            context.setStyle((ShapeStyle)style);
        }
        
        // 直接调用子类的draw方法进行实际绘制
        draw(context);
        
        // 恢复原始样式
        if (originalStyle != null) {
            context.setStyle(originalStyle);
        }
    }
    
    /**
     * 使用ImGui渲染形状
     * @param drawList ImGui绘制列表
     * @param camera 画布相机
     */
    public void renderImGui(imgui.ImDrawList drawList, CanvasCamera camera) {
        if (!isVisible() || isDeleted()) {
            return;
        }
        
        // 调用子类的具体实现
        drawImGui(drawList, camera);
    }
    
    /**
     * 子类需要实现的ImGui绘制方法
     * @param drawList ImGui绘制列表
     * @param camera 画布相机
     */
    protected abstract void drawImGui(imgui.ImDrawList drawList, CanvasCamera camera);

    // 几何操作
    public abstract void translate(Vec2d offset);

    public abstract void rotate(double angle, Vec2d center);
    
    /**
     * 应用一个通用的仿射变换。
     * 这是所有变换操作（平移、旋转、缩放、斜切）的底层基础。
     * 子类应重写此方法以高效地变换其几何定义。
     *
     * @param transformMatrix 一个表示仿射变换的矩阵。
     * @return 变换后的图形。如果变换不改变图形类型，可以返回 'this'；
     *         如果变换改变了图形类型（例如，圆被非均匀缩放后变成椭圆），则必须返回一个新的Shape对象。
     */
    public abstract Shape transform(AffineTransform transformMatrix);

    /**
     * 获取形状的边界框
     * @return 形状的边界框
     */
    public abstract com.masterplanner.core.geometry.BoundingBox getBoundingBox();

    // 点操作
    public abstract boolean contains(Vec2d point);

    public abstract Vec2d getClosestPoint(Vec2d point);

    public abstract List<Vec2d> getControlPoints();

    public abstract void setControlPoint(int index, Vec2d point);

    // 相交测试
    public abstract boolean intersects(Shape other);

    public abstract List<Vec2d> getIntersectionPoints(Shape other);

    // 修改工具所需的额外方法
    public abstract List<Vec2d> getIntersectionsWith(Shape other);

    public abstract List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance);

    public abstract List<Vec2d> getEndpoints();

    public abstract Vec2d getTangentAt(Vec2d point);

    public abstract double getSignedDistance(Vec2d point);

    public abstract List<Shape> split(List<Vec2d> points, Vec2d pickPoint);

    public abstract Shape extend(Vec2d point, double distance);

    public abstract Shape extend(Vec2d point, Vec2d toPoint);

    public abstract Shape trimToPoint(Vec2d point);

    public abstract Shape createOffset(double distance);

    public abstract List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points);

    public abstract boolean intersectsPolyline(List<Vec2d> points);

    /**
     * 打断图形
     * 
     * <p>在指定点打断图形，返回打断后的新图形列表。</p>
     * 
     * @param firstBreakPoint 第一个打断点
     * @param secondBreakPoint 第二个打断点（两点模式），可为null
     * @param breakMode 打断模式（"SINGLE_POINT" 或 "TWO_POINT"）
     * @return 打断后的图形列表
     */
    public abstract List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode);

    /**
     * 计算点到图形的距离
     * 
     * <p>计算指定点到当前图形的最短距离，用于图形选择和碰撞检测。</p>
     * 
     * @param point 要计算距离的点
     * @return 点到图形的最短距离
     */
    public abstract double getDistanceToPoint(Vec2d point);

    /**
     * 克隆形状
     *
     * @return 形状的克隆副本
     */
    @Override
    public Shape clone() {
        try {
            Shape clone = (Shape) super.clone();
            clone.transform = this.transform.clone();
            clone.style = this.style.clone();
            // 克隆其他可变字段
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    // 序列化
    public abstract String serialize();

    public abstract void deserialize(String data);
    
    /**
     * 接受访问者
     * 
     * <p>这是访问者模式的核心方法，让形状对象"接受"一个访问者，
     * 由访问者处理该形状的特定操作。</p>
     * 
     * <p><strong>设计优势：</strong></p>
     * <ul>
     *   <li><strong>类型安全</strong>：编译时类型检查，避免运行时类型转换</li>
     *   <li><strong>扩展性</strong>：新增操作类型时无需修改形状类</li>
     *   <li><strong>职责分离</strong>：形状负责数据结构，访问者负责操作逻辑</li>
     * </ul>
     * 
     * @param visitor 访问者对象
     * @return 处理后的形状
     */
    public abstract Shape accept(IShapeVisitor visitor);
    
    /**
     * 接受渲染访问者
     * 
     * <p>专门用于渲染操作的访问者模式实现，支持ImGui等不同渲染后端。</p>
     * 
     * @param visitor 渲染访问者对象
     * @param drawList ImGui绘制列表
     * @param camera 画布相机
     */
    public abstract void accept(IRenderVisitor visitor,
                                imgui.ImDrawList drawList, CanvasCamera camera);

    /**
     * 计算点到形状的最短距离
     *
     * @param point 要计算的点
     * @return 最短距离
     */
    public double distanceTo(Vec2d point) {
        // 首先检查点是否在边界框内，如果在，则距离为0
        if (getBoundingBox().contains(point)) {
            return 0.0;
        }
        
        // 否则使用边界框的距离计算方法
        return getBoundingBox().distanceTo(point);
    }

    /**
     * 获取形状的位置
     *
     * @return 形状的中心点位置
     */
    public Vec2d getPosition() {
        return position;
    }

    /**
     * 设置形状的位置
     *
     * @param position 新的位置（中心点）
     */
    public void setPosition(Vec2d position) {
        // 修复：先获取当前位置，再设置新位置，然后计算偏移量
        Vec2d oldPosition = this.position;
        this.position = position;
        markDirty();  // 当位置改变时标记为脏
        
        // 计算从旧位置到新位置的偏移量
        Vec2d offset = position.subtract(oldPosition);
        
        // 应用偏移量到变换矩阵
        if (offset.length() > 0.001) {  // 只有当偏移量足够大时才应用
            move(offset);
        }
    }

    /**
     * 获取线条样式
     */
    public LineStyle getLineStyle() {
        return (LineStyle) style.getLineStyle();
    }

    /**
     * 设置线条样式
     */
    public void setLineStyle(LineStyle lineStyle) {
        if (style instanceof ShapeStyle) {
            style.setLineStyle(lineStyle);
        }
    }

    /**
     * 获取旋转角度（弧度）
     */
    public double getRotation() {
        return rotation;
    }

    /**
     * 设置旋转角度（弧度）
     */
    public void setRotation(double angle) {
        this.rotation = angle;
        // 更新变换矩阵
        updateTransform();
    }

    /**
     * 获取缩放因子
     */
    public Vec2d getScale() {
        return scale;
    }

    /**
     * 设置缩放因子
     */
    public void setScale(double sx, double sy) {
        this.scale = new Vec2d(sx, sy);
        // 更新变换矩阵
        updateTransform();
    }

    /**
     * 设置位置
     */
    public void setPosition(double x, double y) {
        setPosition(new Vec2d(x, y));
    }

    /**
     * 移动形状
     */
    public void move(double dx, double dy) {
        move(new Vec2d(dx, dy));
    }

    /**
     * 旋转形状
     */
    public void rotate(double angle) {
        rotate(getPosition(), angle);
    }

    /**
     * 更新变换矩阵
     */
    protected void updateTransform() {
        Vec2d pos = getPosition();

        // 重置变换矩阵
        transform = new Matrix3d();

        // 应用平移
        Matrix3d translation = new Matrix3d();
        translation.setTranslation(pos.x, pos.y);
        transform = transform.multiply(translation);

        // 应用旋转
        Matrix3d rotation = new Matrix3d();
        rotation.setRotation(this.rotation);
        transform = transform.multiply(rotation);

        // 应用缩放
        Matrix3d scaling = new Matrix3d();
        scaling.setScale(scale.x, scale.y);
        transform = transform.multiply(scaling);
    }

    /**
     * 检查形状是否包含指定点
     *
     * @param point 要检查的点
     * @param tolerance 容差值
     * @return 是否包含该点
     */
    public boolean containsPoint(Vec2d point, double tolerance) {
        // 首先检查点是否在边界框内
        if (!getBoundingBox().contains(point)) {
            return false;
        }

        try {
            // 计算点到形状的最短距离
            double distance = getSignedDistance(point);
            
            // 检查距离是否为有效值
            if (Double.isNaN(distance) || Double.isInfinite(distance)) {
                // 如果距离无效，使用点到最近点的距离
                Vec2d closestPoint = getClosestPoint(point);
                if (closestPoint != null) {
                    distance = point.distance(closestPoint);
                    return distance <= tolerance;
                }
                return false;
            }

            // 如果距离小于容差值，则认为点在形状内
            return Math.abs(distance) <= tolerance;
        } catch (Exception e) {
            // 如果计算过程中出现异常，使用备用方法
            try {
                Vec2d closestPoint = getClosestPoint(point);
                return closestPoint != null && point.distance(closestPoint) <= tolerance;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    /**
     * 检查形状是否包含指定点（使用默认容差）
     *
     * @param point 要检查的点
     * @return 是否包含该点
     */
    public boolean containsPoint(Vec2d point) {
        return containsPoint(point, 5.0); // 使用默认容差值5.0
    }

    /**
     * 获取形状包含的方块数量
     *
     * @return 方块数量
     */
    public int getBlockCount() {
        // 默认实现，返回边界框内的方块数量的估计值
        com.masterplanner.core.geometry.BoundingBox bounds = getBoundingBox();
        double width = bounds.getWidth();
        double height = bounds.getHeight();

        // 将实际尺寸转换为方块数量（1个方块 = 1个单位）
        int blockWidth = (int) Math.ceil(width);
        int blockHeight = (int) Math.ceil(height);

        // 返回估计的方块数量
        return blockWidth * blockHeight;
    }

    /**
     * 获取形状的具体方块位置列表
     * 子类可以重写此方法提供更精确的实现
     *
     * @return 方块位置列表
     */
    public List<Vec2d> getBlockPositions() {
        List<Vec2d> positions = new ArrayList<>();
        com.masterplanner.core.geometry.BoundingBox bounds = getBoundingBox();

        // 获取边界框的范围
        int minX = (int) Math.floor(bounds.getMin().x);
        int minY = (int) Math.floor(bounds.getMin().y);
        int maxX = (int) Math.ceil(bounds.getMax().x);
        int maxY = (int) Math.ceil(bounds.getMax().y);

        // 遍历边界框内的每个方块位置
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                Vec2d blockPos = new Vec2d(x, y);
                if (containsPoint(blockPos)) {
                    positions.add(blockPos);
                }
            }
        }

        return positions;
    }

    /**
     * 获取形状的所有点
     *
     * @return 形状的点列表
     */
    public abstract List<Vec2d> getPoints();

    /**
     * 获取形状的关键点（用于吸附等功能）
     * 默认实现返回控制点，子类可以重写提供更精确的关键点
     *
     * @return 形状的关键点列表
     */
    public List<Vec2d> getKeyPoints() {
        try {
            List<Vec2d> keyPoints = new ArrayList<>();
            
            // 添加控制点作为基础关键点
            List<Vec2d> controlPoints = getControlPoints();
            if (controlPoints != null && !controlPoints.isEmpty()) {
                keyPoints.addAll(controlPoints);
            }
            
            // 添加中心点
            Vec2d center = getPosition();
            if (center != null) {
                keyPoints.add(center);
            }
            
            return keyPoints;
        } catch (Exception e) {
            // 如果获取关键点失败，返回空列表
            return new ArrayList<>();
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void clearDirty() {
        dirty = false;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }
    
    // 组相关方法
    
    /**
     * 获取所属组的ID
     * @return 组ID，如果不属于任何组则返回null
     */
    public String getGroupId() {
        return groupId;
    }
    
    /**
     * 设置所属组的ID
     * @param groupId 组ID
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
        markDirty();
    }
    
    /**
     * 判断是否属于某个组
     * @return true 如果属于组，false 否则
     */
    public boolean isInGroup() {
        return groupId != null;
    }
    
    /**
     * 判断是否是组本身
     * @return true 如果是组，false 否则
     */
    public boolean isGroup() {
        return isGroup;
    }
    
    /**
     * 设置是否为组
     * @param isGroup 是否为组
     */
    public void setIsGroup(boolean isGroup) {
        this.isGroup = isGroup;
        markDirty();
    }
}
