package com.masterplanner.plugin.road;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.FreeDrawPath;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.plugin.config.RoadSystemConfig;
import com.masterplanner.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路生成器
 * 
 * 负责将2D路径转换为3D道路方块，包括：
 * - 路径采样和分段
 * - 地形高度检测
 * - 坡度限制和调整
 * - 桥/隧道检测和生成
 * - 挖填方计算
 */
public class RoadGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/RoadGenerator");
    
    private final RoadSystemConfig config;
    private final CoordinateTransformer coordinateTransformer;
    
    public RoadGenerator(RoadSystemConfig config, CoordinateTransformer coordinateTransformer) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
    }
    
    /**
     * 道路生成结果
     */
    public static class RoadGenerationResult {
        public final List<BlockPos> roadBlocks = new ArrayList<>();
        public final List<BlockPos> bridgeBlocks = new ArrayList<>();
        public final List<BlockPos> tunnelBlocks = new ArrayList<>();
        public int cutVolume;      // 挖方量（方块数）
        public int fillVolume;     // 填方量（方块数）
        public int bridgeCount;    // 桥梁数量
        public int tunnelCount;    // 隧道数量
        public double pathLength;  // 路径长度（米）
        
        public RoadGenerationResult(double pathLength) {
            this.pathLength = pathLength;
        }
    }
    
    /**
     * 生成道路
     * 
     * @param path 路径图形（PolylineShape、FreeDrawPath或BezierCurveShape）
     * @return 道路生成结果
     */
    public RoadGenerationResult generateRoad(Shape path) {
        if (path == null) {
            LOGGER.warn("路径为空，无法生成道路");
            return new RoadGenerationResult(0);
        }
        
        try {
            // 1. 从路径中提取点列表
            List<Vec2d> pathPoints = extractPathPoints(path);
            if (pathPoints == null || pathPoints.size() < 2) {
                LOGGER.warn("路径点数不足，无法生成道路");
                return new RoadGenerationResult(0);
            }
            
            LOGGER.info("开始生成道路，路径点数: {}", pathPoints.size());
            
            // 2. 采样路径点（细分以确保足够的采样密度）
            List<PathSegment> segments = samplePath(pathPoints);
            LOGGER.debug("路径分段数: {}", segments.size());
            
            // 3. 获取Minecraft世界（客户端）
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                LOGGER.error("无法获取Minecraft客户端或世界");
                return new RoadGenerationResult(0);
            }
            World world = client.world;
            
            // 4. 计算每个分段的目标高度（考虑坡度限制）
            List<SegmentHeightInfo> heightInfos = calculateSegmentHeights(segments, world);
            
            // 5. 检测桥和隧道需求
            List<BridgeSegment> bridges = detectBridges(segments, heightInfos);
            List<TunnelSegment> tunnels = detectTunnels(segments, heightInfos, world);
            
            // 6. 生成道路方块（挖填）
            RoadGenerationResult result = generateRoadBlocks(segments, heightInfos, bridges, tunnels, world);
            
            LOGGER.info("道路生成完成: 挖{} 填{} 桥{}座 隧道{}段", 
                result.cutVolume, result.fillVolume, result.bridgeCount, result.tunnelCount);
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("生成道路时发生错误: {}", e.getMessage(), e);
            return new RoadGenerationResult(0);
        }
    }
    
    /**
     * 从路径图形中提取点列表
     */
    private List<Vec2d> extractPathPoints(Shape path) {
        if (path instanceof PolylineShape) {
            return path.getPoints();
        } else if (path instanceof FreeDrawPath) {
            return path.getPoints();
        } else if (path instanceof BezierCurveShape) {
            // 贝塞尔曲线需要采样
            return sampleBezierCurve((BezierCurveShape) path, 20);
        }
        return null;
    }
    
    /**
     * 采样贝塞尔曲线
     */
    private List<Vec2d> sampleBezierCurve(BezierCurveShape curve, int samples) {
        List<Vec2d> points = new ArrayList<>();
        List<Vec2d> controlPoints = curve.getControlPoints();
        if (controlPoints == null || controlPoints.size() < 2) {
            return points;
        }
        
        // 简化实现：使用控制点线性插值（实际应该使用贝塞尔曲线公式）
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Vec2d p1 = controlPoints.get(i);
            Vec2d p2 = controlPoints.get(i + 1);
            for (int j = 0; j < samples; j++) {
                double t = (double) j / samples;
                points.add(p1.lerp(p2, t));
            }
        }
        return points;
    }
    
    /**
     * 路径分段
     */
    private static class PathSegment {
        final Vec2d start;
        final Vec2d end;
        final double distance;
        
        PathSegment(Vec2d start, Vec2d end) {
            this.start = start;
            this.end = end;
            this.distance = start.distance(end);
        }
    }
    
    /**
     * 分段高度信息
     */
    private static class SegmentHeightInfo {
        final PathSegment segment;
        final int groundStart;      // 起始地面高度
        final int groundEnd;        // 结束地面高度
        final int targetStart;      // 目标起始高度（考虑坡度限制）
        final int targetEnd;        // 目标结束高度（考虑坡度限制）
        final double slope;         // 实际坡度（百分比）
        
        SegmentHeightInfo(PathSegment segment, int groundStart, int groundEnd, 
                         int targetStart, int targetEnd, double slope) {
            this.segment = segment;
            this.groundStart = groundStart;
            this.groundEnd = groundEnd;
            this.targetStart = targetStart;
            this.targetEnd = targetEnd;
            this.slope = slope;
        }
    }
    
    /**
     * 桥段
     */
    private static class BridgeSegment {
        final PathSegment segment;
        final int bridgeHeight;
        
        BridgeSegment(PathSegment segment, int bridgeHeight) {
            this.segment = segment;
            this.bridgeHeight = bridgeHeight;
        }
    }
    
    /**
     * 隧道段
     */
    private static class TunnelSegment {
        final PathSegment segment;
        final int tunnelDepth;
        
        TunnelSegment(PathSegment segment, int tunnelDepth) {
            this.segment = segment;
            this.tunnelDepth = tunnelDepth;
        }
    }
    
    /**
     * 采样路径点，创建分段
     */
    private List<PathSegment> samplePath(List<Vec2d> pathPoints) {
        List<PathSegment> segments = new ArrayList<>();
        
        // 采样密度：每1米至少1个点
        double minSampleDistance = 1.0;
        
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d start = pathPoints.get(i);
            Vec2d end = pathPoints.get(i + 1);
            double distance = start.distance(end);
            
            if (distance < minSampleDistance) {
                // 距离太短，直接添加
                segments.add(new PathSegment(start, end));
            } else {
                // 细分采样
                int samples = (int) Math.ceil(distance / minSampleDistance);
                Vec2d prev = start;
                for (int j = 1; j <= samples; j++) {
                    double t = (double) j / samples;
                    Vec2d current = start.lerp(end, t);
                    segments.add(new PathSegment(prev, current));
                    prev = current;
                }
            }
        }
        
        return segments;
    }
    
    /**
     * 计算分段高度（考虑坡度限制）
     */
    private List<SegmentHeightInfo> calculateSegmentHeights(List<PathSegment> segments, World world) {
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        
        if (segments.isEmpty()) {
            return heightInfos;
        }
        
        // 获取第一个点的地面高度
        Vec2d firstPoint = segments.getFirst().start;
        BlockPos firstBlockPos = canvasToBlockPos(firstPoint);
        int currentHeight = getTopHeight(world, firstBlockPos);
        
        double maxSlopePercent = config.getMaxSlope();
        double maxSlopeRatio = maxSlopePercent / 100.0; // 转换为比率
        
        for (PathSegment segment : segments) {
            // 获取起始和结束的地面高度
            BlockPos startBlockPos = canvasToBlockPos(segment.start);
            BlockPos endBlockPos = canvasToBlockPos(segment.end);
            
            int groundStart = getTopHeight(world, startBlockPos);
            int groundEnd = getTopHeight(world, endBlockPos);
            
            // 计算最大允许高度差（基于坡度和距离）
            double maxRise = segment.distance * maxSlopeRatio;
            int idealRise = groundEnd - groundStart;
            
            // 计算目标高度（应用坡度限制）
            int targetStart = currentHeight;
            int targetEnd;
            
            if (Math.abs(idealRise) <= maxRise) {
                // 坡度在限制内，使用地面高度
                targetEnd = groundEnd;
            } else {
                // 坡度超过限制，按最大坡度调整
                if (idealRise > 0) {
                    targetEnd = targetStart + (int) maxRise;
                } else {
                    targetEnd = targetStart - (int) maxRise;
                }
            }
            
            // 计算实际坡度
            double actualSlope = segment.distance > 0 ? 
                Math.abs(targetEnd - targetStart) / segment.distance * 100.0 : 0;
            
            heightInfos.add(new SegmentHeightInfo(segment, groundStart, groundEnd, targetStart, targetEnd, actualSlope));
            
            // 更新当前高度
            currentHeight = targetEnd;
        }
        
        return heightInfos;
    }
    
    /**
     * 检测桥需求
     */
    private List<BridgeSegment> detectBridges(List<PathSegment> segments, List<SegmentHeightInfo> heightInfos) {
        List<BridgeSegment> bridges = new ArrayList<>();
        int bridgeThreshold = config.getBridgeThreshold();
        
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            
            // 如果目标高度明显高于地面高度，需要建桥
            int heightDifference = info.targetStart - info.groundStart;
            if (heightDifference > bridgeThreshold) {
                bridges.add(new BridgeSegment(info.segment, heightDifference));
            }
        }
        
        return bridges;
    }
    
    /**
     * 检测隧道需求
     */
    private List<TunnelSegment> detectTunnels(List<PathSegment> segments, List<SegmentHeightInfo> heightInfos, World world) {
        List<TunnelSegment> tunnels = new ArrayList<>();
        int tunnelThreshold = config.getTunnelThreshold();
        
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            
            // 如果地面高度明显高于目标高度，需要挖隧道
            int heightDifference = info.groundStart - info.targetStart;
            if (heightDifference > tunnelThreshold) {
                // 检查是否为实心方块（粗略检测）
                BlockPos pos = canvasToBlockPos(info.segment.start);
                if (isSolidBlock(world, pos)) {
                    tunnels.add(new TunnelSegment(info.segment, heightDifference));
                }
            }
        }
        
        return tunnels;
    }
    
    /**
     * 生成道路方块
     */
    private RoadGenerationResult generateRoadBlocks(List<PathSegment> segments, 
                                                     List<SegmentHeightInfo> heightInfos,
                                                     List<BridgeSegment> bridges,
                                                     List<TunnelSegment> tunnels,
                                                     World world) {
        RoadGenerationResult result = new RoadGenerationResult(
            segments.stream().mapToDouble(s -> s.distance).sum());
        result.bridgeCount = bridges.size();
        result.tunnelCount = tunnels.size();
        
        int roadWidth = config.getRoadWidth();
        int cutVolume = 0;
        int fillVolume = 0;
        
        // 生成道路方块
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            
            // 采样分段上的点
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d point = segment.start.lerp(segment.end, t);
                
                // 计算目标高度（线性插值）
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                
                // 生成道路宽度的方块
                for (int offsetX = -roadWidth / 2; offsetX <= roadWidth / 2; offsetX++) {
                    for (int offsetZ = -roadWidth / 2; offsetZ <= roadWidth / 2; offsetZ++) {
                        Vec2d roadPoint = point.add(new Vec2d(offsetX, offsetZ));
                        BlockPos roadBlockPos = canvasToBlockPos(roadPoint);
                        roadBlockPos = new BlockPos(roadBlockPos.getX(), targetY, roadBlockPos.getZ());
                        
                        // 获取地面高度
                        int groundY = getTopHeight(world, new BlockPos(roadBlockPos.getX(), 0, roadBlockPos.getZ()));
                        
                        // 计算挖填方
                        if (targetY < groundY) {
                            cutVolume += (groundY - targetY);
                            result.roadBlocks.add(roadBlockPos);
                        } else if (targetY > groundY) {
                            fillVolume += (targetY - groundY);
                            result.roadBlocks.add(roadBlockPos);
                        } else {
                            result.roadBlocks.add(roadBlockPos);
                        }
                    }
                }
            }
        }
        
        result.cutVolume = cutVolume;
        result.fillVolume = fillVolume;
        
        return result;
    }
    
    /**
     * 将画布坐标转换为BlockPos（XZ平面）
     */
    private BlockPos canvasToBlockPos(Vec2d canvasPos) {
        if (coordinateTransformer != null) {
            Vec2d worldPos = coordinateTransformer.canvasToMinecraftWorld(canvasPos);
            if (worldPos != null) {
                return new BlockPos((int) worldPos.x, 0, (int) worldPos.y);
            }
        }
        // 回退：直接使用画布坐标（假设1:1映射）
        return new BlockPos((int) canvasPos.x, 0, (int) canvasPos.y);
    }
    
    /**
     * 获取地形顶部高度
     */
    private int getTopHeight(World world, BlockPos pos) {
        try {
            // 使用WORLD_SURFACE高度图（地表高度）
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
            return topPos != null ? topPos.getY() : pos.getY();
        } catch (Exception e) {
            LOGGER.warn("获取地形高度失败 ({}, {}): {}", pos.getX(), pos.getZ(), e.getMessage());
            return 64; // 默认海平面
        }
    }
    
    /**
     * 检查是否为实心方块（粗略检测）
     */
    private boolean isSolidBlock(World world, BlockPos pos) {
        try {
            var blockState = world.getBlockState(pos);
            return blockState != null && !blockState.isAir();
        } catch (Exception e) {
            return false;
        }
    }
}
