package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.client.MinecraftClient;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.RoadNode;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.GenerateRoadCommand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
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
    public RoadGenerationResult generateEdge(
            RoadNetwork network, RoadEdge edge, RoadNode startNode, RoadNode endNode, World world) {
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
            ResolvedCrossSection crossSection = RoadModelUtils.resolveCrossSection(network, edge, config);
            List<PathSegment> segments = samplePath(pathPoints);
            List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
                segments, world, network, edge, startNode, endNode);
            List<BridgeSegment> bridges = detectBridges(segments, heightInfos);
            List<TunnelSegment> tunnels = detectTunnels(segments, heightInfos, world);

            double halfWidth = crossSection.carriagewayHalfWidth();
            List<Vec2d> leftBoundary = OffsetHandler.offsetPolyline(pathPoints, halfWidth);
            List<Vec2d> rightBoundary = OffsetHandler.offsetPolyline(pathPoints, -halfWidth);

            RoadGenerationResult result = generateRoadBlocksFromBoundaries(
                segments, heightInfos, leftBoundary, rightBoundary, bridges, tunnels, world,
                getBlockIdFromMaterial(crossSection.carriagewayMaterial));

            int shoulderWidth = crossSection.includeShoulder ? crossSection.shoulderWidth : 0;
            if (crossSection.includeShoulder && shoulderWidth > 0) {
                double shoulderOffset = halfWidth + shoulderWidth / 2.0;
                List<Vec2d> leftShoulder = OffsetHandler.offsetPolyline(pathPoints, shoulderOffset);
                List<Vec2d> rightShoulder = OffsetHandler.offsetPolyline(pathPoints, -shoulderOffset);
                generateShoulderBlocks(result, segments, heightInfos, leftShoulder, rightShoulder,
                    shoulderWidth, getBlockIdFromMaterial(crossSection.shoulderMaterial));
                generateSlopeBatterBlocks(result, segments, heightInfos, leftShoulder, rightShoulder,
                    shoulderWidth, pathPoints, world);
            }

            if (crossSection.includeSidewalk && crossSection.sidewalkWidth > 0) {
                double sidewalkOffset = halfWidth + shoulderWidth + crossSection.sidewalkWidth / 2.0;
                List<Vec2d> leftSidewalk = OffsetHandler.offsetPolyline(pathPoints, sidewalkOffset);
                List<Vec2d> rightSidewalk = OffsetHandler.offsetPolyline(pathPoints, -sidewalkOffset);
                generateSidewalkBlocks(result, segments, heightInfos, leftSidewalk, rightSidewalk,
                    crossSection.sidewalkWidth, world,
                    getBlockIdFromMaterial(crossSection.sidewalkMaterial));
            }

            if (crossSection.includeDrain) {
                double drainageOffset = crossSection.outerDrainageOffset();
                List<Vec2d> leftDrainage = OffsetHandler.offsetPolyline(pathPoints, drainageOffset);
                List<Vec2d> rightDrainage = OffsetHandler.offsetPolyline(pathPoints, -drainageOffset);
                generateDrainageChannels(result, segments, heightInfos, leftDrainage, rightDrainage,
                    getBlockIdFromMaterial("material.plot.gravel"));
            }

            if (crossSection.includeMedian && crossSection.medianWidth > 0) {
                double halfMedian = crossSection.medianWidth / 2.0;
                List<Vec2d> leftMedian = OffsetHandler.offsetPolyline(pathPoints, -halfMedian);
                List<Vec2d> rightMedian = OffsetHandler.offsetPolyline(pathPoints, halfMedian);
                generateMedianBlocks(
                    result,
                    segments,
                    heightInfos,
                    leftMedian,
                    rightMedian,
                    getBlockIdFromMaterial(crossSection.medianMaterial));
            }

            if (crossSection.laneDividers || crossSection.centerLine) {
                generateLaneMarkings(result, segments, heightInfos, pathPoints, crossSection);
            }

            Integer spacing = crossSection.streetlightSpacing;
            if (spacing != null && spacing > 0) {
                generateStreetlights(result, pathPoints, network, edge, world, shoulderWidth);
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

    public void mergeJunctionBlocks(
            RoadGenerationResult target,
            RoadJunctionGenerator.JunctionBlocks junction,
            String roadBlockId,
            String sidewalkBlockId) {
        if (target == null || junction == null) {
            return;
        }
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        for (BlockPos pos : junction.roadBlocks) {
            target.roadBlocks.add(pos);
            recordBlockOverride(target, pos, roadBlockId, projectionHandler);
        }
        for (BlockPos pos : junction.sidewalkBlocks) {
            target.sidewalkBlocks.add(pos);
            recordBlockOverride(target, pos, sidewalkBlockId, projectionHandler);
        }
    }

    private void recordBlockOverride(
            RoadGenerationResult result,
            BlockPos pos,
            String newBlockId,
            BlockProjectionHandler projectionHandler) {
        String previousBlockId = result.placementRecords.containsKey(pos)
            ? result.placementRecords.get(pos).previousBlockId
            : projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previousBlockId, newBlockId));
    }

    /**
     * @deprecated 使用带材质参数的 {@link #mergeJunctionBlocks(RoadGenerationResult, JunctionBlocks, String, String)}
     */
    @Deprecated
    public void mergeJunctionBlocks(RoadGenerationResult target, RoadJunctionGenerator.JunctionBlocks junction) {
        mergeJunctionBlocks(
            target,
            junction,
            getBlockIdFromMaterial(config.getSelectedMaterial()),
            getBlockIdFromMaterial(config.getSelectedMaterial())
        );
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
            return getGroundHeightAtNode(world, node, network);
        }

        int min = heights.stream().mapToInt(Integer::intValue).min().orElse(64);
        int max = heights.stream().mapToInt(Integer::intValue).max().orElse(64);
        if (max - min > 2) {
            LOGGER.warn("路口节点 {} 汇聚道路高度不一致 {}，使用平均值拼接", node.getId(), heights);
        }
        return RoadSlopeUtils.averageJunctionHeight(heights);
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
            return getGroundHeightAtNode(world, node, network);
        }

        List<SegmentHeightInfo> heightInfos = calculateSegmentHeightsForEdge(
            segments, world, network, edge, startNode, node);
        if (heightInfos.isEmpty()) {
            return getGroundHeightAtNode(world, node, network);
        }

        if (edge.getStartNodeId().equals(node.getId())) {
            return heightInfos.getFirst().targetStart;
        }
        if (edge.getEndNodeId().equals(node.getId())) {
            return heightInfos.getLast().targetEnd;
        }
        return getGroundHeightAtNode(world, node, network);
    }

    public BlockPos toBlockPos(Vec2d canvasPos, int y) {
        BlockPos base = RoadGeometryUtils.canvasToBlockXZ(canvasPos, coordinateTransformer);
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
        
        // 获取第一个点的横断面平均地面高度
        Vec2d firstPoint = segments.getFirst().start;
        PathSegment firstSegment = segments.getFirst();
        Vec2d firstTangent = firstSegment.end.subtract(firstSegment.start);
        double halfWidth = config.getRoadWidth() / 2.0;
        int currentHeight = getGroundHeightAtPoint(world, firstPoint, firstTangent, halfWidth);
        
        double maxSlopePercent = config.getMaxSlope();
        
        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            int groundStart = getGroundHeightAtPoint(world, segment.start, tangent, halfWidth);
            int groundEnd = getGroundHeightAtPoint(world, segment.end, tangent, halfWidth);
            
            int targetStart = currentHeight;
            int targetEnd = RoadSlopeUtils.computeTargetEndHeight(
                targetStart, groundStart, groundEnd, segment.distance, (float) maxSlopePercent);
            double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
                targetStart, targetEnd, segment.distance);
            
            heightInfos.add(new SegmentHeightInfo(segment, groundStart, groundEnd, targetStart, targetEnd, actualSlope));
            
            // 更新当前高度
            currentHeight = targetEnd;
        }
        
        return heightInfos;
    }

    private List<SegmentHeightInfo> calculateSegmentHeightsForEdge(
            List<PathSegment> segments, World world, RoadNetwork network, RoadEdge edge,
            RoadNode startNode, RoadNode endNode) {
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        if (segments.isEmpty()) {
            return heightInfos;
        }

        Integer manualStartHeight = startNode != null && startNode.getManualElevation() != null
            ? startNode.getManualElevation().intValue()
            : null;

        double halfWidth = RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0;
        List<Double> distances = new ArrayList<>();
        List<Integer> groundStarts = new ArrayList<>();
        List<Integer> groundEnds = new ArrayList<>();
        List<Float> maxSlopes = new ArrayList<>();
        double accumulatedDistance = 0.0;

        for (PathSegment segment : segments) {
            Vec2d tangent = segment.end.subtract(segment.start);
            distances.add(segment.distance);
            groundStarts.add(getGroundHeightAtPoint(world, segment.start, tangent, halfWidth));
            groundEnds.add(getGroundHeightAtPoint(world, segment.end, tangent, halfWidth));
            maxSlopes.add(RoadModelUtils.getEffectiveMaxSlope(network, edge, config, accumulatedDistance));
            accumulatedDistance += segment.distance;
        }

        List<Integer> targetEnds = RoadSlopeUtils.computeChainedTargetHeights(
            distances,
            groundStarts,
            groundEnds,
            maxSlopes,
            manualStartHeight,
            config.getMaxContinuousSlopeLength(),
            config.getRelaxedSlopeLength(),
            config.getRelaxedSlopePercent()
        );

        int currentHeight = manualStartHeight != null
            ? manualStartHeight
            : groundStarts.getFirst();

        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            int targetStart = currentHeight;
            int targetEnd = targetEnds.get(i);
            double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
                targetStart, targetEnd, segment.distance);
            heightInfos.add(new SegmentHeightInfo(
                segment, groundStarts.get(i), groundEnds.get(i),
                targetStart, targetEnd, actualSlope));
            currentHeight = targetEnd;
        }

        if (endNode != null && endNode.getManualElevation() != null && !heightInfos.isEmpty()) {
            applyManualEndHeight(heightInfos, network, edge, endNode.getManualElevation().intValue(), accumulatedDistance);
        }
        return heightInfos;
    }

    private void applyManualEndHeight(
            List<SegmentHeightInfo> heightInfos,
            RoadNetwork network,
            RoadEdge edge,
            int desiredEndHeight,
            double totalDistance) {
        int lastIndex = heightInfos.size() - 1;
        SegmentHeightInfo last = heightInfos.get(lastIndex);
        double lastSegmentStartDistance = Math.max(0.0, totalDistance - last.segment.distance);
        float maxSlopePercent = RoadModelUtils.getEffectiveMaxSlope(
            network, edge, config, lastSegmentStartDistance);
        int clampedEnd = clampTowardTarget(last.targetStart, desiredEndHeight, last.segment.distance, maxSlopePercent);
        if (clampedEnd == last.targetEnd) {
            return;
        }
        double actualSlope = RoadSlopeUtils.computeActualSlopePercent(
            last.targetStart, clampedEnd, last.segment.distance);
        heightInfos.set(lastIndex, new SegmentHeightInfo(
            last.segment, last.groundStart, last.groundEnd, last.targetStart, clampedEnd, actualSlope));
    }

    private static int clampTowardTarget(int fromHeight, int targetHeight, double distance, float maxSlopePercent) {
        double maxRise = distance * maxSlopePercent / 100.0;
        int delta = targetHeight - fromHeight;
        if (Math.abs(delta) <= maxRise) {
            return targetHeight;
        }
        return fromHeight + (int) (delta > 0 ? maxRise : -maxRise);
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
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));

            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d point = segment.start.lerp(segment.end, t);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);

                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                fillBetweenPoints(result, left, right, targetY, blockId, projectionHandler);

                int groundY = getTopHeight(world, canvasToBlockPos(point));
                if (targetY < groundY) {
                    cutVolume += (groundY - targetY);
                } else if (targetY > groundY) {
                    fillVolume += (targetY - groundY);
                }
            }
            accumulatedSegmentStart += segment.distance;
        }

        generateBridgeStructures(result, bridges, segments, heightInfos, leftBoundary, rightBoundary, world);
        generateTunnelStructures(result, tunnels, segments, heightInfos, leftBoundary, rightBoundary, world);

        result.cutVolume = cutVolume;
        result.fillVolume = fillVolume;
        return result;
    }

    private void generateShoulderBlocks(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            int shoulderWidth,
            String blockId) {
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                placeSidewalkStrip(result, left, shoulderWidth, targetY, blockId, projectionHandler);
                placeSidewalkStrip(result, right, shoulderWidth, targetY, blockId, projectionHandler);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void generateSlopeBatterBlocks(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftShoulder,
            List<Vec2d> rightShoulder,
            int shoulderWidth,
            List<Vec2d> pathPoints,
            World world) {
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        String fillBlockId = getBlockIdFromMaterial(config.getFillSlopeMaterial());
        String cutBlockId = config.getCutSlopeMaterial().isBlank()
            ? null
            : getBlockIdFromMaterial(config.getCutSlopeMaterial());
        float fillRatio = config.getFillSlopeRatio();
        float cutRatio = config.getCutSlopeRatio();
        int maxHorizontalRun = 32;
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;
        double outerOffset = shoulderWidth / 2.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftShoulder, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightShoulder, normalized);
                placeSlopeBatterAtPoint(result, left, targetY, outerOffset, pathPoints, world,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun, 1, projectionHandler);
                placeSlopeBatterAtPoint(result, right, targetY, outerOffset, pathPoints, world,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun, -1, projectionHandler);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void placeSlopeBatterAtPoint(
            RoadGenerationResult result,
            Vec2d shoulderCenter,
            int targetY,
            double outerOffset,
            List<Vec2d> pathPoints,
            World world,
            float fillRatio,
            float cutRatio,
            String fillBlockId,
            String cutBlockId,
            int maxHorizontalRun,
            int sideSign,
            BlockProjectionHandler projectionHandler) {
        int pathIndex = Math.max(0, Math.min(pathPoints.size() - 2,
            RoadGeometryUtils.findNearestSegmentIndex(pathPoints, shoulderCenter)));
        Vec2d direction = pathPoints.get(pathIndex + 1).subtract(pathPoints.get(pathIndex)).normalize();
        Vec2d normal = new Vec2d(-direction.y, direction.x).multiply(sideSign);
        Vec2d outerEdge = shoulderCenter.add(normal.multiply(outerOffset));

        BlockPos outerPos = canvasToBlockPos(outerEdge);
        int groundAtEdge = getTopHeight(world, outerPos);
        if (targetY == groundAtEdge) {
            return;
        }

        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        String surfaceBlockId = isFill ? fillBlockId : cutBlockId;

        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            targetY,
            profileDirection,
            horizontalOffset -> {
                Vec2d sample = outerEdge.add(normal.multiply(horizontalOffset));
                return getTopHeight(world, canvasToBlockPos(sample));
            },
            slopeRatio,
            maxHorizontalRun
        );

        for (int step = 1; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d sample = outerEdge.add(normal.multiply(horizontalOffset));
            BlockPos columnBase = canvasToBlockPos(sample);
            int groundY = getTopHeight(world, columnBase);

            if (isFill) {
                for (int y = groundY + 1; y <= slopeHeight; y++) {
                    BlockPos pos = new BlockPos(columnBase.getX(), y, columnBase.getZ());
                    recordBlock(result, pos, fillBlockId, projectionHandler);
                    result.sidewalkBlocks.add(pos);
                }
            } else {
                for (int y = slopeHeight + 1; y <= groundY; y++) {
                    BlockPos pos = new BlockPos(columnBase.getX(), y, columnBase.getZ());
                    recordBlock(result, pos, "minecraft:air", projectionHandler);
                    result.sidewalkBlocks.add(pos);
                }
                if (cutBlockId != null) {
                    BlockPos facePos = new BlockPos(columnBase.getX(), slopeHeight, columnBase.getZ());
                    recordBlock(result, facePos, surfaceBlockId, projectionHandler);
                    result.sidewalkBlocks.add(facePos);
                }
            }
        }
    }

    private void generateDrainageChannels(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            String blockId) {
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t) - 1;
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                placeSidewalkStrip(result, left, 1, targetY, blockId, projectionHandler);
                placeSidewalkStrip(result, right, 1, targetY, blockId, projectionHandler);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void generateBridgeStructures(
            RoadGenerationResult result,
            List<BridgeSegment> bridges,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            World world) {
        if (bridges.isEmpty()) {
            return;
        }
        String pillarBlockId = getBlockIdFromMaterial("material.plot.stone");
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();

        for (BridgeSegment bridge : bridges) {
            SegmentHeightInfo info = findHeightInfo(segments, heightInfos, bridge.segment);
            if (info == null) {
                continue;
            }
            int samples = Math.max(2, (int) Math.ceil(bridge.segment.distance));
            for (int j = 0; j <= samples; j++) {
                if (j % 4 != 0 && j != samples) {
                    continue;
                }
                double t = (double) j / samples;
                double normalized = findNormalizedDistance(segments, bridge.segment, t, totalLength);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                Vec2d center = bridge.segment.start.lerp(bridge.segment.end, t);
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                placeBridgePillars(result, center, targetY, world, pillarBlockId, projectionHandler);
                placeBridgePillars(result, left, targetY, world, pillarBlockId, projectionHandler);
                placeBridgePillars(result, right, targetY, world, pillarBlockId, projectionHandler);
            }
        }
    }

    private void generateTunnelStructures(
            RoadGenerationResult result,
            List<TunnelSegment> tunnels,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            World world) {
        if (tunnels.isEmpty()) {
            return;
        }
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();

        for (TunnelSegment tunnel : tunnels) {
            SegmentHeightInfo info = findHeightInfo(segments, heightInfos, tunnel.segment);
            if (info == null) {
                continue;
            }
            int headroom = Math.max(3, Math.min(6, tunnel.tunnelDepth + 1));
            int samples = Math.max(2, (int) Math.ceil(tunnel.segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                double normalized = findNormalizedDistance(segments, tunnel.segment, t, totalLength);
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                for (int y = targetY + 1; y <= targetY + headroom; y++) {
                    carveBetweenPoints(result, left, right, y, projectionHandler);
                }
            }
        }
    }

    private void placeBridgePillars(
            RoadGenerationResult result,
            Vec2d canvasPos,
            int deckY,
            World world,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        BlockPos base = canvasToBlockPos(canvasPos);
        int groundY = getTopHeight(world, base);
        if (deckY - groundY <= config.getBridgeThreshold()) {
            return;
        }
        for (int y = groundY + 1; y < deckY; y++) {
            BlockPos pillarPos = new BlockPos(base.getX(), y, base.getZ());
            recordBlock(result, pillarPos, blockId, projectionHandler);
            result.bridgeBlocks.add(pillarPos);
        }
    }

    private void carveBetweenPoints(
            RoadGenerationResult result,
            Vec2d left,
            Vec2d right,
            int y,
            BlockProjectionHandler projectionHandler) {
        for (BlockPos pos : rasterizeSpan(left, right, y, coordinateTransformer)) {
            recordBlock(result, pos, "minecraft:air", projectionHandler);
            result.tunnelBlocks.add(pos);
        }
    }

    private static SegmentHeightInfo findHeightInfo(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            PathSegment target) {
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            if (sameSegment(segments.get(i), target)) {
                return heightInfos.get(i);
            }
        }
        return null;
    }

    private static double findNormalizedDistance(
            List<PathSegment> segments,
            PathSegment target,
            double segmentT,
            double totalLength) {
        if (totalLength <= 1e-9) {
            return 0.0;
        }
        double accumulated = 0.0;
        for (PathSegment segment : segments) {
            if (sameSegment(segment, target)) {
                return (accumulated + segmentT * segment.distance) / totalLength;
            }
            accumulated += segment.distance;
        }
        return 0.0;
    }

    private static boolean sameSegment(PathSegment a, PathSegment b) {
        return a.start.distance(b.start) < 1e-3 && a.end.distance(b.end) < 1e-3;
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
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                placeSidewalkStrip(result, left, sidewalkWidth, targetY, blockId, projectionHandler);
                placeSidewalkStrip(result, right, sidewalkWidth, targetY, blockId, projectionHandler);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void generateMedianBlocks(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            String blockId) {
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                fillBetweenPoints(result, left, right, targetY, blockId, projectionHandler);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private void generateLaneMarkings(
            RoadGenerationResult result,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> pathPoints,
            ResolvedCrossSection crossSection) {
        String blockId = getBlockIdFromMaterial(crossSection.markingMaterial);
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        List<Double> offsets = new ArrayList<>();
        if (crossSection.centerLine) {
            offsets.add(0.0);
        }
        if (crossSection.laneDividers) {
            for (Double offset : crossSection.laneDividerOffsets) {
                if (offset != null && Math.abs(offset) > 1e-6) {
                    offsets.add(offset);
                }
            }
        }

        for (Double offset : offsets) {
            List<Vec2d> markingLine = OffsetHandler.offsetPolyline(pathPoints, offset);
            accumulatedSegmentStart = 0.0;
            for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
                SegmentHeightInfo info = heightInfos.get(i);
                PathSegment segment = segments.get(i);
                int samples = Math.max(2, (int) Math.ceil(segment.distance));
                for (int j = 0; j <= samples; j++) {
                    if (j % 2 != 0) {
                        continue;
                    }
                    double t = (double) j / samples;
                    int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                    double normalized = totalLength > 1e-9
                        ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                        : 0.0;
                    Vec2d point = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(markingLine, normalized);
                    placeMarkingStrip(result, point, targetY, blockId, projectionHandler);
                }
                accumulatedSegmentStart += segment.distance;
            }
        }
    }

    private void generateStreetlights(RoadGenerationResult result, List<Vec2d> pathPoints,
                                      RoadNetwork network, RoadEdge edge, World world, double shoulderWidth) {
        Integer spacingValue = RoadModelUtils.getStreetlightSpacing(network, edge);
        int spacing = spacingValue != null ? spacingValue : 0;
        double skipDistance = RoadModelUtils.getEffectiveWidth(network, edge, config);
        double offset = RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0
            + shoulderWidth
            + (RoadModelUtils.getEffectiveIncludeSidewalk(network, edge, config)
                ? RoadModelUtils.getEffectiveSidewalkWidth(network, edge, config) : 0)
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

    private void fillBetweenPoints(
            RoadGenerationResult result,
            Vec2d left,
            Vec2d right,
            int targetY,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        for (BlockPos pos : rasterizeSpan(left, right, targetY, coordinateTransformer)) {
            recordBlock(result, pos, blockId, projectionHandler);
            result.roadBlocks.add(pos);
        }
    }

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y) {
        return rasterizeSpan(left, right, y, null);
    }

    static List<BlockPos> rasterizeSpan(Vec2d left, Vec2d right, int y, CoordinateTransformer transformer) {
        if (left == null || right == null) {
            return List.of();
        }
        double span = left.distance(right);
        int steps = Math.max(1, (int) Math.ceil(span * 2.0));
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec2d sample = left.lerp(right, t);
            BlockPos base = RoadGeometryUtils.canvasToBlockXZ(sample, transformer);
            unique.add(new BlockPos(base.getX(), y, base.getZ()));
        }
        return new ArrayList<>(unique);
    }

    private void placeMarkingStrip(
            RoadGenerationResult result,
            Vec2d center,
            int targetY,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        BlockPos centerPos = canvasToBlockPos(center);
        BlockPos pos = new BlockPos(centerPos.getX(), targetY, centerPos.getZ());
        recordBlock(result, pos, blockId, projectionHandler);
        result.roadBlocks.add(pos);
    }

    private void placeSidewalkStrip(
            RoadGenerationResult result,
            Vec2d center,
            int width,
            int targetY,
            String blockId,
            BlockProjectionHandler projectionHandler) {
        BlockPos centerPos = canvasToBlockPos(center);
        int half = Math.max(0, (int) Math.ceil(width / 2.0));
        double radiusSquared = Math.max(0.25, width * width / 4.0);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if ((dx * dx + dz * dz) > radiusSquared) {
                    continue;
                }
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
        recordPlacementIfAbsent(result, pos, projectionHandler.getBlockIdAt(pos), newBlockId);
    }

    static void recordPlacementIfAbsent(
            RoadGenerationResult result,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        if (!result.placementRecords.containsKey(pos)) {
            result.placementRecords.put(pos, new BlockRecord(pos, previousBlockId, newBlockId));
        }
    }

    public String getBlockIdFromMaterial(String material) {
        return RoadMaterialUtils.resolveBlockId(material);
    }
    
    /**
     * 将画布坐标转换为BlockPos（XZ平面）
     */
    private BlockPos canvasToBlockPos(Vec2d canvasPos) {
        return RoadGeometryUtils.canvasToBlockXZ(canvasPos, coordinateTransformer);
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
     * 沿道路横断面采样地面高度并取平均（覆盖 [-halfWidth, +halfWidth]）
     */
    private int getGroundHeightAtPoint(World world, Vec2d center, Vec2d tangent, double halfWidth) {
        if (world == null || center == null || halfWidth <= 0) {
            return getTopHeight(world, canvasToBlockPos(center));
        }

        Vec2d normal = RoadGeometryUtils.leftNormal(tangent);
        List<Integer> heights = new ArrayList<>();
        for (int offset : RoadGeometryUtils.crossSectionSampleOffsets(halfWidth)) {
            Vec2d samplePos = center.add(normal.multiply(offset));
            heights.add(getTopHeight(world, canvasToBlockPos(samplePos)));
        }
        return RoadSlopeUtils.averageGroundHeight(heights);
    }

    private int getGroundHeightAtNode(World world, RoadNode node, RoadNetwork network) {
        if (node == null) {
            return 64;
        }
        return getGroundHeightAtPoint(
            world,
            node.getPosition(),
            resolveNodeTangent(node, network),
            resolveNodeHalfWidth(node, network)
        );
    }

    private Vec2d resolveNodeTangent(RoadNode node, RoadNetwork network) {
        if (node == null || network == null) {
            return null;
        }

        RoadEdge widestEdge = null;
        double widest = -1.0;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            double width = RoadModelUtils.getEffectiveWidth(network, edge, config);
            if (width > widest) {
                widest = width;
                widestEdge = edge;
            }
        }
        if (widestEdge == null) {
            return null;
        }

        List<Vec2d> points = widestEdge.getCenterlinePoints();
        if (points.size() < 2) {
            return null;
        }
        if (widestEdge.getStartNodeId().equals(node.getId())) {
            return points.get(1).subtract(points.get(0));
        }
        if (widestEdge.getEndNodeId().equals(node.getId())) {
            return points.get(points.size() - 2).subtract(points.getLast());
        }
        return null;
    }

    private double resolveNodeHalfWidth(RoadNode node, RoadNetwork network) {
        double halfWidth = config.getRoadWidth() / 2.0;
        if (node == null || network == null) {
            return halfWidth;
        }

        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                halfWidth = Math.max(halfWidth, RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0);
            }
        }
        return halfWidth;
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
