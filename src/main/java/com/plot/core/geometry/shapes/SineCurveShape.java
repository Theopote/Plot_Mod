package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.graphics.style.LineStyle;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 正弦曲线形状类
 * 支持自定义振幅、波长和相位
 * 
 * <p><strong>重要限制说明：</strong></p>
 * <ul>
 * <li>本类的所有几何计算（如getBoundingBox、containsPoint、getClosestPoint等）
 *     都基于多段线近似，而非精确的数学计算。因此结果可能不够精确。</li>
 * <li>渲染质量取决于细分精度，在放大视图时可能出现锯齿状边缘。</li>
 * <li>性能与曲线长度成正比，长曲线可能需要更多计算时间。</li>
 * </ul>
 * 
 * <p>建议在需要高精度几何计算的场景中使用其他形状类型，
 * 或考虑使用更精确的数学方法重新实现相关功能。</p>
 */
public class SineCurveShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(SineCurveShape.class);
    
    // 常量定义
    private static final double TWO_PI = 2 * Math.PI;  // 2π常量，避免重复计算
    private static final int MIN_POINTS_PER_WAVE = 8;   // 每个波形的最小点数
    private static final int MAX_POINTS_PER_WAVE = 200; // 每个波形的最大点数
    private static final int MAX_TOTAL_POINTS = 20_000; // 最大总点数限制
    private static final double DEFAULT_TOLERANCE = 5.0;  // 默认容差值
    // 注意：CURVATURE_THRESHOLD 常量保留用于未来的曲率自适应细分功能
    // private static final double CURVATURE_THRESHOLD = 0.1;
    
    // JSON序列化支持 - 延迟初始化
    private static Gson gson = null;
    
    private static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder().setPrettyPrinting().create();
        }
        return gson;
    }
    
    private Vec2d startPoint;  // 起始点
    private Vec2d endPoint;    // 结束点
    private double amplitude;  // 振幅
    private double wavelength; // 波长
    private double phase;      // 相位（弧度）
    
    // 缓存相关
    private List<Vec2d> cachedPoints;  // 缓存的点列表
    private boolean dirty = true;      // 脏标记，表示需要重新计算点
    private double lastScreenScale = -1; // 上次计算时的屏幕缩放，用于自适应细分
    private double lastAmplitude = Double.NaN; // 上次计算时的振幅，用于缓存失效检测
    private double lastWavelength = Double.NaN; // 上次计算时的波长，用于缓存失效检测
    private double lastPhase = Double.NaN; // 上次计算时的相位，用于缓存失效检测
    
    /**
     * 构造函数
     * @param startPoint 起始点
     * @param endPoint 结束点
     * @param amplitude 振幅
     * @param wavelength 波长
     * @param phase 相位（弧度）
     */
    public SineCurveShape(Vec2d startPoint, Vec2d endPoint, double amplitude, double wavelength, double phase) {
        // 调用父类构造函数，使用起点和终点的中点作为形状的位置
        super(startPoint != null && endPoint != null ? 
            new Vec2d((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2) : 
            new Vec2d(0, 0));
            
        // 参数验证
        if (startPoint == null) {
            throw new IllegalArgumentException("起始点不能为空");
        }
        if (endPoint == null) {
            throw new IllegalArgumentException("结束点不能为空");
        }
        if (Double.isNaN(amplitude) || Double.isInfinite(amplitude)) {
            throw new IllegalArgumentException("振幅必须是有限数值");
        }
        if (wavelength <= 0 || Double.isNaN(wavelength) || Double.isInfinite(wavelength)) {
            throw new IllegalArgumentException("波长必须是大于0的有限数值");
        }
        if (Double.isNaN(phase) || Double.isInfinite(phase)) {
            throw new IllegalArgumentException("相位必须是有限数值");
        }
        
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.amplitude = amplitude;
        this.wavelength = wavelength;
        this.phase = normalizePhase(phase); // 规范化相位
    }
    
    // Getter 和 Setter 方法
    public Vec2d getStartPoint() { return startPoint; }
    public Vec2d getEndPoint() { return endPoint; }

    public double getPhase() { return phase; }
    
    public void setStartPoint(Vec2d startPoint) {
        if (startPoint == null) {
            throw new IllegalArgumentException("起始点不能为空");
        }
        if (!this.startPoint.equals(startPoint)) {
            this.startPoint = startPoint;
            dirty = true;
        }
    }
    
    public void setEndPoint(Vec2d endPoint) {
        if (endPoint == null) {
            throw new IllegalArgumentException("结束点不能为空");
        }
        if (!this.endPoint.equals(endPoint)) {
            this.endPoint = endPoint;
            dirty = true;
        }
    }
    
    public void setPhase(double phase) {
        if (Double.isNaN(phase) || Double.isInfinite(phase)) {
            throw new IllegalArgumentException("相位必须是有限数值");
        }
        this.phase = normalizePhase(phase); // 规范化相位
        dirty = true;
    }
    
    /**
     * 生成正弦曲线的点（自适应细分版本）
     * @return 正弦曲线上的点列表
     */
    private List<Vec2d> generateSinePoints() {
        return generateSinePoints(1.0); // 默认屏幕缩放为1.0
    }
    
    /**
     * 生成正弦曲线的点（支持自适应细分）
     * @param screenScale 屏幕缩放因子，用于自适应细分
     * @return 正弦曲线上的点列表
     */
    private List<Vec2d> generateSinePoints(double screenScale) {
        // 检查是否需要重新计算
        boolean needsRecalculation = dirty || 
            cachedPoints == null || 
            Math.abs(screenScale - lastScreenScale) > 0.1 ||
            Math.abs(amplitude - lastAmplitude) > 1e-10 ||
            Math.abs(wavelength - lastWavelength) > 1e-10 ||
            Math.abs(phase - lastPhase) > 1e-10;
            
        if (!needsRecalculation) {
            return cachedPoints;
        }
        
        List<Vec2d> points = new ArrayList<>();
        
        // 计算曲线方向和长度
        Vec2d direction = endPoint.subtract(startPoint);
        double length = direction.length();
        if (length == 0) {
            return List.of(startPoint);
        }
        
        // 计算单位向量
        Vec2d unitDirection = direction.multiply(1.0 / length);
        Vec2d normalDirection = new Vec2d(-unitDirection.y, unitDirection.x);
        
        // 自适应计算点数
        int numWaves = (int) Math.ceil(length / wavelength);
        int pointsPerWave = calculateAdaptivePointsPerWave(length, screenScale);
        int totalPoints = numWaves * pointsPerWave;
        
        // 限制总点数
        if (totalPoints > MAX_TOTAL_POINTS) {
            pointsPerWave = MAX_TOTAL_POINTS / numWaves;
            totalPoints = numWaves * pointsPerWave;
        }
        
        // 生成点
        for (int i = 0; i <= totalPoints; i++) {
            double t = i * length / totalPoints;
            double x = t / wavelength * TWO_PI + phase;
            double y = amplitude * Math.sin(x);
            
            Vec2d basePoint = startPoint.add(unitDirection.multiply(t));
            Vec2d point = basePoint.add(normalDirection.multiply(y));
            points.add(point);
        }
        
        cachedPoints = points;
        dirty = false;
        lastScreenScale = screenScale;
        lastAmplitude = amplitude;
        lastWavelength = wavelength;
        lastPhase = phase;
        return points;
    }
    
    /**
     * 根据曲线长度和屏幕缩放自适应计算每波形的点数
     * @param length 曲线长度
     * @param screenScale 屏幕缩放因子
     * @return 每波形的点数
     */
    private int calculateAdaptivePointsPerWave(double length, double screenScale) {
        // 基础点数：根据波长和振幅计算
        double basePoints = Math.max(MIN_POINTS_PER_WAVE, Math.min(MAX_POINTS_PER_WAVE, 
            Math.sqrt(length / wavelength) * 20));
        
        // 根据屏幕缩放调整
        double scaledPoints = basePoints * Math.max(0.5, Math.min(2.0, screenScale));
        
        // 根据振幅调整（振幅越大，需要的点数越多）
        if (amplitude > 0) {
            double amplitudeFactor = Math.min(2.0, 1.0 + Math.log(amplitude + 1) / 10);
            scaledPoints *= amplitudeFactor;
        }
        
        return (int) Math.round(scaledPoints);
    }
    
    /**
     * 规范化相位到 [0, 2π) 范围
     * @param phase 要规范化的相位
     * @return 规范化后的相位
     */
    private double normalizePhase(double phase) {
        while (phase < 0) phase += TWO_PI;
        while (phase >= TWO_PI) phase -= TWO_PI;
        return phase;
    }
    
    /**
     * 获取正弦曲线的数学属性信息
     * @return 包含数学属性的字符串
     */
    public String getMathematicalProperties() {
        double baselineLength = startPoint.distance(endPoint);
        int numWaves = (int) Math.ceil(baselineLength / wavelength);
        double frequency = 1.0 / wavelength;
        
        return String.format(
            "SineCurve Properties: Length=%.2f, Amplitude=%.2f, Wavelength=%.2f, " +
            "Phase=%.2f°, Frequency=%.2f, Waves=%d",
            baselineLength, amplitude, wavelength, 
            Math.toDegrees(phase), frequency, numWaves
        );
    }
    
    /**
     * 检查正弦曲线是否有效
     * @return 如果正弦曲线参数有效则返回true
     */
    public boolean isValid() {
        return startPoint != null && endPoint != null && !Double.isNaN(amplitude) && !Double.isInfinite(amplitude) && wavelength > 0 && !Double.isInfinite(wavelength) && !Double.isNaN(phase) && !Double.isInfinite(phase);
    }
    
    @Override
    public void draw(DrawContext context) {
        if (context == null) {
            LOGGER.warn("DrawContext为空，无法绘制正弦曲线");
            return;
        }
        
        if (!isValid()) {
            LOGGER.warn("正弦曲线参数无效，无法绘制: {}", getMathematicalProperties());
            return;
        }
        
        try {
            List<Vec2d> points = generateSinePoints();
            if (points.size() < 2) {
                LOGGER.debug("正弦曲线点数不足，无法绘制");
                return;
            }
            
            // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
            ShapeStyle activeStyle = context.getCurrentStyle();
            if (activeStyle == null) {
                // 如果DrawContext没有设置样式，使用图形自己的样式
                if (getStyle() != null && getStyle() instanceof ShapeStyle) {
                    activeStyle = (ShapeStyle) getStyle();
                }
            }
            
            if (activeStyle == null || !activeStyle.getLineStyle().isVisible()) {
                return;
            }
            
            // 绘制正弦曲线
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                
                // 使用LineStyle对象而不是只传递颜色，确保线宽等属性也被应用
                if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                    context.drawLine(p1, p2, lineStyle);
                } else {
                    // 回退到颜色绘制
                    java.awt.Color lineColor = activeStyle.getLineColor();
                    context.drawLine(p1, p2, lineColor);
                }
            }
        } catch (Exception e) {
            LOGGER.error("绘制正弦曲线时发生错误: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        List<Vec2d> points = generateSinePoints();
        if (points.isEmpty()) {
            return BoundingBox.empty();
        }
        
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        
        return new BoundingBox(
            new Vec2d(minX, minY),
            new Vec2d(maxX, maxY)
        );
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        List<Vec2d> points = generateSinePoints();
        if (points.size() < 2) {
            return false;
        }
        
        // 检查点是否在任何线段附近
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            if (GeometryUtils.pointToSegmentDistance(point, p1, p2) <= tolerance) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean contains(Vec2d point) {
        return containsPoint(point, DEFAULT_TOLERANCE);
    }
    
    @Override
    public Shape clone() {
        SineCurveShape clone = new SineCurveShape(
            startPoint,
            endPoint,
            amplitude,
            wavelength,
            phase
        );
        
        if (getStyle() != null) {
            clone.setStyle(getStyle().clone());
        }
        if (getTransform() != null) {
            clone.setTransform(getTransform().clone());
        }
        
        return clone;
    }
    
    @Override
    public void translate(Vec2d offset) {
        startPoint = startPoint.add(offset);
        endPoint = endPoint.add(offset);
        dirty = true;
    }
    
    @Override
    public void rotate(Vec2d center, double angle) {
        startPoint = GeometryUtils.rotate(startPoint.subtract(center), angle).add(center);
        endPoint = GeometryUtils.rotate(endPoint.subtract(center), angle).add(center);
        // 移除 phase += angle; 因为旋转起点和终点已经正确旋转了整个曲线
        // 额外增加相位会导致波形发生不必要的平移
        dirty = true;
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        rotate(center, angle);  // 调用已有的rotate方法
    }

    @Override
    public void scale(Vec2d scale, Vec2d center) {
        // 关键修复：缩放工具最终调用的是 Shape.scale(Vec2d, center)。
        // SineCurveShape 之前未重写该方法，同时其 draw() 也不应用 transform 矩阵，
        // 导致“正弦曲线完全不能缩放”。
        if (scale == null || center == null) return;

        // 记录缩放前的基线方向（波长沿该方向，振幅沿法线方向）
        Vec2d baseline = endPoint.subtract(startPoint);
        double len = baseline.length();
        Vec2d u = len > 1e-9 ? baseline.multiply(1.0 / len) : new Vec2d(1, 0);
        Vec2d n = new Vec2d(-u.y, u.x);

        // 非均匀缩放下，不同方向长度缩放系数不同：k = |S*v| / |v|
        double kAlong = Math.sqrt(Math.pow(scale.x * u.x, 2) + Math.pow(scale.y * u.y, 2));
        double kPerp = Math.sqrt(Math.pow(scale.x * n.x, 2) + Math.pow(scale.y * n.y, 2));

        // 缩放端点
        startPoint = center.add(startPoint.subtract(center).multiply(scale));
        endPoint = center.add(endPoint.subtract(center).multiply(scale));

        // 缩放参数（长度量）
        amplitude *= kPerp;
        wavelength *= kAlong;

        // 标记缓存失效
        dirty = true;
        cachedPoints = null;
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换定义点
        this.startPoint = transformMatrix.transform(this.startPoint);
        this.endPoint = transformMatrix.transform(this.endPoint);
        
        // 从矩阵行列式获取平均缩放因子
        double avgScale = Math.sqrt(Math.abs(transformMatrix.getDeterminant()));
        
        // 缩放尺寸参数
        this.amplitude *= avgScale;
        this.wavelength *= avgScale;
        
        this.markDirty(); // 标记需要重新计算
        return this;
    }
    
    @Override
    public String serialize() {
        try {
            SineCurveData data = new SineCurveData();
            data.startX = startPoint.x;
            data.startY = startPoint.y;
            data.endX = endPoint.x;
            data.endY = endPoint.y;
            data.amplitude = amplitude;
            data.wavelength = wavelength;
            data.phase = phase;
            
            return getGson().toJson(data);
        } catch (Exception e) {
            LOGGER.error("序列化正弦曲线时发生错误: {}", e.getMessage(), e);
            // 回退到简单格式
            return String.format("startX:%s,startY:%s,endX:%s,endY:%s,amplitude:%s,wavelength:%s,phase:%s",
                startPoint.x, startPoint.y, endPoint.x, endPoint.y, amplitude, wavelength, phase);
        }
    }
    
    @Override
    public void deserialize(String data) {
        try {
            // 首先尝试JSON格式
            if (data.trim().startsWith("{")) {
                SineCurveData curveData = getGson().fromJson(data, SineCurveData.class);
                this.startPoint = new Vec2d(curveData.startX, curveData.startY);
                this.endPoint = new Vec2d(curveData.endX, curveData.endY);
                this.amplitude = curveData.amplitude;
                this.wavelength = curveData.wavelength;
                this.phase = curveData.phase;
            } else {
                // 回退到旧格式
                deserializeLegacyFormat(data);
            }
            
            this.dirty = true;
            
        } catch (Exception e) {
            LOGGER.warn("JSON反序列化失败，尝试旧格式: {}", e.getMessage());
            try {
                deserializeLegacyFormat(data);
                this.dirty = true;
            } catch (Exception ex) {
                LOGGER.error("反序列化正弦曲线时发生错误: {}", ex.getMessage(), ex);
                throw new RuntimeException("反序列化失败", ex);
            }
        }
    }
    
    /**
     * 旧格式的反序列化方法（向后兼容）
     */
    private void deserializeLegacyFormat(String data) {
        double startX = 0, startY = 0, endX = 0, endY = 0;
        double newAmplitude = 0, newWavelength = 0, newPhase = 0;
        
        String[] pairs = data.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length != 2) continue;
            
            String key = keyValue[0].trim();
            double value = Double.parseDouble(keyValue[1].trim());
            
            switch (key) {
                case "startX" -> startX = value;
                case "startY" -> startY = value;
                case "endX" -> endX = value;
                case "endY" -> endY = value;
                case "amplitude" -> newAmplitude = value;
                case "wavelength" -> newWavelength = value;
                case "phase" -> newPhase = value;
            }
        }
        
        this.startPoint = new Vec2d(startX, startY);
        this.endPoint = new Vec2d(endX, endY);
        this.amplitude = newAmplitude;
        this.wavelength = newWavelength;
        this.phase = newPhase;
    }
    
    /**
     * 正弦曲线数据的JSON序列化类
     */
    private static class SineCurveData {
        public double startX;
        public double startY;
        public double endX;
        public double endY;
        public double amplitude;
        public double wavelength;
        public double phase;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        List<Vec2d> intersections = new ArrayList<>();
        
        try {
            // 确定延伸方向：从起点还是终点延伸
            boolean fromStart = point.distance(startPoint) < point.distance(endPoint);
            
            // 计算基线的延伸方向
            Vec2d baseline = endPoint.subtract(startPoint);
            if (baseline.length() == 0) {
                return intersections;
            }
            
            Vec2d direction = baseline.normalize();
            if (fromStart) {
                direction = direction.multiply(-1); // 从起点向前延伸
            }
            
            // 创建延伸后的正弦曲线
            Vec2d extendedStart = fromStart ? startPoint.subtract(direction.multiply(maxDistance)) : startPoint;
            Vec2d extendedEnd = fromStart ? endPoint : endPoint.add(direction.multiply(maxDistance));
            
            // 计算延伸后的相位
            double phaseOffset = fromStart ? 2 * Math.PI * maxDistance / wavelength : 0;
            double extendedPhase = fromStart ? phase + phaseOffset : phase;
            
            // 创建临时的延伸正弦曲线
            SineCurveShape extendedSine = new SineCurveShape(extendedStart, extendedEnd, amplitude, wavelength, extendedPhase);
            
            // 获取与另一个形状的交点
            intersections = extendedSine.getIntersectionPoints(other);
            
            // 过滤掉原始曲线范围内的交点
            intersections.removeIf(intersection -> {
                double distToStart = intersection.distance(startPoint);
                double distToEnd = intersection.distance(endPoint);
                double baselineLength = baseline.length();
                return distToStart <= baselineLength && distToEnd <= baselineLength;
            });
            
        } catch (Exception e) {
            LOGGER.error("计算正弦曲线延伸交点时发生错误: {}", e.getMessage(), e);
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        return List.of(startPoint, endPoint);
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> newShapes = new ArrayList<>();
        
        if (points == null || points.isEmpty()) {
            return newShapes;
        }
        
        try {
            // 将分割点投影到基线上
            List<Vec2d> baselinePoints = new ArrayList<>();
            for (Vec2d point : points) {
                Vec2d projectedPoint = calculateBreakPointOnBaseline(point);
                if (projectedPoint != null) {
                    baselinePoints.add(projectedPoint);
                }
            }
            
            if (baselinePoints.isEmpty()) {
                return newShapes;
            }
            
            // 按距离起点的远近排序
            baselinePoints.sort((p1, p2) -> {
                double dist1 = p1.distance(startPoint);
                double dist2 = p2.distance(startPoint);
                return Double.compare(dist1, dist2);
            });
            
            // 创建分割后的正弦曲线段
            Vec2d currentStart = startPoint;
            for (Vec2d splitPoint : baselinePoints) {
                if (!currentStart.equals(splitPoint)) {
                    SineCurveShape segment = createSineSegment(currentStart, splitPoint);
                    if (segment != null) {
                        newShapes.add(segment);
                    }
                }
                currentStart = splitPoint;
            }
            
            // 添加最后一段
            if (!currentStart.equals(endPoint)) {
                SineCurveShape lastSegment = createSineSegment(currentStart, endPoint);
                if (lastSegment != null) {
                    newShapes.add(lastSegment);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("分割正弦曲线时发生错误: {}", e.getMessage(), e);
        }
        
        return newShapes;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 参数验证
        if (point == null) {
            throw new IllegalArgumentException("延伸点不能为空");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("延伸距离不能为负");
        }
        if (distance == 0) {
            return clone(); // 距离为0时返回副本
        }
        
        // 确定延伸方向：从起点还是终点延伸
        boolean fromStart = point.distance(startPoint) < point.distance(endPoint);
        
        // 计算基线方向和长度
        Vec2d baseline = endPoint.subtract(startPoint);
        double baselineLength = baseline.length();
        if (baselineLength == 0) {
            // 如果起点和终点相同，无法确定延伸方向
            return clone();
        }
        
        Vec2d direction = baseline.normalize();
        Vec2d newStartPoint = startPoint;
        Vec2d newEndPoint = endPoint;
        double newPhase = phase;
        
        if (fromStart) {
            // 从起点延伸：保持终点不变，向前延伸起点
            newStartPoint = startPoint.subtract(direction.multiply(distance));
            
            // 调整相位以保持波形连续性
            // 延伸距离对应的相位变化 = 2π * 延伸距离 / 波长
            double phaseShift = 2 * Math.PI * distance / wavelength;
            newPhase = phase + phaseShift;
            
            // 规范化相位到 [0, 2π) 范围，避免数值误差累积
            newPhase = normalizePhase(newPhase);
            
        } else {
            // 从终点延伸：保持起点不变，向后延伸终点
            newEndPoint = endPoint.add(direction.multiply(distance));
            // 终点延伸不需要调整相位，因为相位参考点（起点）没有改变
        }
        
        // 创建延伸后的正弦曲线，保持原有的振幅和波长，但调整相位
        SineCurveShape extended = new SineCurveShape(
            newStartPoint,
            newEndPoint,
            amplitude,
            wavelength,
            newPhase
        );
        
        // 继承样式和变换
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }
        if (getTransform() != null) {
            extended.setTransform(getTransform().clone());
        }
        
        return extended;
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 参数验证
        if (point == null) {
            throw new IllegalArgumentException("延伸点不能为空");
        }
        if (toPoint == null) {
            throw new IllegalArgumentException("目标点不能为空");
        }
        
        // 确定延伸方向：从起点还是终点延伸
        boolean fromStart = point.distance(startPoint) < point.distance(endPoint);
        
        // 计算基线方向和长度
        Vec2d baseline = endPoint.subtract(startPoint);
        double baselineLength = baseline.length();
        if (baselineLength == 0) {
            // 如果起点和终点相同，无法确定延伸方向
            return clone();
        }
        
        Vec2d newStartPoint = startPoint;
        Vec2d newEndPoint = endPoint;
        double newPhase = phase;
        
        if (fromStart) {
            // 从起点延伸到目标点：保持终点不变，将起点移动到目标点
            newStartPoint = toPoint;
            
            // 计算延伸距离并调整相位
            double distance = startPoint.distance(toPoint);
            double phaseShift = 2 * Math.PI * distance / wavelength;
            newPhase = phase + phaseShift;
            
            // 规范化相位到 [0, 2π) 范围，避免数值误差累积
            newPhase = normalizePhase(newPhase);
            
        } else {
            // 从终点延伸到目标点：保持起点不变，将终点移动到目标点
            newEndPoint = toPoint;
            // 终点延伸不需要调整相位，因为相位参考点（起点）没有改变
        }
        
        // 创建延伸后的正弦曲线，保持原有的振幅和波长，但调整相位
        SineCurveShape extended = new SineCurveShape(
            newStartPoint,
            newEndPoint,
            amplitude,
            wavelength,
            newPhase
        );
        
        // 继承样式和变换
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }
        if (getTransform() != null) {
            extended.setTransform(getTransform().clone());
        }
        
        return extended;
    }

    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (point == null) {
            return startPoint;
        }
        
        List<Vec2d> points = generateSinePoints();
        if (points.isEmpty()) {
            return startPoint;
        }
        if (points.size() == 1) {
            return points.getFirst();
        }

        Vec2d closestPoint = points.getFirst();
        double minDistance = point.distance(closestPoint);

        // 遍历所有线段找到最近点
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);

            // 使用更精确的点到线段距离计算
            Vec2d segmentClosest = GeometryUtils.projectPointOnLine(point, p1, p2);
            double distance = point.distance(segmentClosest);
            
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = segmentClosest;
            }
        }

        return closestPoint;
    }

    @Override
    public List<Vec2d> getControlPoints() {
        // 返回正弦曲线的控制点（起点和终点）
        List<Vec2d> points = List.of(startPoint, endPoint);
        
        // 应用变换
        if (getTransform() != null) {
            return points.stream()
                .map(p -> getTransform().transform(p))
                .toList();
        }
        
        return points;
    }

    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (point == null) {
            throw new IllegalArgumentException("控制点不能为空");
        }
        
        switch (index) {
            case 0 -> setStartPoint(point);  // 设置起点
            case 1 -> setEndPoint(point);    // 设置终点
            default -> throw new IllegalArgumentException("无效的控制点索引: " + index);
        }
    }

    @Override
    public boolean intersects(Shape other) {
        // 获取与其他形状的交点
        List<Vec2d> intersections = getIntersectionsWith(other);
        // 如果有交点，则表示相交
        return !intersections.isEmpty();
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        List<Vec2d> points = generateSinePoints();
        
        if (points.size() < 2) {
            return intersections;
        }
        
        // 遍历正弦曲线的所有线段
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            // 获取当前线段与其他形状的交点
            if (other instanceof LineShape line) {
                intersections.addAll(GeometryUtils.segmentIntersection(p1, p2, line.getStart(), line.getEnd()));
            } else {
                // 对于其他形状，让它们处理交点计算
                intersections.addAll(other.getIntersectionPoints(new LineShape(p1, p2)));
            }
        }
        
        return intersections;
    }

    @Override
    public Vec2d getTangentAt(Vec2d point) {
        List<Vec2d> points = generateSinePoints();
        if (points.size() < 2) {
            // 如果点数不足，返回从起点到终点的方向
            return endPoint.subtract(startPoint).normalize();
        }

        // 找到最近的线段
        int segmentIndex = -1;
        double minDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);

            double distance = GeometryUtils.pointToSegmentDistance(point, p1, p2);
            if (distance < minDistance) {
                minDistance = distance;
                segmentIndex = i;
            }
        }

        if (segmentIndex == -1) {
            // 如果没有找到最近点（不应该发生），返回默认方向
            return endPoint.subtract(startPoint).normalize();
        }

        // 使用最近线段的方向作为切线方向
        Vec2d p1 = points.get(segmentIndex);
        Vec2d p2 = points.get(segmentIndex + 1);
        return p2.subtract(p1).normalize();
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        if (point == null) {
            return Double.POSITIVE_INFINITY;
        }
        
        List<Vec2d> points = generateSinePoints();
        if (points.size() < 2) {
            // 如果点数不足，返回到起点的距离
            return point.distance(startPoint);
        }

        // 找到最近的线段和距离
        double minDistance = Double.POSITIVE_INFINITY;
        Vec2d closestPoint = null;
        Vec2d segmentDirection = null;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);

            double distance = GeometryUtils.pointToSegmentDistance(point, p1, p2);
            if (distance < Math.abs(minDistance)) {
                minDistance = distance;
                closestPoint = GeometryUtils.projectPointOnLine(point, p1, p2);
                segmentDirection = p2.subtract(p1);
            }
        }

        if (closestPoint != null && segmentDirection != null) {
            // 计算点到线段的有符号距离
            Vec2d normal = new Vec2d(-segmentDirection.y, segmentDirection.x).normalize();
            Vec2d toPoint = point.subtract(closestPoint);
            
            // 根据点在线段的哪一侧确定符号
            return Math.copySign(minDistance, normal.dot(toPoint));
        }

        return minDistance;
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        // 找到最近点
        Vec2d closestPoint = getClosestPoint(point);
        
        // 计算最近点到起点的距离在基线上的投影长度
        Vec2d baseDirection = endPoint.subtract(startPoint);
        double totalLength = baseDirection.length();
        Vec2d toClosestPoint = closestPoint.subtract(startPoint);
        double projectedLength = toClosestPoint.dot(baseDirection.normalize());
        
        // 如果投影长度小于等于0或大于等于总长度，返回原始形状
        if (projectedLength <= 0 || projectedLength >= totalLength) {
            return clone();
        }
        
        // 创建新的终点
        Vec2d newEndPoint = startPoint.add(baseDirection.normalize().multiply(projectedLength));
        
        // 创建新的正弦曲线
        SineCurveShape trimmed = new SineCurveShape(
            startPoint,
            newEndPoint,
            amplitude,
            wavelength,
            phase
        );
        
        // 复制样式
        if (getStyle() != null) {
            trimmed.setStyle(getStyle().clone());
        }
        
        return trimmed;
    }

    @Override
    public Shape createOffset(double distance) {
        // 计算基线方向和法向量
        Vec2d baseDirection = endPoint.subtract(startPoint).normalize();
        Vec2d normalDirection = new Vec2d(-baseDirection.y, baseDirection.x);
        
        // 创建偏移后的起点和终点
        Vec2d offsetStartPoint = startPoint.add(normalDirection.multiply(distance));
        Vec2d offsetEndPoint = endPoint.add(normalDirection.multiply(distance));
        
        // 创建偏移后的正弦曲线，振幅保持不变
        SineCurveShape offset = new SineCurveShape(
            offsetStartPoint,
            offsetEndPoint,
            amplitude,
            wavelength,
            phase
        );
        
        // 复制样式
        if (getStyle() != null) {
            offset.setStyle(getStyle().clone());
        }
        
        return offset;
    }

    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> intersections = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return intersections;
        }

        // 获取正弦曲线的所有点
        List<Vec2d> sinePoints = generateSinePoints();
        if (sinePoints.size() < 2) {
            return intersections;
        }

        // 遍历折线的每个线段
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);

            // 遍历正弦曲线的每个线段
            for (int j = 0; j < sinePoints.size() - 1; j++) {
                Vec2d s1 = sinePoints.get(j);
                Vec2d s2 = sinePoints.get(j + 1);

                // 计算两个线段的交点
                List<Vec2d> segmentIntersections = GeometryUtils.segmentIntersection(p1, p2, s1, s2);
                intersections.addAll(segmentIntersections);
            }
        }

        return intersections;
    }

    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        // 使用getIntersectionsWithPolyline方法检查是否有交点
        return !getIntersectionsWithPolyline(points).isEmpty();
    }

    @Override
    public List<Vec2d> getPoints() {
        // 返回正弦曲线上的所有点
        return generateSinePoints();
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
                // 计算打断点在基线上的投影位置
                Vec2d breakPoint = calculateBreakPointOnBaseline(localFirstPoint);
                if (breakPoint != null) {
                    // 创建第一段：从起点到打断点
                    SineCurveShape firstSegment = createSineSegment(startPoint, breakPoint);
                    if (firstSegment != null) {
                        newShapes.add(firstSegment);
                    }
                    
                    // 创建第二段：从打断点到终点
                    SineCurveShape secondSegment = createSineSegment(breakPoint, endPoint);
                    if (secondSegment != null) {
                        newShapes.add(secondSegment);
                    }
                }
            } else if ("TWO_POINT".equals(breakMode) && localSecondPoint != null) {
                // 计算两个打断点在基线上的投影位置
                Vec2d firstBreak = calculateBreakPointOnBaseline(localFirstPoint);
                Vec2d secondBreak = calculateBreakPointOnBaseline(localSecondPoint);
                
                if (firstBreak != null && secondBreak != null) {
                    // 确保顺序正确
                    if (firstBreak.distance(startPoint) > secondBreak.distance(startPoint)) {
                        Vec2d temp = firstBreak;
                        firstBreak = secondBreak;
                        secondBreak = temp;
                    }
                    
                    // 创建第一段：从起点到第一个打断点
                    SineCurveShape firstSegment = createSineSegment(startPoint, firstBreak);
                    if (firstSegment != null) {
                        newShapes.add(firstSegment);
                    }
                    
                    // 创建第二段：从第一个打断点到第二个打断点
                    SineCurveShape secondSegment = createSineSegment(firstBreak, secondBreak);
                    if (secondSegment != null) {
                        newShapes.add(secondSegment);
                    }
                    
                    // 创建第三段：从第二个打断点到终点
                    SineCurveShape thirdSegment = createSineSegment(secondBreak, endPoint);
                    if (thirdSegment != null) {
                        newShapes.add(thirdSegment);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("打断正弦曲线时发生错误: {}", e.getMessage(), e);
            // 回退到多段线方法
            return fallbackToPolylineBreak(localFirstPoint, localSecondPoint, breakMode);
        }
        
        return newShapes;
    }
    
    /**
     * 计算打断点在基线上的投影位置
     */
    private Vec2d calculateBreakPointOnBaseline(Vec2d breakPoint) {
        // 计算基线的方向和长度
        Vec2d baseline = endPoint.subtract(startPoint);
        double baselineLength = baseline.length();
        if (baselineLength == 0) {
            return null;
        }
        
        // 计算打断点到基线的投影
        Vec2d toBreakPoint = breakPoint.subtract(startPoint);
        double projectionLength = toBreakPoint.dot(baseline.normalize());
        
        // 确保投影在基线范围内
        projectionLength = Math.max(0, Math.min(baselineLength, projectionLength));
        
        // 返回投影点
        return startPoint.add(baseline.normalize().multiply(projectionLength));
    }
    
    /**
     * 创建正弦曲线段，保持原有的参数化特性
     */
    private SineCurveShape createSineSegment(Vec2d newStart, Vec2d newEnd) {
        if (newStart == null || newEnd == null || newStart.equals(newEnd)) {
            return null;
        }
        
        // 计算新段的长度和相位偏移
        double segmentLength = newStart.distance(newEnd);
        double originalLength = startPoint.distance(endPoint);
        
        if (segmentLength == 0 || originalLength == 0) {
            return null;
        }
        
        // 计算相位偏移：保持波形的连续性
        double startOffset = newStart.distance(startPoint);
        double phaseOffset = 2 * Math.PI * startOffset / wavelength;
        double newPhase = phase + phaseOffset;
        
        // 创建新的正弦曲线段
        SineCurveShape segment = new SineCurveShape(newStart, newEnd, amplitude, wavelength, newPhase);
        
        // 复制样式和变换
        if (getStyle() != null) {
            segment.setStyle(getStyle().clone());
        }
        if (getTransform() != null) {
            segment.setTransform(getTransform().clone());
        }
        
        return segment;
    }
    
    /**
     * 回退到多段线打断方法
     */
    private List<Shape> fallbackToPolylineBreak(Vec2d localFirstPoint, Vec2d localSecondPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        List<Vec2d> sinePoints = getPoints();
        
        if (sinePoints.size() < 2) {
            return newShapes;
        }
        
        PolylineShape polylineShape = new PolylineShape(sinePoints, false);
        if (getStyle() != null) polylineShape.setStyle(getStyle().clone());
        
        List<Shape> brokenShapes = polylineShape.breakShape(localFirstPoint, localSecondPoint, breakMode);
        for (Shape s : brokenShapes) {
            if (s instanceof PolylineShape) {
                if (getTransform() != null) s.setTransform(getTransform().clone());
                if (s.getStyle() == null && getStyle() != null) s.setStyle(getStyle().clone());
            }
            newShapes.add(s);
        }
        
        return newShapes;
    }
    
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到正弦曲线的距离
        List<Vec2d> sinePoints = getPoints();
        if (sinePoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(sinePoints, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            // 获取正弦曲线上的点
            List<Vec2d> sinePoints = getPoints();
            if (sinePoints.size() < 2) return;
            
            // 获取变换后的点
            List<Vec2d> transformedPoints = new ArrayList<>();
            for (Vec2d point : sinePoints) {
                transformedPoints.add(getTransform().transform(point));
            }
            
            // 转换到屏幕坐标
            List<Vec2d> screenPoints = new ArrayList<>();
            for (Vec2d point : transformedPoints) {
                screenPoints.add(camera.worldToScreen(point));
            }
            
            // 绘制正弦曲线
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
            LOGGER.error("渲染正弦曲线ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints;
        
        try {
            // 添加控制点
            keyPoints = new ArrayList<>(getControlPoints());
            
            // 添加正弦曲线上的关键点
            List<Vec2d> sinePoints = getPoints();
            if (sinePoints.size() > 2) {
                // 添加中点
                keyPoints.add(sinePoints.get(sinePoints.size() / 2));
                
                // 添加四分位点
                if (sinePoints.size() > 4) {
                    keyPoints.add(sinePoints.get(sinePoints.size() / 4)); // 1/4点
                    keyPoints.add(sinePoints.get(3 * sinePoints.size() / 4)); // 3/4点
                }
                
                // 添加极值点（波峰和波谷）
                Vec2d highest = sinePoints.getFirst();
                Vec2d lowest = sinePoints.getFirst();
                for (Vec2d point : sinePoints) {
                    if (point.y > highest.y) {
                        highest = point;
                    }
                    if (point.y < lowest.y) {
                        lowest = point;
                    }
                }
                keyPoints.add(highest); // 波峰
                keyPoints.add(lowest);  // 波谷
            }
            
            // 注意：getControlPoints()和getPoints()已经应用了变换，所以这里不需要再次应用
            
        } catch (Exception e) {
            LOGGER.error("获取正弦曲线关键点时发生错误: {}", e.getMessage(), e);
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
} 