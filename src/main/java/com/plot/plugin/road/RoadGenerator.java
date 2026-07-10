package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.client.MinecraftClient;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import com.plot.core.command.commands.GenerateRoadCommand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadGenerator");
    
    private final RoadSystemConfig config;
    private final CoordinateTransformer coordinateTransformer;
    
    public RoadSystemConfig getConfig() {
        return config;
    }

    public RoadGenerator(RoadSystemConfig config, CoordinateTransformer coordinateTransformer) {
        this.config = config;
        this.coordinateTransformer = coordinateTransformer;
    }
    
    /**
     * 道路生成结果
     */
    public static class RoadGenerationResult {
        public final List<BlockPos> roadBlocks = new ArrayList<>();
        public final List<BlockPos> sidewalkBlocks = new ArrayList<>();
        public final List<BlockPos> bridgeBlocks = new ArrayList<>();
        public final List<BlockPos> tunnelBlocks = new ArrayList<>();
        public final List<BlockPos> streetlightBlocks = new ArrayList<>();
        public final Map<BlockPos, GenerateRoadCommand.BlockRecord> placementRecords = new LinkedHashMap<>();
        public int cutVolume;
        public int fillVolume;
        public int bridgeCount;
        public int tunnelCount;
        public int streetlightCount;
        public double pathLength;
        
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
    
    private List<Vec2d> extractPathPoints(Shape path) {
        return RoadGeometryUtils.extractShapePoints(path);
    }
    
    /**
     * 基于路网边生成道路（不依赖 Shape）
     */
    public RoadGenerationResult generateEdge(RoadEdge edge, RoadNode startNode, RoadNode endNode, World world) {
        if (edge == null || world == null) {
            LOGGER.warn("道路边或世界为空，无法生成");
            return new RoadGenerationResult(0);
        }

        List<Vec2d> pathPoints = edge.getCenterlinePoints();
        if (pathPoints.size() < 2) {
            LOGGER.warn("道路中心线点数不足");
            return new RoadGenerationResult(0);
        }

        try {
            List<PathSegment> segments = samplePath(pathPoints);
            List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
                segments, world, edge, startNode);
            List<BridgeSegment> bridges = detectBridges(segments, heightInfos);
            List<TunnelSegment> tunnels = detectTunnels(segments, heightInfos, world);

            double halfWidth = edge.getEffectiveWidth(config) / 2.0;
            List<Vec2d> leftBoundary = OffsetHandler.offsetPolyline(pathPoints, halfWidth);
            List<Vec2d> rightBoundary = OffsetHandler.offsetPolyline(pathPoints, -halfWidth);

            RoadGenerationResult result = generateRoadBlocksFromBoundaries(
                segments, heightInfos, leftBoundary, rightBoundary, bridges, tunnels, world,
                getBlockIdFromMaterial(edge.getEffectiveMaterial(config)));

            if (edge.getEffectiveIncludeSidewalk(config)) {
                double sidewalkOffset = halfWidth + edge.getEffectiveSidewalkWidth(config) / 2.0;
                List<Vec2d> leftSidewalk = OffsetHandler.offsetPolyline(pathPoints, sidewalkOffset);
                List<Vec2d> rightSidewalk = OffsetHandler.offsetPolyline(pathPoints, -sidewalkOffset);
                generateSidewalkBlocks(result, segments, heightInfos, leftSidewalk, rightSidewalk,
                    edge.getEffectiveSidewalkWidth(config), world,
                    getBlockIdFromMaterial(edge.getEffectiveSidewalkMaterial(config)));
            }

            Integer spacing = edge.getStreetlightSpacing();
            if (spacing != null && spacing > 0) {
                generateStreetlights(result, pathPoints, edge, world);
            }

            result.pathLength = edge.getLength();
            return result;
        } catch (Exception e) {
            LOGGER.error("生成道路边失败: {}", e.getMessage(), e);
            return new RoadGenerationResult(0);
        }
    }

    public void mergeResult(RoadGenerationResult target, RoadGenerationResult source) {
        if (target == null || source == null) {
            return;
        }
        target.roadBlocks.addAll(source.roadBlocks);
        target.sidewalkBlocks.addAll(source.sidewalkBlocks);
        target.bridgeBlocks.addAll(source.bridgeBlocks);
        target.tunnelBlocks.addAll(source.tunnelBlocks);
        target.streetlightBlocks.addAll(source.streetlightBlocks);
        target.placementRecords.putAll(source.placementRecords);
        target.cutVolume += source.cutVolume;
        target.fillVolume += source.fillVolume;
        target.bridgeCount += source.bridgeCount;
        target.tunnelCount += source.tunnelCount;
        target.streetlightCount += source.streetlightCount;
        target.pathLength += source.pathLength;
    }

    public void mergeJunctionBlocks(RoadGenerationResult target, RoadJunctionGenerator.JunctionBlocks junction) {
        if (target == null || junction == null) {
            return;
        }
        target.roadBlocks.addAll(junction.roadBlocks);
        target.sidewalkBlocks.addAll(junction.sidewalkBlocks);
    }

    /**
     * 计算路口节点处的目标路面高度（汇聚各相连边在节点处的高度均值）
     */
    public int computeJunctionTargetHeight(RoadNode node, RoadNetwork network, World world) {
        if (node == null || network == null || world == null) {
            return 64;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }

        List<Integer> heights = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            heights.add(getTargetHeightAtNode(edge, node, network, world));
        }

        if (heights.isEmpty()) {
            return getTopHeight(world, canvasToBlockPos(node.getPosition()));
        }

        int min = heights.stream().mapToInt(Integer::intValue).min().orElse(64);
        int max = heights.stream().mapToInt(Integer::intValue).max().orElse(64);
        if (max - min > 2) {
            LOGGER.warn("路口节点 {} 汇聚道路高度不一致 {}，使用平均值拼接", node.getId(), heights);
        }
        return (int) Math.round(heights.stream().mapToInt(Integer::intValue).average().orElse(min));
    }

    /**
     * 获取单条边在指定节点处的目标路面高度
     */
    public int getTargetHeightAtNode(RoadEdge edge, RoadNode node, RoadNetwork network, World world) {
        if (edge == null || node == null || world == null) {
            return 64;
        }
        if (node.getManualElevation() != null) {
            return node.getManualElevation().intValue();
        }

        RoadNode startNode = network != null ? network.getNode(edge.getStartNodeId()) : null;
        List<PathSegment> segments = samplePath(edge.getCenterlinePoints());
        if (segments.isEmpty()) {
            return getTopHeight(world, canvasToBlockPos(node.getPosition()));
        }

        List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(segments, world, edge, startNode);
        if (heightInfos.isEmpty()) {
            return getTopHeight(world, canvasToBlockPos(node.getPosition()));
        }

        if (edge.getStartNodeId().equals(node.getId())) {
            return heightInfos.getFirst().targetStart;
        }
        if (edge.getEndNodeId().equals(node.getId())) {
            return heightInfos.getLast().targetEnd;
        }
        return getTopHeight(world, canvasToBlockPos(node.getPosition()));
    }

    public BlockPos toBlockPos(Vec2d canvasPos, int y) {
        BlockPos base = canvasToBlockPos(canvasPos);
        return new BlockPos(base.getX(), y, base.getZ());
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

    private List<SegmentHeightInfo> calculateSegmentHeightsForEdge(
            List<PathSegment> segments, World world, RoadEdge edge, RoadNode startNode) {
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        if (segments.isEmpty()) {
            return heightInfos;
        }

        int currentHeight;
        if (startNode != null && startNode.getManualElevation() != null) {
            currentHeight = startNode.getManualElevation().intValue();
        } else {
            Vec2d firstPoint = segments.getFirst().start;
            currentHeight = getTopHeight(world, canvasToBlockPos(firstPoint));
        }

        double accumulatedDistance = 0.0;
        for (PathSegment segment : segments) {
            float maxSlopePercent = edge.getEffectiveMaxSlope(accumulatedDistance, config);
            double maxSlopeRatio = maxSlopePercent / 100.0;

            BlockPos startBlockPos = canvasToBlockPos(segment.start);
            BlockPos endBlockPos = canvasToBlockPos(segment.end);
            int groundStart = getTopHeight(world, startBlockPos);
            int groundEnd = getTopHeight(world, endBlockPos);

            double maxRise = segment.distance * maxSlopeRatio;
            int idealRise = groundEnd - groundStart;
            int targetStart = currentHeight;
            int targetEnd;

            if (Math.abs(idealRise) <= maxRise) {
                targetEnd = groundEnd;
            } else if (idealRise > 0) {
                targetEnd = targetStart + (int) maxRise;
            } else {
                targetEnd = targetStart - (int) maxRise;
            }

            double actualSlope = segment.distance > 0
                ? Math.abs(targetEnd - targetStart) / segment.distance * 100.0
                : 0;
            heightInfos.add(new SegmentHeightInfo(
                segment, groundStart, groundEnd, targetStart, targetEnd, actualSlope));
            currentHeight = targetEnd;
            accumulatedDistance += segment.distance;
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

    private RoadGenerationResult generateRoadBlocksFromBoundaries(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels,
            World world,
            String blockId) {
        RoadGenerationResult result = new RoadGenerationResult(
            segments.stream().mapToDouble(s -> s.distance).sum());
        result.bridgeCount = bridges.size();
        result.tunnelCount = tunnels.size();

        int cutVolume = 0;
        int fillVolume = 0;
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));

            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d point = segment.start.lerp(segment.end, t);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);

                Vec2d left = interpolateBoundary(leftBoundary, t, i, segments.size());
                Vec2d right = interpolateBoundary(rightBoundary, t, i, segments.size());
                fillBetweenPoints(result, point, left, right, targetY, world, blockId, projectionHandler);

                int groundY = getTopHeight(world, canvasToBlockPos(point));
                if (targetY < groundY) {
                    cutVolume += (groundY - targetY);
                } else if (targetY > groundY) {
                    fillVolume += (targetY - groundY);
                }
            }
        }

        result.cutVolume = cutVolume;
        result.fillVolume = fillVolume;
        return result;
    }

    private void generateSidewalkBlocks(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            int sidewalkWidth,
            World world,
            String blockId) {
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                Vec2d left = interpolateBoundary(leftBoundary, t, i, segments.size());
                Vec2d right = interpolateBoundary(rightBoundary, t, i, segments.size());
                placeSidewalkStrip(result, left, sidewalkWidth, targetY, world, blockId, projectionHandler);
                placeSidewalkStrip(result, right, sidewalkWidth, targetY, world, blockId, projectionHandler);
            }
        }
    }

    private void generateStreetlights(RoadGenerationResult result, List<Vec2d> pathPoints,
                                      RoadEdge edge, World world) {
        int spacing = edge.getStreetlightSpacing();
        double skipDistance = edge.getEffectiveWidth(config);
        double offset = edge.getEffectiveWidth(config) / 2.0
            + (edge.getEffectiveIncludeSidewalk(config) ? edge.getEffectiveSidewalkWidth(config) : 0)
            + 0.5;

        List<Vec2d> samples = RoadGeometryUtils.sampleAlongPath(pathPoints, spacing, skipDistance);
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        boolean placeLeft = true;

        for (Vec2d sample : samples) {
            int index = Math.max(0, Math.min(pathPoints.size() - 2,
                RoadGeometryUtils.findNearestSegmentIndex(pathPoints, sample)));
            Vec2d direction = pathPoints.get(index + 1).subtract(pathPoints.get(index)).normalize();
            Vec2d normal = new Vec2d(-direction.y, direction.x);
            double side = placeLeft ? offset : -offset;
            Vec2d lightPos = sample.add(normal.multiply(side));
            BlockPos blockPos = canvasToBlockPos(lightPos);
            int groundY = getTopHeight(world, blockPos);
            BlockPos finalPos = new BlockPos(blockPos.getX(), groundY + 1, blockPos.getZ());
            recordBlock(result, finalPos, "minecraft:lantern", projectionHandler);
            result.streetlightBlocks.add(finalPos);
            placeLeft = !placeLeft;
        }
        result.streetlightCount = result.streetlightBlocks.size();
    }

    private Vec2d interpolateBoundary(List<Vec2d> boundary, double t, int segmentIndex, int totalSegments) {
        if (boundary == null || boundary.isEmpty()) {
            return new Vec2d(0, 0);
        }
        int index = Math.min(
            boundary.size() - 1,
            Math.max(0, (int) Math.round((segmentIndex + t) / Math.max(1, totalSegments) * (boundary.size() - 1)))
        );
        return boundary.get(index);
    }

    private void fillBetweenPoints(
            RoadGenerationResult result,
            Vec2d center,
            Vec2d left,
            Vec2d right,
            int targetY,
            World world,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        BlockPos leftPos = canvasToBlockPos(left);
        BlockPos rightPos = canvasToBlockPos(right);
        int minX = Math.min(leftPos.getX(), rightPos.getX());
        int maxX = Math.max(leftPos.getX(), rightPos.getX());
        int minZ = Math.min(leftPos.getZ(), rightPos.getZ());
        int maxZ = Math.max(leftPos.getZ(), rightPos.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, targetY, z);
                recordBlock(result, pos, blockId, projectionHandler);
                result.roadBlocks.add(pos);
            }
        }
    }

    private void placeSidewalkStrip(
            RoadGenerationResult result,
            Vec2d center,
            int width,
            int targetY,
            World world,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        BlockPos centerPos = canvasToBlockPos(center);
        int half = Math.max(0, width / 2);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos pos = new BlockPos(centerPos.getX() + dx, targetY, centerPos.getZ() + dz);
                recordBlock(result, pos, blockId, projectionHandler);
                result.sidewalkBlocks.add(pos);
            }
        }
    }

    private void recordBlock(
            RoadGenerationResult result,
            BlockPos pos,
            String newBlockId,
            BlockProjectionHandler projectionHandler) {
        if (!result.placementRecords.containsKey(pos)) {
            String previous = projectionHandler.getBlockIdAt(pos);
            result.placementRecords.put(pos, new GenerateRoadCommand.BlockRecord(pos, previous, newBlockId));
        }
    }

    private String getBlockIdFromMaterial(String material) {
        return switch (material) {
            case "material.plot.concrete", "混凝土" -> "minecraft:white_concrete";
            case "material.plot.gravel", "砂砾" -> "minecraft:gravel";
            case "material.plot.planks", "木板" -> "minecraft:oak_planks";
            case "material.plot.stone", "石头" -> "minecraft:stone";
            default -> "minecraft:stone";
        };
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
