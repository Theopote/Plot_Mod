package com.plot.core.geometry.shapes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.plot.api.geometry.Vec2d;
import com.plot.api.geometry.Matrix3d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.utils.PlotI18n;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.graphics.style.LineStyle;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * 螺旋线形状类
 * 支持多种数学螺旋类型，包括：
 * <ul>
 *   <li>线性螺旋 (LINEAR) - r = r₀ + k·θ</li>
 *   <li>对数螺旋 (LOGARITHMIC) - r = a·e^(b·θ)</li>
 *   <li>半圆螺旋 (SEMICIRCLE) - 分段半圆拼接</li>
 *   <li>费马螺旋变种 (REVERSE_SEMICIRCLE) - 与半圆螺旋相反方向</li>
 *   <li>斐波那契螺旋 (FIBONACCI) - 基于黄金比例的对数螺旋</li>
 *   <li>多边形螺旋 (POLYGON) - 带有角状边缘的螺旋 r = a + b·θ + c·cos(n·θ)</li>
 * </ul>
 * 
 * 提供丰富的参数控制：
 * <ul>
 *   <li>中心点、半径、圈数、间距等基本几何参数</li>
 *   <li>每种类型特有的控制参数（如衰减率、边数、螺旋系数等）</li>
 *   <li>旋转角度、尖角模式、旋转方向等形态控制</li>
 * </ul>
 * 
 * 性能优化：
 * <ul>
 *   <li>使用缓存避免重复计算点坐标</li>
 *   <li>预计算三角函数值减少运算开销</li>
 *   <li>使用关键点优化包含检测和最近点查找</li>
 *   <li>实现增量更新机制，仅在必要时重新计算</li>
 *   <li>动态调整点密度，在保持质量的同时控制点数量</li>
 * </ul>
 */
public class SpiralShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralShape.class);
    
    // 数学常量
    private static final double TWO_PI = 2 * Math.PI;  // 2π常量，避免重复计算
    private static final double GOLDEN_RATIO = 1.618033988749895;  // 黄金比例，用于斐波那契螺旋
    
    // 默认参数

    private static final int DEFAULT_SIDES = 6;  // 默认多边形边数
    private static final double DEFAULT_ROTATION = 0.0;  // 默认旋转角度
    private static final double DEFAULT_TURNS = 3.0;  // 默认圈数
    private static final double DEFAULT_SPACING = 20.0;  // 默认间距
    private static final double DEFAULT_GROWTH_FACTOR = 0.9;  // 默认生长因子
    
    // 分段和精度控制
    private static final int DEFAULT_SEGMENTS_PER_TURN = 60;  // 默认每圈分段数，用于平滑曲线
    private static final int SHARP_EDGED_SEGMENTS_PER_TURN = 8;  // 尖角样式每圈分段数，用于多边形近似
    private static final int MAX_TOTAL_SEGMENTS = 10_000;  // 最大总点数限制
    private static final double DISTANCE_TOLERANCE_FACTOR = 0.5;  // 距离容差因子，用于contains方法
    
    // 螺旋形状参数
    private static final double POLYGON_AMPLITUDE = 0.2;  // 多边形螺旋的振幅系数
    
    /**
     * 最近点结果
     */
    private record ClosestPointResult(Vec2d closestPoint, double distance) {
    }
    
    // 使用共享的SpiralType枚举，移除重复定义

    /**
     * 用于标识发生变化的参数类型
     * 这些参数变化会影响螺旋线的形状和生成方式
     * 用于跟踪参数变化以实现增量更新优化
     */
    private enum SpiralParameter {
        /** 
         * 中心点位置
         * 控制螺旋线的中心坐标，平移操作会改变此参数
         */
        CENTER, 
        
        /** 
         * 基础半径
         * 控制螺旋线的整体大小比例，与spacing共同决定螺旋的展开程度
         */
        RADIUS, 
        
        /** 
         * 旋转圈数
         * 控制螺旋线的总圈数，直接影响生成的点数量
         */
        TURNS, 
        
        /** 
         * 每圈间距
         * 控制螺旋线的紧密程度，数值越大，螺旋线越疏松
         */
        SPACING, 
        
        /** 
         * 螺旋类型
         * 决定使用哪种数学公式计算半径，完全改变螺旋线的形态
         */
        TYPE, 
        
        /** 
         * 生长因子
         * 用于LOGARITHMIC类型螺旋，控制半径增长的速率，值越小增长越快
         */
        GROWTH_FACTOR, 
        
        /** 
         * 多边形边数
         * 用于POLYGON类型和尖角模式，决定多边形的边数
         */
        SIDES, 
        
        /** 
         * 旋转角度
         * 整体旋转螺旋线，不改变形状，只改变方向
         */
        ROTATION, 
        
        /** 
         * 是否使用尖角样式
         * true表示使用多边形近似，false表示使用平滑曲线
         */
        SHARP_EDGED, 
        
        /** 
         * 起始半径
         * 指定螺旋线起点的最小半径，防止螺旋起点重叠在中心点
         */
        START_RADIUS, 
        
        /** 
         * 扩张率
         * 用于半圆螺旋，控制每圈半径的增长系数
         */
        EXPANSION_RATE,
        
        /** 
         * 螺旋系数
         * 用于FIBONACCI和FERMAT螺旋类型：
         * - FIBONACCI: 调整黄金比例的影响强度
         * - FERMAT: 作为公式 r = a·√θ 中的系数 a
         */
        SPIRAL_COEFFICIENT, 
        
        /** 
         * 旋转方向
         * true表示顺时针旋转，false表示逆时针旋转
         */
        CLOCKWISE
    }

    // ===== 核心几何属性 =====
    /** 螺旋中心点 */
    private Vec2d center;          
    /** 起始半径 */
    private double radius;         
    /** 螺旋圈数 */
    private double turns;          
    /** 每圈间距 */
    private double spacing;        
    /** 螺旋类型 */
    private SpiralType type;      
    
    // ===== 形状参数 =====
    /** 生长因子，用于对数螺旋 */
    private double growthFactor;
    /** 多边形边数，用于多边形螺旋 */
    private int sides;            
    /** 旋转角度（弧度） */
    private double rotation;      
    /** 尖角样式：true=使用直线段，false=平滑曲线 */
    private boolean sharpEdged;   
    /** 基础起始半径，用于线性、衰减、半圆和反半圆螺旋 */
    private double startRadius;   
    /** 扩张率，用于半圆螺旋 */
    private double expansionRate; 
    /** 费马螺旋系数 */
    private double spiralCoefficient = 0.5; 
    /** 螺旋方向：true=顺时针，false=逆时针 */
    private boolean clockwise = true;      
    
    // ===== 缓存相关 =====
    /** 缓存的点列表 */
    private List<Vec2d> cachedPoints;  
    /** 脏标记，表示需要重新计算点 */
    private boolean dirty = true;      
    /** 缓存上次计算时的参数值，使用类型安全的参数类 */
    private SpiralParameters cachedParameters;
    /** 存储关键点（每圈的代表点），用于优化操作 */
    private List<Vec2d> keyPoints;

    /**
     * 构造函数
     * @param center 中心点
     * @param radius 起始半径
     * @param turns 圈数
     * @param spacing 间距
     * @param type 螺旋类型
     */
    public SpiralShape(Vec2d center, double radius, double turns, double spacing, SpiralType type) {
        this(center, radius, turns, spacing, type, false);
    }

    /**
     * 构造函数（带尖角样式参数）
     * <p>
     * 参数说明：
     * @param center 中心点坐标，决定螺旋线的位置
     * @param radius 起始半径，决定螺旋线的初始大小（≥0）
     * @param turns 圈数，决定螺旋线的总旋转圈数（>0），过大可能影响性能
     * @param spacing 间距，决定螺旋线的紧密程度（>0）
     * @param type 螺旋类型，决定螺旋线的数学模型（非null）
     * @param sharpEdged 是否使用尖角样式（true=使用直线段，false=平滑曲线）
     * @throws IllegalArgumentException 如果参数不合法（radius<0, turns≤0, spacing≤0, type=null）
     */
    public SpiralShape(Vec2d center, double radius, double turns, double spacing, SpiralType type, boolean sharpEdged) {
        super(center != null ? center : new Vec2d(0, 0));
        
        // 参数验证
        if (radius < 0) throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.negative_radius", radius));
        if (turns <= 0) throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_turns", turns));
        if (spacing <= 0) throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_spacing", spacing));
        if (type == null) throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.type_null"));
        
        // 对可能影响性能的极值参数进行警告
        if (turns * DEFAULT_SEGMENTS_PER_TURN > MAX_TOTAL_SEGMENTS) {
            LOGGER.warn("圈数 {} 过大，可能影响性能。建议圈数不超过 {}", 
                    turns, MAX_TOTAL_SEGMENTS / DEFAULT_SEGMENTS_PER_TURN);
        }
        
        if (radius > 10000) {
            LOGGER.warn("半径 {} 非常大，可能超出预期视图范围", radius);
        }
        
        if (spacing > 1000) {
            LOGGER.warn("间距 {} 过大，可能导致螺旋线超出预期尺寸", spacing);
        } else if (spacing < 0.1) {
            LOGGER.warn("间距 {} 过小，可能导致螺旋线过于紧密", spacing);
        }
        
        this.center = center != null ? center : new Vec2d(0, 0);
        this.radius = radius;
        this.turns = turns;
        this.spacing = spacing;
        this.type = type;
        this.growthFactor = DEFAULT_GROWTH_FACTOR;
        this.sides = DEFAULT_SIDES;
        this.rotation = DEFAULT_ROTATION;
        this.sharpEdged = sharpEdged;
        this.startRadius = 0.0; // 默认基础起始半径为0
        this.expansionRate = 0.0; // 默认扩张率为0
    }

    // Getter和Setter方法
    @Override
    public Vec2d getPosition() {
        // 关键修复：返回center字段，确保getPosition()返回正确的值
        // 这避免了position和center不同步导致的位置漂移问题
        // 参考其他图形（CircleShape、ArcShape）使用center作为位置源
        return center;
    }
    
    public Vec2d getCenter() { return center; }
    /**
     * 设置螺旋线的中心点
     * 
     * @param center 新的中心点，如果为null则使用默认坐标(0,0)
     */
    public void setCenter(Vec2d center) {
        // 如果center为null，使用默认点(0,0)
        Vec2d newCenter = center != null ? center : new Vec2d(0, 0);
        
        if (!Objects.equals(this.center, newCenter)) {
            this.center = newCenter;    // 更新本地字段
            markParameterDirty(SpiralParameter.CENTER);
        }
    }
    public double getRadius() { return radius; }
    public void setRadius(double radius) {
        if (this.radius != radius) {
            if (radius < 0) {
                throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.negative_radius", radius));
            }
            
            // 对极端值进行警告
            if (radius > 10000) {
                LOGGER.warn("设置的半径 {} 非常大，可能超出视图范围", radius);
            } else if (radius < 0.01 && radius > 0) {
                LOGGER.warn("设置的半径 {} 非常小，螺旋线可能难以可见", radius);
            }
            
            this.radius = radius;
            markParameterDirty(SpiralParameter.RADIUS);
        }
    }

    public double getSpacing() { return spacing; }
    /**
     * 设置螺旋线的每圈间距
     * <p>
     * 间距控制螺旋线的紧密程度：
     * - 较小的间距值会产生紧密的螺旋
     * - 较大的间距值会产生松散的螺旋
     * <p>
     * 间距值必须为正数，建议根据预期显示尺寸选择合适的值。
     * 
     * @param spacing 每圈间距（>0）
     * @throws IllegalArgumentException 如果spacing为0或负数
     */
    public void setSpacing(double spacing) {
        if (this.spacing != spacing) {
            if (spacing <= 0) {
                throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_spacing", spacing));
            }
            
            // 对极端值进行警告
            if (spacing > 1000) {
                LOGGER.warn("设置的间距 {} 非常大，螺旋线可能超出预期尺寸", spacing);
            } else if (spacing < 0.1) {
                LOGGER.warn("设置的间距 {} 非常小，螺旋线可能过于紧密", spacing);
            }
            
            this.spacing = spacing;
            markParameterDirty(SpiralParameter.SPACING);
        }
    }
    public SpiralType getType() { return type; }
    /**
     * 设置螺旋线类型
     * 
     * @param type 新的螺旋线类型，决定半径计算公式
     * @throws IllegalArgumentException 如果type为null
     */
    public void setType(SpiralType type) {
        if (this.type != type) {
            if (type == null) {
                throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.type_null"));
            }
            
            // 根据新类型给出参数调整建议
            switch (type) {
                case LOGARITHMIC -> {
                    if (growthFactor > 0.99 || growthFactor < 0.01) {
                        LOGGER.info("切换到LOGARITHMIC类型螺旋，建议将growthFactor设置为0.1-0.9之间以获得良好效果");
                    }
                }
                case POLYGON -> {
                    if (sides < 4) {
                        LOGGER.info("切换到POLYGON类型螺旋，建议将sides设置为至少4以获得良好效果");
                    }
                }
                case FIBONACCI -> {
                    if (spiralCoefficient > 1.0 || spiralCoefficient < 0.1) {
                        LOGGER.info("切换到FIBONACCI类型螺旋，建议将spiralCoefficient设置为0.1-1.0之间");
                    }
                }
                default -> {}
            }
            
            this.type = type;
            markParameterDirty(SpiralParameter.TYPE);
        }
    }
    /**
     * 设置生长因子
     * 
     * @param growthFactor 生长因子（正数）
     * @throws IllegalArgumentException 如果growthFactor为0或负数
     */
    public void setGrowthFactor(double growthFactor) {
        if (this.growthFactor != growthFactor) {
            this.growthFactor = growthFactor;
            markParameterDirty(SpiralParameter.GROWTH_FACTOR);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置生长因子: {}", growthFactor);
            }
        }
    }
    /**
     * 设置多边形螺旋的边数
     * <p>
     * 该参数在以下情况下使用：
     * 1. 当螺旋类型为POLYGON时，决定多边形的边数
     * 2. 当sharpEdged设置为true时，无论类型如何都会影响尖角的数量
     * <p>
     * 边数必须至少为3（三角形），建议通常不超过24，以避免过于复杂的计算。
     * 
     * @param sides 多边形边数（≥3）
     * @throws IllegalArgumentException 如果sides小于3
     */
    public void setSides(int sides) {
        if (this.sides != sides) {
            this.sides = sides;
            markParameterDirty(SpiralParameter.SIDES);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置多边形边数: {}", sides);
            }
        }
    }
    public double getRotation() { return rotation; }
    @Override
    public void setRotation(double rotation) {
        if (this.rotation != rotation) {
            this.rotation = rotation;
            markParameterDirty(SpiralParameter.ROTATION);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置旋转角度: {}", rotation);
            }
        }
    }

    public void setStartRadius(double startRadius) {
        if (this.startRadius != startRadius) {
            this.startRadius = startRadius;
            markParameterDirty(SpiralParameter.START_RADIUS);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置起始半径: {}", startRadius);
            }
        }
    }

    /**
     * 将螺旋线平移指定的偏移量
     * 
     * @param offset 平移偏移量，如果为null，则方法不执行任何操作
     */
    @Override
    public void translate(Vec2d offset) {
        if (offset == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("translate: 偏移量为null，跳过平移操作");
            }
            return;
        }
        
        center = center.add(offset);
        markParameterDirty(SpiralParameter.CENTER);
    }

    /**
     * 绕指定点旋转螺旋线
     * 
     * @param angle 旋转角度（弧度）
     * @param center 旋转中心点，如果为null，则方法不执行任何操作
     */
    @Override
    public void rotate(double angle, Vec2d center) {
        if (center == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("rotate: 旋转中心点为null，跳过旋转操作");
            }
            return;
        }
        
        // 旋转中心点
        this.center = this.center.subtract(center).rotate(angle).add(center);
        
        // 更新总旋转角度
        this.rotation += angle;
        markParameterDirty(SpiralParameter.ROTATION);
    }

    @Override
    public void scale(Vec2d scale, Vec2d centerPoint) {
        // 关键检查/修复点：
        // 1) 缩放工具最终会调用 Shape.scale(Vec2d, center)，如果不重写则会只改 Shape.transform 矩阵；
        //    SpiralShape 的序列化不包含 transform，这会导致“缩放后保存/刷新就丢失”，也容易出现后续交互锚点不一致。
        // 2) SpiralShape 的几何由 center/radius/spacing/... 参数决定，因此应当直接缩放这些参数。
        if (scale == null || centerPoint == null) return;

        // 非均匀缩放对螺旋的严格结果是“椭圆螺旋”，当前实现仍是圆形螺旋参数模型；
        // 因此这里采用“平均缩放因子”保持形态一致（与 transform(AffineTransform) 的思路一致）。
        // 缩放中心点位置（允许非均匀缩放影响位置）
        this.center = centerPoint.add(this.center.subtract(centerPoint).multiply(scale));

        // 对于单轴缩放，使用对应的缩放因子；对于非等比缩放，使用平均缩放因子
        // 这样可以支持单轴缩放，同时对于角点拖拽的非等比缩放保持形状一致
        double scaleFactor = getScaleFactor(scale);

        if (!Double.isFinite(scaleFactor) || scaleFactor <= 0) return;

        // 缩放尺寸相关参数（长度量）
        this.radius *= scaleFactor;
        this.spacing *= scaleFactor;
        this.startRadius *= scaleFactor;

        // 关键修复：不要在等比缩放时清除已有 transform。
        // 如果此前存在非等比变换（椭圆化）信息，清除会导致再次变换时“回退到原始形状”。
        // 等比缩放仅更新参数，保留已有 transform 以支持连续变换累积。
        // 对于非等比缩放，scale()方法不应该被调用，应该使用transform()方法
        // 但为了安全，这里不设置transform矩阵，让transform()方法来处理

        // 标记重新生成点
        markDirty();
    }

    private static double getScaleFactor(Vec2d scale) {
        double scaleFactor;
        if (Math.abs(scale.x - 1.0) < 1e-10) {
            // 只在Y方向缩放（单轴垂直缩放）
            scaleFactor = scale.y;
        } else if (Math.abs(scale.y - 1.0) < 1e-10) {
            // 只在X方向缩放（单轴水平缩放）
            scaleFactor = scale.x;
        } else {
            // 非等比缩放（角点拖拽），使用平均缩放因子保持形状一致
            // 非均匀缩放对螺旋的严格结果是"椭圆螺旋"，当前实现仍是圆形螺旋参数模型；
            // 因此这里采用"平均缩放因子"保持形态一致（与 transform(AffineTransform) 的思路一致）。
            scaleFactor = Math.sqrt(Math.abs(scale.x * scale.y));
        }
        return scaleFactor;
    }

    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 参考圆形和椭圆的实现：直接变换中心点和参数
        // 但是，对于非等比缩放，我们需要使用Shape.transform矩阵来实现单轴缩放效果
        
        // 对于非等比缩放，需要将变换应用到Shape.transform矩阵
        // 这样在绘制时，点会被正确变换，实现单轴缩放效果
        if (!transformMatrix.isUniform()) {
            // 关键理解：
            // 1. getPoints()返回的点是绝对坐标（center.x + currentRadius * cos, center.y + currentRadius * sin）
            // 2. AffineTransform矩阵 T(scaleCenter) * S(scaleFactors) * T(-scaleCenter) 已经包含了完整变换
            // 3. 参考CircleShape的实现：直接变换center和向量，而不是使用decompose()
            
            // 先变换center
            this.center = transformMatrix.transform(this.center);
            
            // 注意：不需要同步更新Shape.position，因为getPosition()已重写为返回center
            // 这样可以避免setPosition()调用move()导致的transform矩阵冲突
            
            // 参考CircleShape：通过变换单位向量来计算缩放和旋转
            // 变换x轴单位向量 (1, 0) 和 y轴单位向量 (0, 1)
            Vec2d transformedX = transformMatrix.transformVector(new Vec2d(1, 0));
            Vec2d transformedY = transformMatrix.transformVector(new Vec2d(0, 1));
            
            // 从变换后的向量构建Matrix3d（相对于center）
            // Matrix3d应该表示：相对于center的缩放和旋转
            // 不包含平移，因为center已经被变换了
            
            // 构建Matrix3d：使用变换后的单位向量作为列向量
            // [transformedX.x, transformedY.x, 0]
            // [transformedX.y, transformedY.y, 0]
            // [0, 0, 1]
            Matrix3d matrix3d = getMatrix3d(transformedX, transformedY);
            setTransform(matrix3d);
            
            // 修复旋转角度计算：使用AffineTransform的旋转增量，而不是从变换后的向量计算绝对角度
            // 关键修复：使用相对旋转增量，而不是绝对角度累加
            // 由于AffineTransform已经包含了所有变换，这里的rotation应该表示相对于初始状态的累积旋转
            // 对于非等比缩放，旋转信息由Matrix3d管理，这里的rotation主要用于参数化表示
            double rotationDelta = transformMatrix.getRotation();
            this.rotation += rotationDelta;
            
            // 对于非等比缩放，仍然使用平均缩放因子来缩放参数
            // 这样可以保持螺旋的基本形状，而实际的拉伸效果通过transform矩阵实现
            double avgScale = Math.sqrt(Math.abs(transformMatrix.getDeterminant()));
            this.radius *= avgScale;
            this.growthFactor *= avgScale;
        } else {
            // 等比缩放：直接修改参数，不使用transform矩阵
            this.center = transformMatrix.transform(this.center);
            
            // 注意：不需要同步更新Shape.position，因为getPosition()已重写为返回center
            
            this.rotation += transformMatrix.getRotation();
            double scale = transformMatrix.getScaleX();
            this.radius *= scale;
            this.growthFactor *= scale;
            // 保留已有transform，避免丢失先前非等比变换导致的形状信息
        }
        
        markParameterDirty(SpiralParameter.RADIUS);
        markParameterDirty(SpiralParameter.GROWTH_FACTOR);
        return this;
    }

    private Matrix3d getMatrix3d(Vec2d transformedX, Vec2d transformedY) {
        Matrix3d matrix3d = new Matrix3d();
        matrix3d.set(0, 0, transformedX.x);
        matrix3d.set(0, 1, transformedY.x);
        matrix3d.set(0, 2, 0);
        matrix3d.set(1, 0, transformedX.y);
        matrix3d.set(1, 1, transformedY.y);
        matrix3d.set(1, 2, 0);
        matrix3d.set(2, 0, 0);
        matrix3d.set(2, 1, 0);
        matrix3d.set(2, 2, 1);

        // 与已有transform组合（新变换在线性空间左乘），确保连续变换可累积
        Matrix3d existingTransform = getTransform();
        if (existingTransform != null) {
            matrix3d = matrix3d.multiply(existingTransform);
        }
        return matrix3d;
    }

    @Override
    public BoundingBox getBoundingBox() {
        // 优化：使用数学分析直接计算包围盒，避免遍历所有顶点
        // 对于大多数螺旋类型，最大半径出现在螺旋的终点
        
        // 1. 计算最大角度
        double maxAngle = turns * TWO_PI;
        
        // 2. 根据不同类型计算理论上的最大半径
        double maxRadius = calculateRadius(maxAngle, this.startRadius > 0 ? this.startRadius : 0.1);
        
        // 3. 对于特殊类型，需要额外考虑振幅影响
        if (type == SpiralType.POLYGON) {
            // 多边形螺旋需要考虑振幅
            maxRadius += POLYGON_AMPLITUDE * spacing;
        } else if (type == SpiralType.LOGARITHMIC) {
            // 对数螺旋可能需要考虑生长因子的影响
            // 这里使用一个保守的估计
            maxRadius *= 1.1; // 增加10%作为安全边界
        }
        
        // 4. 考虑整体旋转的影响
        // 对于非圆形螺旋，旋转可能会影响边界
        // 这里使用一个简化的处理方式：直接使用最大半径作为边界
        // 更精确的方式是计算旋转后的四个极点，但会更复杂
        
        Vec2d extent = new Vec2d(maxRadius, maxRadius);
        Vec2d minPoint = center.subtract(extent);
        Vec2d maxPoint = center.add(extent);

        // 关键修复：
        // SpiralShape 的缩放目前依赖 Shape.scale() 修改 transform 矩阵，
        // 但旧 getBoundingBox() 完全忽略 transform，会导致：
        // - 缩放后选框/命中检测错误
        // - 如果渲染/裁剪依赖包围盒，可能出现"缩放后看起来不对/像没缩放"的问题
        var t = getTransform();
        if (t == null) {
            return new BoundingBox(minPoint, maxPoint);
        }

        // 关键修复：transform矩阵是"相对于center的缩放和旋转"，不包含平移
        // minPoint和maxPoint是基于center的绝对坐标，需要先转换为相对坐标，应用transform，再加回center
        // 将轴对齐包围盒四角应用变换后再求新的AABB
        Vec2d corner1 = new Vec2d(minPoint.x, minPoint.y);
        Vec2d corner2 = new Vec2d(maxPoint.x, minPoint.y);
        Vec2d corner3 = new Vec2d(maxPoint.x, maxPoint.y);
        Vec2d corner4 = new Vec2d(minPoint.x, maxPoint.y);
        
        // 先将角点转换为相对于center的坐标，应用transform，再加回center
        Vec2d c1 = center.add(t.transform(corner1.subtract(center)));
        Vec2d c2 = center.add(t.transform(corner2.subtract(center)));
        Vec2d c3 = center.add(t.transform(corner3.subtract(center)));
        Vec2d c4 = center.add(t.transform(corner4.subtract(center)));

        double minX = Math.min(Math.min(c1.x, c2.x), Math.min(c3.x, c4.x));
        double minY = Math.min(Math.min(c1.y, c2.y), Math.min(c3.y, c4.y));
        double maxX = Math.max(Math.max(c1.x, c2.x), Math.max(c3.x, c4.x));
        double maxY = Math.max(Math.max(c1.y, c2.y), Math.max(c3.y, c4.y));

        return new BoundingBox(new Vec2d(minX, minY), new Vec2d(maxX, maxY));
    }

    /**
     * 检查点是否在螺旋线上
     * 使用多级测试加速检测：边界框快速测试 -> 关键点距离测试 -> 线段距离测试
     * @param point 要检测的点
     * @return 如果点在螺旋线上（考虑一定容差）返回true
     */
    @Override
    public boolean contains(Vec2d point) {
        if (point == null) return false;
        
        // 优化：使用距离阈值快速判断
        // 1. 计算点到中心的距离
        double distanceFromCenter = point.distance(center);
        
        // 2. 计算螺旋在该角度下的理论半径
        double angleFromCenter = Math.atan2(point.y - center.y, point.x - center.x);
        double normalizedAngle = (angleFromCenter - rotation + TWO_PI) % TWO_PI;
        if (normalizedAngle < 0) normalizedAngle += TWO_PI;
        
        // 3. 计算该角度下的螺旋半径
        double spiralRadius = calculateRadius(normalizedAngle, startRadius);
        
        // 4. 使用距离容差判断是否在螺旋线上
        double tolerance = DISTANCE_TOLERANCE_FACTOR * spacing;
        double distanceDifference = Math.abs(distanceFromCenter - spiralRadius);
        
        // 如果距离差异在容差范围内，认为点在螺旋线上
        if (distanceDifference <= tolerance) {
            return true;
        }
        
        // 5. 对于特殊类型，可能需要更精确的检查
        if (type == SpiralType.POLYGON) {
            // 多边形螺旋需要考虑振幅影响
            double amplitude = POLYGON_AMPLITUDE * spacing;
            double adjustedRadius = spiralRadius + amplitude * Math.sin(sides * normalizedAngle);
            double adjustedDistanceDifference = Math.abs(distanceFromCenter - adjustedRadius);
            return adjustedDistanceDifference <= tolerance;
        }
        
        return false;
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (point == null) return null;
        List<Vec2d> points = getPoints();
        if (points.size() < 2) return center;

        // 优化：使用角度定位快速定位搜索区域
        // 1. 计算目标点相对于螺旋中心的角度和距离
        double angleFromCenter = Math.atan2(point.y - center.y, point.x - center.x);
        double distanceFromCenter = point.distance(center);
        
        // 2. 根据角度估算索引位置
        // 计算每步角度增量
        double totalAngle = turns * TWO_PI;
        double angleStep = totalAngle / (points.size() - 1);
        
        // 将角度标准化到[0, totalAngle]范围
        double normalizedAngle = (angleFromCenter - rotation + TWO_PI) % TWO_PI;
        if (normalizedAngle < 0) normalizedAngle += TWO_PI;
        
        // 估算对应的索引
        int estimatedIndex = (int) (normalizedAngle / angleStep);
        estimatedIndex = Math.max(0, Math.min(points.size() - 2, estimatedIndex));

        // 3. 定义局部搜索窗口
        int searchRadius = Math.min(50, points.size() / 10); // 动态调整搜索半径
        int startIndex = Math.max(0, estimatedIndex - searchRadius);
        int endIndex = Math.min(points.size() - 2, estimatedIndex + searchRadius);

        // 4. 在窗口内进行精确搜索
        ClosestPointResult bestResult = null;
        double bestDistance = Double.MAX_VALUE;
        
        for (int i = startIndex; i <= endIndex; i++) {
            ClosestPointResult result = findClosestPointOnSegment(point, points.get(i), points.get(i + 1));
            if (result.distance() < bestDistance) {
                bestResult = result;
                bestDistance = result.distance();
            }
        }
        
        // 5. 如果局部搜索效果不好，进行全量扫描作为回退
        // 这种情况通常发生在螺旋起点或终点附近
        if (bestResult == null || bestDistance > distanceFromCenter * 0.5) {
            LOGGER.debug("局部搜索效果不佳，进行全量扫描，目标点距离中心: {}", distanceFromCenter);
            
            // 全量扫描
            for (int i = 0; i < points.size() - 1; i++) {
                ClosestPointResult result = findClosestPointOnSegment(point, points.get(i), points.get(i + 1));
                if (bestResult == null || result.distance() < bestResult.distance()) {
                    bestResult = result;
                }
            }
        }
        
        return bestResult != null ? bestResult.closestPoint() : center;
    }

    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(center);
        return points;
    }

    /**
     * 设置控制点位置
     * 控制点索引:
     * - 0: 中心点
     * 
     * @param index 控制点索引
     * @param point 新的控制点坐标，如果为null且为中心点，则使用默认坐标(0,0)
     */
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index == 0) {
            if (point == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("setControlPoint: 中心点为null，使用默认点(0,0)");
                }
                point = new Vec2d(0, 0);
            }
            
            Vec2d delta = point.subtract(center);
            translate(delta);
        } else {
            LOGGER.warn("setControlPoint: 无效的控制点索引 {}", index);
        }
    }

    @Override
    public void draw(DrawContext context) {
        List<Vec2d> points = getPoints();
        if (points.size() < 2) return;

        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        if (!activeStyle.getLineStyle().isVisible()) return;

        // 关键修复：transform矩阵是"相对于center的缩放和旋转"，不包含平移
        // getPoints()返回的点是绝对坐标（center.x + radius * cos, center.y + radius * sin）
        // 因此需要先将点转换为相对于center的坐标，应用transform，再加回center
        Matrix3d transform = getTransform();
        boolean hasTransform = false;
        if (transform != null) {
            hasTransform = !(Math.abs(transform.get(0, 0) - 1.0) < 1e-10 && 
                             Math.abs(transform.get(0, 1)) < 1e-10 && 
                             Math.abs(transform.get(0, 2)) < 1e-10 &&
                             Math.abs(transform.get(1, 0)) < 1e-10 && 
                             Math.abs(transform.get(1, 1) - 1.0) < 1e-10 && 
                             Math.abs(transform.get(1, 2)) < 1e-10 &&
                             Math.abs(transform.get(2, 0)) < 1e-10 && 
                             Math.abs(transform.get(2, 1)) < 1e-10 && 
                             Math.abs(transform.get(2, 2) - 1.0) < 1e-10);
        }
        
        if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                if (hasTransform) {
                    // 关键修复：先将点转换为相对于center的坐标，应用transform，再加回center
                    Vec2d relativeP1 = p1.subtract(center);
                    Vec2d relativeP2 = p2.subtract(center);
                    p1 = center.add(transform.transform(relativeP1));
                    p2 = center.add(transform.transform(relativeP2));
                }
                context.drawLine(p1, p2, lineStyle);
            }
        } else {
            // 如果没有LineStyle，使用默认颜色绘制
            Color color = new Color(activeStyle.getLineStyle().getColor().getRGB());
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                if (hasTransform) {
                    // 关键修复：先将点转换为相对于center的坐标，应用transform，再加回center
                    Vec2d relativeP1 = p1.subtract(center);
                    Vec2d relativeP2 = p2.subtract(center);
                    p1 = center.add(transform.transform(relativeP1));
                    p2 = center.add(transform.transform(relativeP2));
                }
                context.drawLine(p1, p2, color);
            }
        }
    }

    /**
     * 标记形状为脏状态，需要重新计算
     * 此方法会触发螺旋线点的重新生成
     */
    @Override
    public void markDirty() {
        dirty = true;
        cachedPoints = null;
        keyPoints = null;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("螺旋线标记为脏");
        }
    }
    
    /**
     * 标记特定参数变化导致的脏状态
     * 此方法跟踪哪些参数发生了变化，用于优化更新过程
     * 
     * @param parameter 变化的参数
     */
    private void markParameterDirty(SpiralParameter parameter) {
        dirty = true;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("标记参数为脏: {}", parameter);
        }
    }
    
    /**
     * 检查参数是否发生变化
     * @return 是否有参数变化
     */
    private boolean hasParameterChanged() {
        if (cachedParameters == null) {
            return true;
        }
        
        boolean changed = !cachedParameters.matches(this);
        if (changed) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("螺旋线参数变化: {}", cachedParameters.getChangeDescription(this));
            }
        }
        return changed;
    }

    /**
     * 生成螺旋线的所有点
     * 处理缓存逻辑和点生成策略选择
     * 实现单一职责，将缓存管理与点生成逻辑分离
     * 
     * @return 螺旋线的点列表
     */
    @Override
    public List<Vec2d> getPoints() {
        return generateSpiralPoints();
    }
    
    /**
     * 生成螺旋线的所有点
     * 处理缓存逻辑和点生成策略选择
     * 实现单一职责，将缓存管理与点生成逻辑分离
     * 
     * @return 螺旋线的点列表
     */
    private List<Vec2d> generateSpiralPoints() {
        if (cachedPoints != null && !dirty && !hasParameterChanged()) {
            return cachedPoints;
        }

        long startTime = System.currentTimeMillis();
        List<Vec2d> result;

        if (canUpdateIncrementally()) {
            LOGGER.debug("使用增量更新生成螺旋线点");
            result = updatePointsIncrementally();
        } else {
            LOGGER.debug("完全重新生成螺旋线点");
            result = generatePointsFromScratch();
        }

        // 更新缓存
        this.cachedPoints = result;
        this.cachedParameters = new SpiralParameters(this);
        this.dirty = false;
        
        long endTime = System.currentTimeMillis();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("螺旋线点生成完成 - 耗时: {}ms, 点数: {}", (endTime - startTime), result.size());
        }
        
        return result;
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        // 简单实现：将螺旋线分解为线段，检查每个线段与其他形状的交点
        List<Vec2d> intersections = new ArrayList<>();
        List<Vec2d> points = getPoints();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            List<Vec2d> segmentIntersections = other.getIntersectionsWithPolyline(List.of(p1, p2));
            intersections.addAll(segmentIntersections);
        }

        return intersections;
    }

    @Override
    public Shape createOffset(double distance) {
        // 创建一个新的螺旋线，其半径增加了给定的距离，并保留所有其他属性
        SpiralShape offsetSpiral = new SpiralShape(center, radius + distance, turns, spacing, type, sharpEdged);
        offsetSpiral.setGrowthFactor(growthFactor);
        offsetSpiral.setSides(sides);
        offsetSpiral.setRotation(rotation);
        offsetSpiral.setStartRadius(startRadius);
        offsetSpiral.setExpansionRate(expansionRate);
        
        // 如果有样式，也复制样式
        if (getStyle() != null) {
            offsetSpiral.setStyle(getStyle());
        }
        
        return offsetSpiral;
    }

    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> intersections = new ArrayList<>();
        List<Vec2d> spiralPoints = getPoints();
        
        // 检查每个螺旋线段与多段线的每个线段是否相交
        for (int i = 0; i < spiralPoints.size() - 1; i++) {
            Vec2d sp1 = spiralPoints.get(i);
            Vec2d sp2 = spiralPoints.get(i + 1);
            
            for (int j = 0; j < points.size() - 1; j++) {
                Vec2d pp1 = points.get(j);
                Vec2d pp2 = points.get(j + 1);
                
                intersections.addAll(GeometryUtils.segmentIntersection(sp1, sp2, pp1, pp2));
            }
        }
        
        return intersections;
    }

    @Override
    public String serialize() {
        try {
            // 使用Gson进行专业序列化
            SpiralShapeData data = new SpiralShapeData(this);
            return GSON.toJson(data);
        } catch (Exception e) {
            LOGGER.error("Gson序列化失败，回退到旧格式", e);
            // 回退到旧格式
            return serializeOldFormat();
        }
    }
    
    @Override
    public void deserialize(String data) {
        if (data == null || data.trim().isEmpty()) {
            LOGGER.warn("反序列化数据为空");
            return;
        }
        
        try {
            // 尝试使用Gson反序列化
            SpiralShapeData shapeData = GSON.fromJson(data, SpiralShapeData.class);
            if (shapeData != null) {
                shapeData.applyTo(this);
                markDirty();
                LOGGER.debug("Gson反序列化成功");
                return;
            }
        } catch (JsonSyntaxException e) {
            LOGGER.debug("Gson反序列化失败，尝试旧格式: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Gson反序列化异常，尝试旧格式", e);
        }
        
        // 回退到旧格式解析
        tryDeserializeOldFormat(data);
    }
    
    /**
     * 旧格式序列化（兼容性）
     */
    private String serializeOldFormat() {
        return "spiral:" +
                "center=" + center.x + "," + center.y + ";" +
                "radius=" + radius + ";" +
                "turns=" + turns + ";" +
                "spacing=" + spacing + ";" +
                "type=" + type.name() + ";" +
                "growthFactor=" + growthFactor + ";" +
                "sides=" + sides + ";" +
                "rotation=" + rotation + ";" +
                "sharpEdged=" + sharpEdged + ";" +
                "startRadius=" + startRadius + ";" +
                "expansionRate=" + expansionRate + ";" +
                "spiralCoefficient=" + spiralCoefficient + ";" +
                "clockwise=" + clockwise;
    }
    
    /**
     * 尝试解析旧版本的序列化格式
     * @param data 序列化数据
     */
    private void tryDeserializeOldFormat(String data) {
        try {
            // 初始化默认值
            center = new Vec2d(0, 0);
            radius = 0;
            turns = DEFAULT_TURNS;
            spacing = DEFAULT_SPACING;
            type = SpiralType.LINEAR;
            growthFactor = DEFAULT_GROWTH_FACTOR;
            sides = DEFAULT_SIDES;
            rotation = DEFAULT_ROTATION;
            sharpEdged = false;
            startRadius = 0.0;
            expansionRate = 0.0;
            spiralCoefficient = 0.5;
            clockwise = true;
            
            // 尝试解析JSON风格格式
            if (data.trim().startsWith("{")) {
                tryDeserializeJsonFormat(data);
            } else {
                // 兼容旧格式
                tryDeserializeLegacyFormat(data);
            }
            
            // 标记为需要重新计算
            markDirty();
        } catch (Exception e) {
            LOGGER.error("解析旧格式螺旋线数据失败", e);
        }
    }
    
    /**
     * 解析JSON风格格式
     */
    private void tryDeserializeJsonFormat(String data) {
        try {
            // 移除花括号
            String content = data.substring(1, data.length() - 1);
            String[] pairs = content.split(",");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length != 2) continue;
                
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                
                switch (key) {
                    case "centerX" -> center = new Vec2d(Double.parseDouble(value), center.y);
                    case "centerY" -> center = new Vec2d(center.x, Double.parseDouble(value));
                    case "radius" -> radius = Double.parseDouble(value);
                    case "turns" -> turns = Double.parseDouble(value);
                    case "spacing" -> spacing = Double.parseDouble(value);
                    case "type" -> type = SpiralType.valueOf(value);
                    case "sharpEdged" -> sharpEdged = Boolean.parseBoolean(value);
                    case "growthFactor" -> growthFactor = Double.parseDouble(value);
                    case "sides" -> sides = Integer.parseInt(value);
                    case "rotation" -> rotation = Double.parseDouble(value);
                    case "startRadius" -> startRadius = Double.parseDouble(value);
                    case "expansionRate" -> expansionRate = Double.parseDouble(value);
                    case "spiralCoefficient" -> spiralCoefficient = Double.parseDouble(value);
                    case "clockwise" -> clockwise = Boolean.parseBoolean(value);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析JSON格式螺旋线数据失败: {}", e.getMessage());
            // 回退到旧格式解析
            tryDeserializeLegacyFormat(data);
        }
    }
    
    /**
     * 尝试解析最旧版本的序列化格式
     */
    private void tryDeserializeLegacyFormat(String data) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 7) {
                center = new Vec2d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                radius = Double.parseDouble(parts[2]);
                turns = Double.parseDouble(parts[3]);
                spacing = Double.parseDouble(parts[4]);
                type = SpiralType.values()[Integer.parseInt(parts[5])];
                sharpEdged = parts[6].equals("1");
            } else if (parts.length == 6) {
                center = new Vec2d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                radius = Double.parseDouble(parts[2]);
                turns = Double.parseDouble(parts[3]);
                spacing = Double.parseDouble(parts[4]);
                type = SpiralType.values()[Integer.parseInt(parts[5])];
                sharpEdged = false;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            LOGGER.error("解析最旧版螺旋线数据时出错: {}", e.getMessage());
        }
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        // 创建一个新的螺旋线，其终点最接近给定点
        List<Vec2d> points = getPoints();
        double minDistance = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int i = 0; i < points.size(); i++) {
            double distance = points.get(i).distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }

        if (closestIndex >= 0) {
            double newTurns = (closestIndex * turns) / points.size();
            return new SpiralShape(center, radius, newTurns, spacing, type);
        }

        return this;
    }

    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        // 返回点到螺旋线的最小距离（不考虑正负）
        return point.distance(getClosestPoint(point));
    }

    @Override
    public Vec2d getTangentAt(Vec2d point) {
        List<Vec2d> points = getPoints();
        if (points.size() < 2) {
            return new Vec2d(1, 0);
        }

        Vec2d tangent = new Vec2d(1, 0);
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            ClosestPointResult result = findClosestPointOnSegment(point, p1, p2);
            if (result.distance() < minDistance) {
                minDistance = result.distance();
                tangent = p2.subtract(p1).normalize();
            }
        }

        return tangent;
    }

    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }

    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 扩展螺旋线并检查交点
        SpiralShape extended = (SpiralShape) extend(point, maxDistance);
        return extended.getIntersectionPoints(other);
    }

    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> points = getPoints();
        if (points.size() < 2) {
            return new ArrayList<>();
        }
        return List.of(points.getFirst(), points.getLast());
    }

    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        // 暂时返回空列表，因为螺旋线分割比较复杂
        return new ArrayList<>();
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        // 通过增加转数来延伸螺旋线，并保留所有其他属性
        double additionalTurns = distance / (TWO_PI * spacing);
        SpiralShape extendedSpiral = new SpiralShape(center, radius, turns + additionalTurns, spacing, type, sharpEdged);
        extendedSpiral.setGrowthFactor(growthFactor);
        extendedSpiral.setSides(sides);
        extendedSpiral.setRotation(rotation);
        extendedSpiral.setStartRadius(startRadius);
        
        // 如果有样式，也复制样式
        if (getStyle() != null) {
            extendedSpiral.setStyle(getStyle());
        }
        
        return extendedSpiral;
    }

    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 计算到目标点的距离，并使用该距离延伸螺旋线
        double distance = point.distance(toPoint);
        return extend(point, distance);
    }

    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }

    /**
     * 查找点在线段上的最近点
     */
    private ClosestPointResult findClosestPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        // 添加空值检查
        if (segStart == null || segEnd == null) {
            throw new NullPointerException(PlotI18n.error("error.plot.spiral.validation.null_segment_endpoint"));
        }
        
        if (point == null) {
            LOGGER.warn("findClosestPointOnSegment: 查询点为null，返回线段起点");
            return new ClosestPointResult(segStart, 0);
        }
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        if (c1 <= 0) {
            return new ClosestPointResult(segStart, segStart.distance(point));
        }
        
        double c2 = v.dot(v);
        if (c2 <= c1) {
            return new ClosestPointResult(segEnd, segEnd.distance(point));
        }
        
        double b = c1 / c2;
        Vec2d pb = segStart.add(v.multiply(b));
        return new ClosestPointResult(pb, pb.distance(point));
    }

    /**
     * 设置费马螺旋系数
     * <p>
     * 该系数用于调整螺旋的展开速度，主要影响以下类型：
     * - FIBONACCI: 调整黄金比例的影响强度，控制螺旋增长的速率
     * - FERMAT: 作为公式 r = a·√θ 中的系数 a，控制螺旋展开的速度
     * <p>
     * 参数效果：
     * - 较小的值（接近0）：螺旋增长缓慢
     * - 中等值（0.5左右）：接近自然界中的螺旋形态
     * - 较大的值（>1）：螺旋增长迅速
     * 
     * @param spiralCoefficient 螺旋系数（建议在0.1至2.0之间）
     * @throws IllegalArgumentException 如果spiralCoefficient为0或负数
     */
    public void setSpiralCoefficient(double spiralCoefficient) {
        if (this.spiralCoefficient != spiralCoefficient) {
            // 验证：确保系数为正数
            if (spiralCoefficient <= 0) {
                throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_coefficient", spiralCoefficient));
            }
            
            // 对极值进行警告
            if (spiralCoefficient > 3.0) {
                LOGGER.warn("螺旋系数 {} 过大，可能导致螺旋急速扩张", spiralCoefficient);
            } else if (spiralCoefficient < 0.1) {
                LOGGER.warn("螺旋系数 {} 过小，可能导致螺旋几乎不扩张", spiralCoefficient);
            }
            
            this.spiralCoefficient = spiralCoefficient;
            markParameterDirty(SpiralParameter.SPIRAL_COEFFICIENT);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置螺旋系数: {}", spiralCoefficient);
            }
        }
    }
    
    /**
     * 设置螺旋线的扩张率
     * <p>
     * 扩张率用于半圆螺旋类型，控制每圈半径的增长系数：
     * - 负值：螺旋逐渐收缩
     * - 0：螺旋保持一致大小
     * - 正值：螺旋逐渐扩大
     * 
     * @param expansionRate 扩张率（建议范围-1.0到10.0）
     */
    public void setExpansionRate(double expansionRate) {
        if (this.expansionRate != expansionRate) {
            // 对极值进行警告
            if (expansionRate > 10.0) {
                LOGGER.warn("扩张率 {} 非常大，可能导致螺旋增长过快", expansionRate);
            } else if (expansionRate < -1.0) {
                LOGGER.warn("扩张率 {} 是较大的负值，可能导致螺旋迅速收缩", expansionRate);
            }
            
            this.expansionRate = expansionRate;
            markParameterDirty(SpiralParameter.EXPANSION_RATE);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置扩张率: {}", expansionRate);
            }
        }
    }

    /**
     * 设置螺旋方向
     * @param clockwise true=顺时针，false=逆时针
     */
    public void setClockwise(boolean clockwise) {
        if (this.clockwise != clockwise) {
            this.clockwise = clockwise;
            markParameterDirty(SpiralParameter.CLOCKWISE);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置旋转方向: {}", clockwise ? "顺时针" : "逆时针");
            }
        }
    }

    /**
     * 获取螺旋方向
     * @return true=顺时针，false=逆时针
     */
    public boolean isClockwise() {
        return clockwise;
    }
    
    /**
     * 设置螺旋线的圈数
     * 
     * @param turns 新的圈数，必须大于0
     * @throws IllegalArgumentException 如果圈数不大于0
     */
    public void setTurns(double turns) {
        if (this.turns != turns) {
            if (turns <= 0) {
                throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_turns", turns));
            }
            
            // 对极端值进行警告
            if (turns * DEFAULT_SEGMENTS_PER_TURN > MAX_TOTAL_SEGMENTS) {
                LOGGER.warn("设置的圈数 {} 过大，可能影响性能。建议圈数不超过 {}", 
                        turns, MAX_TOTAL_SEGMENTS / DEFAULT_SEGMENTS_PER_TURN);
            }
            
            this.turns = turns;
            markParameterDirty(SpiralParameter.TURNS);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置圈数: {}", turns);
            }
        }
    }
    
    /**
     * 设置是否使用尖角样式
     * 
     * @param sharpEdged true表示使用多边形近似，false表示使用平滑曲线
     */
    public void setSharpEdged(boolean sharpEdged) {
        if (this.sharpEdged != sharpEdged) {
            this.sharpEdged = sharpEdged;
            markParameterDirty(SpiralParameter.SHARP_EDGED);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("设置尖角样式: {}", sharpEdged ? "开启" : "关闭");
            }
        }
    }

    /**
     * 计算给定角度的半径
     * 根据不同的螺旋类型使用不同的公式计算半径
     * 
     * @param angle 角度（弧度）
     * @param initialRadius 初始半径
     * @return 计算得到的半径
     */
    private double calculateRadius(double angle, double initialRadius) {
        // 预计算常用值，减少重复计算
        double anglePerTurn = angle / TWO_PI;
        
        return switch (type) {
            // 线性螺旋: r = r₀ + k·θ
            // 半径与角度成正比线性增长
            case LINEAR -> {
                // 对于线性螺旋，直接使用startRadius作为基础半径
                // 避免双重计算：startRadius + initialRadius
                double baseRadius = this.startRadius > 0 ? this.startRadius : initialRadius;
                yield baseRadius + spacing * anglePerTurn;
            }
            
            // 对数螺旋: r = a·e^(b·θ)
            // 半径以指数方式增长，常见于自然界
            case LOGARITHMIC -> {
                // 修复：直接使用基础半径，不缩小
                double b = Math.log(1.0 / growthFactor); // 将生长因子转换为对数螺旋参数
                // 实现标准对数螺旋公式
                yield (this.startRadius > 0 ? this.startRadius : initialRadius) * Math.exp(b * anglePerTurn);
            }
            
            // 半圆螺旋: 不断增大的半圆首尾相切连接
            // 每个半圆的半径都比前一个大，形成连续的螺旋
            case SEMICIRCLE -> {
                // 改为以“半圈”为步长的线性增长（连续，无跳变）
                // r(θ) = base + expansionRate * spacing * (θ / π)
                // 其中 θ/π 表示已走过的半圈数
                double baseRadius = this.startRadius > 0 ? this.startRadius : initialRadius;
                double halfTurnsProgress = angle / Math.PI;
                yield baseRadius + expansionRate * spacing * halfTurnsProgress;
            }

            // 斐波那契螺旋: r = a·e^(b·θ) 其中b与黄金比例相关
            // 任意两个相邻1/4圈的半径比近似等于黄金比例
            case FIBONACCI -> {
                // 计算斐波那契螺旋的增长因子，基于黄金比例
                double baseRadius = this.startRadius > 0 ? this.startRadius : initialRadius;
                double goldenLogFactor = Math.log(GOLDEN_RATIO) / (Math.PI / 2);
                // 应用斐波那契螺旋公式，使用spiralCoefficient调整紧密度
                yield baseRadius * Math.exp(goldenLogFactor * angle * spiralCoefficient);
            }
            
            // 多边形螺旋: r = a + b·θ + c·cos(n·θ)
            // 基础为线性螺旋，叠加余弦函数产生边缘效果
            case POLYGON -> {
                // 基础线性螺旋部分
                double baseRadius = this.startRadius > 0 ? this.startRadius : initialRadius;
                double baseValue = baseRadius + spacing * anglePerTurn;
                // 边缘调制部分，使用余弦函数产生波动
                double edgeModulation = POLYGON_AMPLITUDE * spacing * 
                        Math.cos(sides * angle) * (1 + anglePerTurn * 0.1);
                // 合并基础部分和调制部分
                yield baseValue + edgeModulation;
            }
            
            // 费马螺旋: r = a·√θ
            // 使用标准费马螺旋公式，半径与角度的平方根成正比
            case FERMAT -> {
                // 应用标准费马螺旋公式: r = a·√θ
                // spiralCoefficient作为系数a，控制螺旋展开的速度
                double baseRadius = this.startRadius > 0 ? this.startRadius : initialRadius;
                double a = spiralCoefficient;
                // 半径计算不应受方向影响，始终使用正角度值
                // 方向由computeSpiralPoints中的finalAngle处理
                double fermatRadius = a * Math.sqrt(angle);
                // 添加起始半径，并应用间距因子调整
                yield baseRadius + fermatRadius * spacing;
            }
        };
    }

    /**
     * 检查是否可以进行增量更新
     * 简化了原有的复杂参数变更检测逻辑
     */
    private boolean canUpdateIncrementally() {
        if (cachedParameters == null || cachedPoints == null || cachedPoints.isEmpty()) {
            return false;
        }
        
        // 检查除 center 和 rotation 外的其他塑形参数是否未变
        boolean shapeParamsUnchanged = cachedParameters.radius == radius &&
               cachedParameters.turns == turns &&
               cachedParameters.spacing == spacing &&
               cachedParameters.type == type &&
               cachedParameters.growthFactor == growthFactor &&
               cachedParameters.sides == sides &&
               cachedParameters.sharpEdged == sharpEdged &&
               cachedParameters.startRadius == startRadius &&
               cachedParameters.expansionRate == expansionRate &&
               cachedParameters.spiralCoefficient == spiralCoefficient &&
               cachedParameters.clockwise == clockwise;

        if (!shapeParamsUnchanged) {
            return false;
        }

        // 如果只有 center 或 rotation 变了，则可以增量更新
        boolean centerChanged = !Objects.equals(cachedParameters.center, center);
        boolean rotationChanged = cachedParameters.rotation != rotation;

        // 只有这两者之一或两者都变了，且其他参数没变，才增量更新
        return centerChanged || rotationChanged;
    }

    /**
     * 增量更新方法，处理平移和旋转的组合情况
     */
    private List<Vec2d> updatePointsIncrementally() {
        Vec2d oldCenter = cachedParameters.center;
        double oldRotation = cachedParameters.rotation;

        Vec2d offset = center.subtract(oldCenter);
        double rotationDiff = rotation - oldRotation;

        List<Vec2d> newPoints = new ArrayList<>(cachedPoints.size());
        for (Vec2d p : cachedPoints) {
            // 1. 先应用旧的旋转的逆操作，回到无旋转状态
            Vec2d pUnrotated = p.subtract(oldCenter).rotate(-oldRotation);
            // 2. 应用新的旋转
            Vec2d pRotated = pUnrotated.rotate(rotation);
            // 3. 平移到新中心
            newPoints.add(pRotated.add(center));
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("螺旋线增量更新完成 - 中心变化: {} -> {}, 旋转变化: {} -> {}", 
                oldCenter, center, oldRotation, rotation);
        }
        
        return newPoints;
    }







    /**
     * 根据给定的最大半径，反向求解所需的圈数。
     * 此方法将螺旋的数学计算内聚在本类中，避免外部类重复实现。
     * 
     * @param maxRadius 期望达到的最大半径
     * @return 计算出的圈数
     */
    public double solveTurnsForRadius(double maxRadius) {
        if (maxRadius <= startRadius) {
            return 0.1; // 最小圈数
        }

        // 逆向运算与 calculateTurnsForRadius 中的逻辑完全一致
        return switch (type) {
            case LINEAR -> {
                // SpiralShape: r = baseRadius + spacing * anglePerTurn
                // 其中 anglePerTurn = angle / TWO_PI，所以 angle = turns * TWO_PI
                // 因此 r = baseRadius + spacing * turns
                // 逆运算: turns = (r - baseRadius) / spacing
                if (spacing > 0) {
                    yield Math.max(0.1, (maxRadius - startRadius) / spacing);
                } else {
                    yield Math.max(0.1, DEFAULT_TURNS); // 避免除以零，确保为正数
                }
            }
            
            case LOGARITHMIC -> {
                // SpiralShape: r = a * exp(b * anglePerTurn)
                // 其中 a = baseRadius, b = log(1.0 / growthFactor)
                // 逆运算: anglePerTurn = log(r / a) / b
                // 由于 anglePerTurn = turns，所以 turns = log(r / a) / b
                double a = startRadius > 0 ? startRadius : 0.1; // 修复：直接使用基础半径，不缩小
                double b = Math.log(1.0 / growthFactor); // 与 SpiralShape 保持一致
                
                if (b != 0 && maxRadius > a) {
                    double calculatedTurns = Math.log(maxRadius / a) / b;
                    // 确保圈数为正数，避免异常
                    yield Math.max(0.1, calculatedTurns);
                }
                yield Math.max(0.1, turns); // 无法求解时返回当前值，但确保为正数
            }
            
            case FERMAT -> {
                // SpiralShape: r = baseRadius + a * sqrt(angle) * spacing
                // 其中 a = spiralCoefficient
                // 逆运算: sqrt(angle) = (r - baseRadius) / (a * spacing)
                // 因此 angle = [(r - baseRadius) / (a * spacing)]^2
                // 由于 angle = turns * TWO_PI，所以 turns = angle / TWO_PI
                double a = spiralCoefficient;
                if (a > 0 && spacing > 0 && maxRadius > startRadius) {
                    double angle = Math.pow((maxRadius - startRadius) / (a * spacing), 2);
                    yield Math.max(0.1, angle / TWO_PI);
                } else {
                    yield Math.max(0.1, DEFAULT_TURNS);
                }
            }
            
            case FIBONACCI -> {
                // SpiralShape: r = baseRadius * exp(goldenLogFactor * angle * spiralCoefficient)
                // 其中 goldenLogFactor = log(GOLDEN_RATIO) / (PI / 2)
                // 逆运算: angle = log(r / baseRadius) / (goldenLogFactor * spiralCoefficient)
                double goldenLogFactor = Math.log(GOLDEN_RATIO) / (Math.PI / 2);
                if (startRadius > 0 && spiralCoefficient > 0 && maxRadius > startRadius) {
                    double angle = Math.log(maxRadius / startRadius) / (goldenLogFactor * spiralCoefficient);
                    yield Math.max(0.1, angle / TWO_PI);
                } else {
                    yield Math.max(0.1, DEFAULT_TURNS);
                }
            }
            
            case SEMICIRCLE -> {
                // r = startRadius + (expansionRate * spacing) * (angle / π)
                // Δr = (expansionRate * spacing) * (angle / π)
                // angle = 2π * turns => Δr = (expansionRate * spacing) * (2 * turns)
                // 解得 turns = Δr / (2 * expansionRate * spacing)
                double deltaR = maxRadius - startRadius;
                double denom = 2.0 * Math.max(1e-6, expansionRate) * Math.max(1e-6, spacing);
                yield Math.max(0.1, deltaR / denom);
            }
            case POLYGON -> {
                // 线性近似
                if (spacing > 0) {
                    yield Math.max(0.1, (maxRadius - startRadius) / spacing);
                } else {
                    yield Math.max(0.1, DEFAULT_TURNS);
                }
            }
        };
    }

    /**
     * 根据螺旋参数计算合适的点数
     * 动态调整分段密度以避免点数过多
     */
    private int calculatePointCount() {
        // 基础点数：每圈的分段数
        int baseSegments = sharpEdged ? SHARP_EDGED_SEGMENTS_PER_TURN : DEFAULT_SEGMENTS_PER_TURN;
        int totalSegments = (int) (turns * baseSegments);
        
        // 限制最大点数以避免性能问题
        if (totalSegments > MAX_TOTAL_SEGMENTS) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("螺旋线分段数过多 ({}), 已调整为每圈 {} 个分段，总计 {} 个分段",
                        totalSegments, MAX_TOTAL_SEGMENTS / (int) turns, MAX_TOTAL_SEGMENTS);
            }
            totalSegments = MAX_TOTAL_SEGMENTS;
        }
        
        return Math.max(3, totalSegments); // 至少3个点
    }
    
    /**
     * 从头生成所有点
     * <p>
     * 该方法负责：
     * 1. 委托点的实际计算给computeSpiralPoints
     * 2. 更新缓存和相关状态
     * 3. 处理关键点更新
     * 4. 记录性能日志
     * 
     * @return 新生成的螺旋线点列表
     */
    private List<Vec2d> generatePointsFromScratch() {
        long startTime = System.currentTimeMillis();
        
        List<Vec2d> result = computeSpiralPoints();
        
        long endTime = System.currentTimeMillis();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("螺旋线生成完成：耗时={}ms, 最终点数={}", (endTime - startTime), result.size());
        }
        
        return result;
    }
    
    /**
     * 计算螺旋线的所有点
     * 该方法专注于点的计算逻辑，不处理缓存和状态更新
     * 
     * @return 新生成的螺旋线点列表
     */
    private List<Vec2d> computeSpiralPoints() {
        // 尖角样式：生成多边形折线螺旋（Affinity 风格）
        if (sharpEdged) {
            return computeSharpEdgedPolygonSpiral();
        }

        // 计算适当的点数，自动调整分段密度（平滑曲线）
        int numPoints = calculatePointCount();
        double angleStep = (turns * TWO_PI) / numPoints;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("计算螺旋线点：类型={}, 圈数={}, 总点数={}, 每圈点数≈{}",
                    type, turns, numPoints, numPoints / turns);
        }

        List<Vec2d> result = new ArrayList<>(numPoints + 1);

        double initialRadius = startRadius > 0 ? startRadius : 0.1;
        double finalRotation = rotation;

        double[] cosTable = new double[numPoints + 1];
        double[] sinTable = new double[numPoints + 1];
        for (int i = 0; i <= numPoints; i++) {
            double angle = i * angleStep;
            double finalAngle = clockwise ? angle + finalRotation : -angle + finalRotation;
            cosTable[i] = Math.cos(finalAngle);
            sinTable[i] = Math.sin(finalAngle);
        }

        for (int i = 0; i <= numPoints; i++) {
            double angle = i * angleStep;
            double currentRadius = calculateRadius(angle, initialRadius);
            double x = center.x + currentRadius * cosTable[i];
            double y = center.y + currentRadius * sinTable[i];
            result.add(new Vec2d(x, y));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("螺旋线点生成完成: {} 个点", result.size());
        }

        return result;
    }

    /**
     * 生成“尖角样式”的多边形折线螺旋
     * 规则：
     * - 以正 N 边形为骨架（N=sides），每边一段直线；
     * - 每完成一边后，在同一顶点方向向外“跨圈”到下一层，再沿下一边继续，从而形成连续的折线螺旋；
     * - 半径随角度按当前螺旋类型公式增长（calculateRadius），保持与曲线型一致的增长节奏；
     * - rotation 控制起始朝向，clockwise 控制旋转方向。
     */
    private List<Vec2d> computeSharpEdgedPolygonSpiral() {
        int polygonSides = Math.max(3, this.sides);
        double stepAngle = TWO_PI / polygonSides;
        int totalSegments = Math.max(1, (int) Math.ceil(this.turns * polygonSides));

        double initialRadius = startRadius > 0 ? startRadius : 0.1;
        double sign = this.clockwise ? 1.0 : -1.0;
        double baseRotation = this.rotation;
        double cosAlpha = Math.cos(Math.PI / polygonSides);

        List<Vec2d> points = new ArrayList<>(totalSegments + 1);

        // 逐段计算：相邻两条边的“偏移直线”交点，避免在顶点处出现额外短折
        for (int k = 0; k < totalSegments; k++) {
            int sideIndex = k % polygonSides;

            double thetaK = k * stepAngle;
            double thetaK1 = (k + 1) * stepAngle;

            double rK = calculateRadius(thetaK, initialRadius);
            double rK1 = calculateRadius(thetaK1, initialRadius);

            // 两条边的外法线方向
            double phi1 = baseRotation + sign * (sideIndex + 0.5) * stepAngle;
            double phi2 = baseRotation + sign * (sideIndex + 1.5) * stepAngle;

            double c1 = Math.cos(phi1), s1 = Math.sin(phi1);
            double c2 = Math.cos(phi2), s2 = Math.sin(phi2);

            // 偏移量（apothem），以中心为参考
            double a1 = rK * cosAlpha;
            double a2 = rK1 * cosAlpha;

            // 解线性方程组： [c1 s1; c2 s2] * [x;y] = [a1;a2]
            double D = c1 * s2 - s1 * c2;
            if (Math.abs(D) < 1e-9) {
                // 极小概率的数值退化，回退到顶点点
                double vAngleA = baseRotation + sign * (sideIndex + 1) * stepAngle;
                double x = rK * Math.cos(vAngleA);
                double y = rK * Math.sin(vAngleA);
                points.add(new Vec2d(center.x + x, center.y + y));
                continue;
            }

            double xLocal = (a1 * s2 - s1 * a2) / D;
            double yLocal = (-a1 * c2 + c1 * a2) / D;

            points.add(new Vec2d(center.x + xLocal, center.y + yLocal));
        }

        return points;
    }

    /**
     * 螺旋参数缓存类
     * 提供类型安全的参数存储，避免ClassCastException
     */
    private static class SpiralParameters {
        Vec2d center;
        double radius;
        double turns;
        double spacing;
        SpiralType type;
        double growthFactor;
        int sides;
        double rotation;
        boolean sharpEdged;
        double startRadius;
        double expansionRate;
        double spiralCoefficient;
        boolean clockwise;

        /**
         * 构造函数，从SpiralShape创建参数快照
         */
        SpiralParameters(SpiralShape shape) {
            this.center = shape.center;
            this.radius = shape.radius;
            this.turns = shape.turns;
            this.spacing = shape.spacing;
            this.type = shape.type;
            this.growthFactor = shape.growthFactor;
            this.sides = shape.sides;
            this.rotation = shape.rotation;
            this.sharpEdged = shape.sharpEdged;
            this.startRadius = shape.startRadius;
            this.expansionRate = shape.expansionRate;
            this.spiralCoefficient = shape.spiralCoefficient;
            this.clockwise = shape.clockwise;
        }

        /**
         * 检查参数是否与当前螺旋形状匹配
         */
        boolean matches(SpiralShape shape) {
            return Objects.equals(center, shape.center) &&
                   radius == shape.radius &&
                   turns == shape.turns &&
                   spacing == shape.spacing &&
                   type == shape.type &&
                   growthFactor == shape.growthFactor &&
                   sides == shape.sides &&
                   rotation == shape.rotation &&
                   sharpEdged == shape.sharpEdged &&
                   startRadius == shape.startRadius &&
                   expansionRate == shape.expansionRate &&
                   spiralCoefficient == shape.spiralCoefficient &&
                   clockwise == shape.clockwise;
        }
        
        /**
         * 获取参数变化的详细信息
         */
        String getChangeDescription(SpiralShape shape) {
            List<String> changes = new ArrayList<>();
            
            if (!Objects.equals(center, shape.center)) {
                changes.add("center");
            }
            if (radius != shape.radius) {
                changes.add("radius");
            }
            if (turns != shape.turns) {
                changes.add("turns");
            }
            if (spacing != shape.spacing) {
                changes.add("spacing");
            }
            if (type != shape.type) {
                changes.add("type");
            }
            if (growthFactor != shape.growthFactor) {
                changes.add("growthFactor");
            }
            if (sides != shape.sides) {
                changes.add("sides");
            }
            if (rotation != shape.rotation) {
                changes.add("rotation");
            }
            if (sharpEdged != shape.sharpEdged) {
                changes.add("sharpEdged");
            }
            if (startRadius != shape.startRadius) {
                changes.add("startRadius");
            }
            if (expansionRate != shape.expansionRate) {
                changes.add("expansionRate");
            }
            if (spiralCoefficient != shape.spiralCoefficient) {
                changes.add("spiralCoefficient");
            }
            if (clockwise != shape.clockwise) {
                changes.add("clockwise");
            }
            
            return changes.isEmpty() ? "无变化" : String.join(", ", changes);
        }
    }
    
    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor,
                       imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        visitor.render(this, drawList, camera);
    }
    
    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 螺旋线的打断：转换为精确的点列表进行处理
        List<Vec2d> spiralPoints = getPoints();
        if (spiralPoints.size() < 2) {
            return newShapes;
        }
        
        // 修复：确保坐标变换的一致性
        Vec2d localFirstPoint = firstBreakPoint;
        Vec2d localSecondPoint = secondBreakPoint;
        if (getTransform() != null) {
            localFirstPoint = getTransform().inverseTransform(firstBreakPoint);
            if (secondBreakPoint != null) {
                localSecondPoint = getTransform().inverseTransform(secondBreakPoint);
            }
        }
        
        try {
            if ("SINGLE_POINT".equals(breakMode)) {
                int segmentIndex = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(spiralPoints, localFirstPoint);
                if (segmentIndex >= 0) {
                    Vec2d segStart = spiralPoints.get(segmentIndex);
                    Vec2d segEnd = spiralPoints.get(segmentIndex + 1);
                    Vec2d projectedPoint = projectPointOnSegment(localFirstPoint, segStart, segEnd);
                    
                    // 创建第一段
                    List<Vec2d> firstPoints = new ArrayList<>(spiralPoints.subList(0, segmentIndex + 1));
                    firstPoints.add(projectedPoint);
                    if (firstPoints.size() >= 2) {
                        PolylineShape firstPoly = new PolylineShape(firstPoints, false);
                        if (style != null) firstPoly.setStyle(style.clone());
                        if (getTransform() != null) firstPoly.setTransform(getTransform().clone());
                        newShapes.add(firstPoly);
                    }
                    
                    // 创建第二段
                    List<Vec2d> secondPoints = new ArrayList<>();
                    secondPoints.add(projectedPoint);
                    secondPoints.addAll(spiralPoints.subList(segmentIndex + 1, spiralPoints.size()));
                    if (secondPoints.size() >= 2) {
                        PolylineShape secondPoly = new PolylineShape(secondPoints, false);
                        if (style != null) secondPoly.setStyle(style.clone());
                        if (getTransform() != null) secondPoly.setTransform(getTransform().clone());
                        newShapes.add(secondPoly);
                    }
                }
            } else if ("TWO_POINT".equals(breakMode) && localSecondPoint != null) {
                int firstSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(spiralPoints, localFirstPoint);
                int secondSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(spiralPoints, localSecondPoint);
                
                if (firstSegment >= 0 && secondSegment >= 0) {
                    // 修复：螺旋线两点打断 - 确保第一个点在第二个点之前（螺旋线是开放路径）
                    if (firstSegment > secondSegment || 
                        (firstSegment == secondSegment && 
                         GeometryUtils.getDistanceFromStart(spiralPoints.get(firstSegment), spiralPoints.get(firstSegment + 1), localFirstPoint) >
                         GeometryUtils.getDistanceFromStart(spiralPoints.get(secondSegment), spiralPoints.get(secondSegment + 1), localSecondPoint))) {
                        
                        int temp = firstSegment;
                        firstSegment = secondSegment;
                        secondSegment = temp;
                        Vec2d tempPoint = localFirstPoint;
                        localFirstPoint = localSecondPoint;
                        localSecondPoint = tempPoint;
                    }
                    
                    Vec2d projectedFirst = projectPointOnSegment(localFirstPoint, 
                        spiralPoints.get(firstSegment), spiralPoints.get(firstSegment + 1));
                    Vec2d projectedSecond = projectPointOnSegment(localSecondPoint, 
                        spiralPoints.get(secondSegment), spiralPoints.get(secondSegment + 1));
                    
                    // 创建第一段（从起点到第一断点）
                    List<Vec2d> firstPoints = new ArrayList<>(spiralPoints.subList(0, firstSegment + 1));
                    firstPoints.add(projectedFirst);
                    if (firstPoints.size() >= 2) {
                        PolylineShape firstPoly = new PolylineShape(firstPoints, false);
                        if (style != null) firstPoly.setStyle(style.clone());
                        if (getTransform() != null) firstPoly.setTransform(getTransform().clone());
                        newShapes.add(firstPoly);
                    }
                    
                    // 创建第二段（从第二断点到终点）
                    if (secondSegment + 1 < spiralPoints.size()) {
                        List<Vec2d> secondPoints = new ArrayList<>();
                        secondPoints.add(projectedSecond);
                        secondPoints.addAll(spiralPoints.subList(secondSegment + 1, spiralPoints.size()));
                        if (secondPoints.size() >= 2) {
                            PolylineShape secondPoly = new PolylineShape(secondPoints, false);
                            if (style != null) secondPoly.setStyle(style.clone());
                            if (getTransform() != null) secondPoly.setTransform(getTransform().clone());
                            newShapes.add(secondPoly);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 回退到原方法
            PolylineShape polylineShape = new PolylineShape(spiralPoints, false);
            if (style != null) polylineShape.setStyle(style.clone());
            
            List<Shape> brokenShapes = polylineShape.breakShape(localFirstPoint, localSecondPoint, breakMode);
            for (Shape s : brokenShapes) {
                if (s instanceof PolylineShape) {
                    if (getTransform() != null) s.setTransform(getTransform().clone());
                    if (s.getStyle() == null && style != null) s.setStyle(style.clone());
                }
                newShapes.add(s);
            }
        }
        
        return newShapes;
    }
    
    /**
     * 将点投影到有限线段上（不延伸到线段外）
     */
    private Vec2d projectPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        if (segStart.equals(segEnd)) return segStart;
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        
        if (c1 <= 0) return segStart;  // 投影在起点外，返回起点
        if (c1 >= c2) return segEnd;   // 投影在终点外，返回终点
        
        double b = c1 / c2;
        return segStart.add(v.multiply(b));  // 投影在线段内
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到螺旋线的距离
        List<Vec2d> spiralPoints = getPoints();
        if (spiralPoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(spiralPoints, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            // 获取螺旋线上的点
            List<Vec2d> spiralPoints = getPoints();
            if (spiralPoints.size() < 2) return;
            
            // 关键修复：transform矩阵是"相对于center的缩放和旋转"，不包含平移
            // getPoints()返回的点是绝对坐标（center.x + radius * cos, center.y + radius * sin）
            // 因此需要先将点转换为相对于center的坐标，应用transform，再加回center
            Matrix3d transform = getTransform();
            boolean hasTransform = false;
            if (transform != null) {
                hasTransform = !(Math.abs(transform.get(0, 0) - 1.0) < 1e-10 && 
                                 Math.abs(transform.get(0, 1)) < 1e-10 && 
                                 Math.abs(transform.get(0, 2)) < 1e-10 &&
                                 Math.abs(transform.get(1, 0)) < 1e-10 && 
                                 Math.abs(transform.get(1, 1) - 1.0) < 1e-10 && 
                                 Math.abs(transform.get(1, 2)) < 1e-10 &&
                                 Math.abs(transform.get(2, 0)) < 1e-10 && 
                                 Math.abs(transform.get(2, 1)) < 1e-10 && 
                                 Math.abs(transform.get(2, 2) - 1.0) < 1e-10);
            }
            
            List<Vec2d> transformedPoints = new ArrayList<>();
            for (Vec2d point : spiralPoints) {
                if (hasTransform) {
                    // 关键修复：先将点转换为相对于center的坐标，应用transform，再加回center
                    Vec2d relativePoint = point.subtract(center);
                    transformedPoints.add(center.add(transform.transform(relativePoint)));
                } else {
                    transformedPoints.add(point);
                }
            }
            
            // 转换到屏幕坐标
            List<Vec2d> screenPoints = new ArrayList<>();
            for (Vec2d point : transformedPoints) {
                screenPoints.add(camera.worldToScreen(point));
            }
            
            // 绘制螺旋线
            for (int i = 0; i < screenPoints.size() - 1; i++) {
                Vec2d p1 = screenPoints.get(i);
                Vec2d p2 = screenPoints.get(i + 1);
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染螺旋线ImGui时发生错误", e);
        }
    }

    /**
     * 内部序列化类，用于Gson序列化
     */
    private static class SpiralShapeData {
        public Vec2d center;
        public double radius;
        public double turns;
        public double spacing;
        public SpiralType type;
        public double growthFactor;
        public int sides;
        public double rotation;
        public boolean sharpEdged;
        public double startRadius;
        public double expansionRate;
        public double spiralCoefficient;
        public boolean clockwise;
        public com.plot.api.graphics.IShapeStyle style;
        
        public SpiralShapeData(SpiralShape shape) {
            this.center = shape.center;
            this.radius = shape.radius;
            this.turns = shape.turns;
            this.spacing = shape.spacing;
            this.type = shape.type;
            this.growthFactor = shape.growthFactor;
            this.sides = shape.sides;
            this.rotation = shape.rotation;
            this.sharpEdged = shape.sharpEdged;
            this.startRadius = shape.startRadius;
            this.expansionRate = shape.expansionRate;
            this.spiralCoefficient = shape.spiralCoefficient;
            this.clockwise = shape.clockwise;
            this.style = shape.getStyle();
        }
        
        public void applyTo(SpiralShape shape) {
            shape.center = this.center;
            shape.radius = this.radius;
            shape.turns = this.turns;
            shape.spacing = this.spacing;
            shape.type = this.type;
            shape.growthFactor = this.growthFactor;
            shape.sides = this.sides;
            shape.rotation = this.rotation;
            shape.sharpEdged = this.sharpEdged;
            shape.startRadius = this.startRadius;
            shape.expansionRate = this.expansionRate;
            shape.spiralCoefficient = this.spiralCoefficient;
            shape.clockwise = this.clockwise;
            if (this.style != null) {
                shape.setStyle(this.style);
            }
        }
    }
    
    // 静态Gson实例，用于序列化
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

} 