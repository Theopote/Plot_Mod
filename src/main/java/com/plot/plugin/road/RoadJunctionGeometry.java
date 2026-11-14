package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * 路口造型相关的纯几何计算（不依赖 Minecraft World）。
 * <p>
 * 生成路径须传入已解析的中心线（{@link com.plot.plugin.road.alignment.RoadPlanGeometry}），
 * 不得在本类内读取 {@link RoadEdge#getCenterlinePoints()}。
 */
public final class RoadJunctionGeometry {
    public static final double DEFAULT_JUNCTION_RADIUS = 3.0;
    private static final double VERTEX_DEDUP_EPSILON = 0.05;

    private RoadJunctionGeometry() {
    }

    /**
     * 从各相连边在路口处的左右边界角点构建闭合多边形（按极角排序）
     */
    public static List<Vec2d> collectPolygonVertices(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        if (nodeId == null || edges == null || halfWidthResolver == null || centerlineResolver == null || edges.isEmpty()) {
            return List.of();
        }

        Vec2d center = resolveNodeCenter(nodeId, edges, centerlineResolver);
        double effectiveRadius = resolveEffectiveJunctionRadius(edges, halfWidthResolver, junctionRadius);
        List<Vec2d> corners = new ArrayList<>();

        for (RoadEdge edge : edges) {
            collectApproachCorners(
                nodeId, center, edge, halfWidthResolver.applyAsDouble(edge),
                effectiveRadius, centerlineResolver, corners);
        }

        corners = dedupeNearbyVertices(corners);
        if (corners.size() < 3) {
            return corners;
        }

        List<Vec2d> sorted = sortVerticesByPolarAngle(corners, center);
        if (!isSimplePolygon(sorted)) {
            List<Vec2d> hull = convexHull(corners);
            sorted = hull.size() >= 3 ? sortVerticesByPolarAngle(hull, center) : sorted;
        }
        if (!RoadGeometryUtils.pointInPolygon(center, sorted)) {
            List<Vec2d> withCenter = new ArrayList<>(corners);
            withCenter.add(center);
            List<Vec2d> hull = convexHull(withCenter);
            if (hull.size() >= 3) {
                sorted = sortVerticesByPolarAngle(hull, center);
            }
        }
        return sorted;
    }

    /**
     * 构建路口铺装多边形：先收集各进口真实半宽边界角点，再按路缘圆角倒角。
     */
    public static List<Vec2d> buildJunctionFillPolygon(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius,
            double cornerRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        return buildJunctionGeometry(
            nodeId, edges, halfWidthResolver, junctionRadius, cornerRadius, centerlineResolver)
            .fillPolygon();
    }

    /**
     * 从相邻进口道路硬路缘构造路口几何（含真正的街角路缘圆角）。
     * 不等宽进口通过 per-edge halfWidth 体现；圆角向原铺装外扩展，而非裁剪凸顶点。
     */
    public static JunctionGeometryResult buildJunctionGeometry(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius,
            double cornerRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        return buildJunctionGeometry(
            nodeId, edges, halfWidthResolver, junctionRadius, cornerRadius, centerlineResolver,
            null, null, 1.0);
    }

    /**
     * @param network 非空时可按桩号解析变宽横断面，生成变宽进口条带
     * @param config  与 network 配套的道路配置
     */
    public static JunctionGeometryResult buildJunctionGeometry(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius,
            double cornerRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            RoadNetwork network,
            RoadSystemConfig config) {
        return buildJunctionGeometry(
            nodeId, edges, halfWidthResolver, junctionRadius, cornerRadius, centerlineResolver,
            network, config, 1.0);
    }

    /**
     * @param canvasUnitsPerBlock 方块半宽→画布半宽的尺度；变宽断面先按方块求出再乘此系数，
     *     避免与已缩放的 {@code halfWidthResolver} 混用单位。
     */
    public static JunctionGeometryResult buildJunctionGeometry(
            String nodeId,
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double junctionRadius,
            double cornerRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            RoadNetwork network,
            RoadSystemConfig config,
            double canvasUnitsPerBlock) {
        if (nodeId == null || edges == null || halfWidthResolver == null || centerlineResolver == null) {
            return JunctionGeometryResult.empty();
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d center = resolveNodeCenter(nodeId, edges, centerlineResolver);
        double effectivePlaza = resolveEffectiveJunctionRadius(edges, halfWidthResolver, junctionRadius);
        // 同一循环同时产出 approach / band，避免跳过条件不同导致按索引错位。
        List<JunctionCurbFilletBuilder.Approach> approaches = new ArrayList<>();
        List<List<Vec2d>> approachBands = new ArrayList<>();
        double maxHalf = 0.0;
        List<RoadEdge> bandEdges = new ArrayList<>();
        List<Double> bandHalfWidths = new ArrayList<>();
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> centerline = centerlineResolver.apply(edge);
            Vec2d direction = computeApproachDirection(centerline, edge, nodeId);
            if (direction.lengthSquared() < 1e-12) {
                continue;
            }
            double halfWidth = halfWidthAtJunction(
                network, config, edge, nodeId, halfWidthResolver, scale);
            if (halfWidth <= 1e-9) {
                continue;
            }
            maxHalf = Math.max(maxHalf, halfWidth);
            approaches.add(new JunctionCurbFilletBuilder.Approach(direction, halfWidth, edge.getId()));
            bandEdges.add(edge);
            bandHalfWidths.add(halfWidth);
        }
        // 真实中心线局部条带（弯曲/变宽进口）；直线恒宽时退化为矩形 stub。
        // 臂长由广场半径驱动，避免 maxHalf*3 下限把 plazaRadius 拉平。
        double bandLength = Math.max(effectivePlaza, maxHalf + 1.0);
        double back = maxHalf + 1.0;
        for (int i = 0; i < bandEdges.size(); i++) {
            RoadEdge edge = bandEdges.get(i);
            double halfWidth = bandHalfWidths.get(i);
            List<Vec2d> outward = extractApproachCenterline(
                centerlineResolver.apply(edge), edge, nodeId, bandLength);
            if (outward.size() < 2) {
                // 与 approaches 对齐：条带失败时塞空列表，禁止后续 get(i) 错配到邻臂。
                approachBands.add(List.of());
                continue;
            }
            List<Vec2d> fromCenter = new ArrayList<>(outward.size());
            fromCenter.add(center);
            for (int j = 1; j < outward.size(); j++) {
                fromCenter.add(outward.get(j));
            }
            DoubleUnaryOperator widthAlong = halfWidthAlongApproach(
                network, config, edge, nodeId, halfWidth, scale);
            List<Vec2d> band = JunctionPavementBand.buildStubPolygon(fromCenter, widthAlong, back);
            approachBands.add(band.size() >= 3 ? band : List.of());
        }
        JunctionRegionGeometry region = JunctionRegionGeometry.empty();
        int validBands = 0;
        for (List<Vec2d> band : approachBands) {
            if (band != null && band.size() >= 3) {
                validBands++;
            }
        }
        if (validBands >= 3 && approaches.size() >= 3) {
            region = JunctionRegionBuilder.buildFromApproachBands(
                center, approaches, approachBands, cornerRadius, effectivePlaza);
        }
        if (!region.hasPavement()) {
            region = JunctionRegionBuilder.buildFromApproaches(
                center, approaches, cornerRadius, effectivePlaza);
        }
        if (region.hasPavement()) {
            return bindFilletOwnersFromApproaches(region.toJunctionGeometryResult(), approaches, center);
        }
        // 立交下层两臂（过滤上层后仅剩连续道路）不得回退旧算法生成虚构平交广场
        if (!isAtGradePlazaCandidate(approaches)) {
            return JunctionGeometryResult.empty();
        }
        // 旧算法可能写入错误 owner；强制按切点重绑，避免 loft 采错剖面。
        return bindFilletOwnersFromApproaches(
            JunctionCurbFilletBuilder.build(center, approaches, effectivePlaza, cornerRadius),
            approaches,
            center,
            true);
    }

    /**
     * 区域式圆角未携带边 id 时，按切点到各进口路缘线的距离绑定归属
     * （|侧向距−半宽| 最小），避免「最近中心线」在不等宽时选错。
     */
    static JunctionGeometryResult bindFilletOwnersFromApproaches(
            JunctionGeometryResult geometry,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            Vec2d junctionCenter) {
        return bindFilletOwnersFromApproaches(geometry, approaches, junctionCenter, false);
    }

    /**
     * @param forceRebind true 时即使已有 owner 也按切点重算（旧算法纠错）。
     */
    static JunctionGeometryResult bindFilletOwnersFromApproaches(
            JunctionGeometryResult geometry,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            Vec2d junctionCenter,
            boolean forceRebind) {
        if (geometry == null || geometry.fillets().isEmpty() || approaches == null || approaches.isEmpty()) {
            return geometry != null ? geometry : JunctionGeometryResult.empty();
        }
        if (!forceRebind) {
            boolean anyMissing = false;
            for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
                if (fillet != null && !fillet.hasOwners()) {
                    anyMissing = true;
                    break;
                }
            }
            if (!anyMissing) {
                return geometry;
            }
        }
        List<JunctionGeometryResult.FilletCorner> rebound = new ArrayList<>(geometry.fillets().size());
        for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
            if (fillet == null) {
                continue;
            }
            if (!forceRebind && fillet.hasOwners()) {
                rebound.add(fillet);
                continue;
            }
            String startOwner = resolveCurbOwnerEdgeId(fillet.tangentStart(), approaches, junctionCenter);
            String endOwner = resolveCurbOwnerEdgeId(fillet.tangentEnd(), approaches, junctionCenter);
            rebound.add(fillet.withOwners(startOwner, endOwner));
        }
        return new JunctionGeometryResult(
            geometry.fillPolygon(),
            geometry.sharpPolygon(),
            geometry.approachBoundaryPoints(),
            geometry.requestedCornerRadius(),
            geometry.effectiveCornerRadius(),
            rebound,
            geometry.overlapRegion(),
            geometry.filletAdditions());
    }

    /**
     * 切点归属：到「中心线+半宽路缘」侧向误差最小的进口边，而非到中心线最近。
     */
    public static String resolveCurbOwnerEdgeId(
            Vec2d tangent,
            List<JunctionCurbFilletBuilder.Approach> approaches,
            Vec2d junctionCenter) {
        if (tangent == null || approaches == null || approaches.isEmpty()) {
            return null;
        }
        String bestId = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (JunctionCurbFilletBuilder.Approach approach : approaches) {
            if (approach == null || approach.edgeId() == null || approach.halfWidth() <= 1e-9) {
                continue;
            }
            Vec2d outward = approach.outward();
            if (outward == null || outward.lengthSquared() < 1e-12) {
                continue;
            }
            Vec2d origin = junctionCenter != null ? junctionCenter : new Vec2d(0, 0);
            Vec2d relative = tangent.subtract(origin);
            double along = relative.dot(outward);
            // 切点应在进口嘴侧附近；明显反向的进口跳过
            if (along < -approach.halfWidth()) {
                continue;
            }
            double lateral = Math.abs(relative.dot(approach.leftNormal()));
            double score = Math.abs(lateral - approach.halfWidth());
            if (score < bestScore) {
                bestScore = score;
                bestId = approach.edgeId();
            }
        }
        return bestId;
    }

    /** 路口处半宽：优先节点桩号上的变宽断面（方块半宽 × canvasUnitsPerBlock）。 */
    public static double halfWidthAtJunction(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadEdge edge,
            String nodeId,
            ToDoubleFunction<RoadEdge> fallback) {
        return halfWidthAtJunction(network, config, edge, nodeId, fallback, 1.0);
    }

    public static double halfWidthAtJunction(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadEdge edge,
            String nodeId,
            ToDoubleFunction<RoadEdge> fallback,
            double canvasUnitsPerBlock) {
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        if (network != null && config != null && edge != null && nodeId != null) {
            Road road = edge.getRoadId() != null ? network.getRoad(edge.getRoadId()) : null;
            if (road != null && VariableCrossSectionResolver.hasVariableSections(road)) {
                OptionalDouble station = RoadStationing.stationAtNode(network, road, edge, nodeId);
                if (station.isPresent()) {
                    int blocks = VariableCrossSectionResolver.resolve(
                        network, road, station.getAsDouble(), config).totalHardSurfaceWidthBlocks();
                    return RoadDimensionUtils.halfExtentFromCenter(blocks) * scale;
                }
            }
        }
        return fallback != null ? fallback.applyAsDouble(edge) : 0.0;
    }

    /**
     * 自路口沿进口向外 {@code along} 处的半宽；无变宽数据时恒为 junctionHalf。
     * {@code along} 与中心线同为画布单位；变宽断面方块半宽再乘 {@code canvasUnitsPerBlock}。
     */
    public static DoubleUnaryOperator halfWidthAlongApproach(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadEdge edge,
            String nodeId,
            double junctionHalf) {
        return halfWidthAlongApproach(network, config, edge, nodeId, junctionHalf, 1.0);
    }

    public static DoubleUnaryOperator halfWidthAlongApproach(
            RoadNetwork network,
            RoadSystemConfig config,
            RoadEdge edge,
            String nodeId,
            double junctionHalf,
            double canvasUnitsPerBlock) {
        if (network == null || config == null || edge == null || nodeId == null) {
            return along -> junctionHalf;
        }
        Road road = edge.getRoadId() != null ? network.getRoad(edge.getRoadId()) : null;
        if (road == null || !VariableCrossSectionResolver.hasVariableSections(road)) {
            return along -> junctionHalf;
        }
        OptionalDouble nodeStation = RoadStationing.stationAtNode(network, road, edge, nodeId);
        if (nodeStation.isEmpty()) {
            return along -> junctionHalf;
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        // 按道路链定向判断：路口在 entry 则向外桩号递增；在 exit 则递减。
        // 不可用 Edge 起终点——forward==false 时存储方向与链方向相反。
        boolean outwardIncreases = outwardIncreasesStation(network, road, edge, nodeId);
        // stationAtNode 为 canonical；条带 along 为实例折线几何距离，先换算再回 canonical 查断面
        double baseCanonical = nodeStation.getAsDouble();
        double baseInstance = RoadStationing.toInstanceChainage(network, road, baseCanonical);
        return along -> {
            double instanceStation = outwardIncreases
                ? baseInstance + along
                : baseInstance - along;
            double station = RoadStationing.toCanonicalChainage(
                network, road, Math.max(0.0, instanceStation));
            int blocks = VariableCrossSectionResolver.resolve(
                network, road, Math.max(0.0, station), config).totalHardSurfaceWidthBlocks();
            return RoadDimensionUtils.halfExtentFromCenter(blocks) * scale;
        };
    }

    /**
     * 自路口节点沿进口向外时，道路 canonical/instance 桩号是否递增。
     */
    public static boolean outwardIncreasesStation(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            String nodeId) {
        if (network == null || road == null || edge == null || nodeId == null) {
            return true;
        }
        return RoadStationing.orientedSegment(network, road, edge.getId())
            .map(oriented -> nodeId.equals(oriented.entryNodeId()))
            .orElseGet(() -> nodeId.equals(edge.getStartNodeId()));
    }

    /**
     * 真正需要平交广场的进口拓扑：至少三个方向，或四条已配对进口。
     * 两臂近似共线/对穿视为连续道路，不生成额外广场。
     */
    static boolean isAtGradePlazaCandidate(List<JunctionCurbFilletBuilder.Approach> approaches) {
        if (approaches == null || approaches.size() < 3) {
            return false;
        }
        if (approaches.size() >= 4) {
            return true;
        }
        // 三臂：T 字等合法平交
        return approaches.size() == 3;
    }

    static List<Vec2d> applyCornerFillets(List<Vec2d> polygon, double radius, int arcSegments) {
        return filletPolygon(polygon, radius, arcSegments).polygon();
    }

    private record FilletBuild(
            List<Vec2d> polygon,
            List<JunctionGeometryResult.FilletCorner> corners,
            double effectiveRadius) {
    }

    private static FilletBuild filletPolygon(List<Vec2d> polygon, double radius, int arcSegments) {
        if (polygon == null || polygon.size() < 3 || radius <= 1e-6) {
            List<Vec2d> copy = polygon == null ? List.of() : new ArrayList<>(polygon);
            return new FilletBuild(copy, List.of(), 0.0);
        }

        int count = polygon.size();
        List<Vec2d> result = new ArrayList<>();
        List<JunctionGeometryResult.FilletCorner> corners = new ArrayList<>();
        int segments = Math.max(2, arcSegments);
        double minEffective = Double.POSITIVE_INFINITY;

        for (int i = 0; i < count; i++) {
            Vec2d prev = polygon.get((i - 1 + count) % count);
            Vec2d curr = polygon.get(i);
            Vec2d next = polygon.get((i + 1) % count);

            Vec2d toPrev = prev.subtract(curr);
            Vec2d toNext = next.subtract(curr);
            double lenPrev = toPrev.length();
            double lenNext = toNext.length();
            if (lenPrev < 1e-6 || lenNext < 1e-6) {
                result.add(curr);
                continue;
            }

            Vec2d dirPrev = toPrev.multiply(1.0 / lenPrev);
            Vec2d dirNext = toNext.multiply(1.0 / lenNext);
            double cosAngle = clamp(dirPrev.dot(dirNext), -1.0, 1.0);
            double angle = Math.acos(cosAngle);
            if (angle < 0.12 || angle > Math.PI - 0.12) {
                result.add(curr);
                continue;
            }

            // CCW 凸包：dirPrev×dirNext < 0 表示外凸转角（可倒圆角）。
            double cross = dirPrev.x * dirNext.y - dirPrev.y * dirNext.x;
            if (cross >= 0) {
                result.add(curr);
                continue;
            }

            double tanHalf = Math.tan(angle / 2.0);
            if (tanHalf < 1e-6) {
                result.add(curr);
                continue;
            }

            // t = R / tan(θ/2)；进口可用边长不足时自动减小有效半径。
            double tangentDist = radius / tanHalf;
            tangentDist = Math.min(tangentDist, lenPrev * 0.49);
            tangentDist = Math.min(tangentDist, lenNext * 0.49);
            double effectiveRadius = tangentDist * tanHalf;
            if (effectiveRadius < 0.1) {
                result.add(curr);
                continue;
            }

            Vec2d tangentStart = curr.add(dirPrev.multiply(tangentDist));
            Vec2d tangentEnd = curr.add(dirNext.multiply(tangentDist));
            // dirPrev+dirNext 指向角平分线内侧；勿取反，否则圆心落到多边形外侧。
            Vec2d inward = dirPrev.add(dirNext);
            if (inward.lengthSquared() < 1e-12) {
                result.add(curr);
                continue;
            }
            inward = inward.normalize();
            double centerDistance = effectiveRadius / Math.sin(angle / 2.0);
            Vec2d center = curr.add(inward.multiply(centerDistance));
            if (Math.abs(center.distance(tangentStart) - effectiveRadius) > 1e-3
                    || Math.abs(center.distance(tangentEnd) - effectiveRadius) > 1e-3) {
                result.add(curr);
                continue;
            }

            double sweep = FilletArcSweep.resolveShort(center, tangentStart, tangentEnd);
            result.add(tangentStart);
            appendArcPoints(result, center, tangentStart, tangentEnd, effectiveRadius, segments, sweep);
            result.add(tangentEnd);
            corners.add(new JunctionGeometryResult.FilletCorner(
                center, tangentStart, tangentEnd, effectiveRadius, angle, sweep));
            minEffective = Math.min(minEffective, effectiveRadius);
        }
        if (result.size() >= 3 && !isSimplePolygon(result)) {
            return new FilletBuild(new ArrayList<>(polygon), List.of(), 0.0);
        }
        double reported = corners.isEmpty() ? 0.0 : minEffective;
        return new FilletBuild(result, corners, reported);
    }

    /** 沿切点间短弧采样中间点（不含起终，由调用方写入）。 */
    private static void appendArcPoints(
            List<Vec2d> out,
            Vec2d center,
            Vec2d start,
            Vec2d end,
            double radius,
            int segments,
            double sweepRadians) {
        FilletArcSweep.ListSamples samples =
            FilletArcSweep.sample(center, start, end, radius, sweepRadians, segments);
        List<Vec2d> points = samples.points();
        for (int i = 1; i < points.size() - 1; i++) {
            out.add(points.get(i));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static void collectApproachCorners(
            String nodeId,
            Vec2d center,
            RoadEdge edge,
            double halfWidth,
            double junctionRadius,
            Function<RoadEdge, List<Vec2d>> centerlineResolver,
            List<Vec2d> out) {
        if (edge == null || center == null || out == null || halfWidth <= 0 || centerlineResolver == null) {
            return;
        }

        List<Vec2d> centerline = centerlineResolver.apply(edge);
        Vec2d direction = computeApproachDirection(centerline, edge, nodeId);
        if (direction.lengthSquared() < 1e-12) {
            return;
        }

        Vec2d unit = direction.normalize();
        Vec2d leftNormal = unit.perpendicular();
        Vec2d mouth = center.add(unit.multiply(junctionRadius));
        out.add(mouth.add(leftNormal.multiply(halfWidth)));
        out.add(mouth.subtract(leftNormal.multiply(halfWidth)));
    }

    public static Vec2d computeApproachDirection(List<Vec2d> centerline, RoadEdge edge, String nodeId) {
        if (centerline == null || centerline.size() < 2 || edge == null || nodeId == null) {
            return new Vec2d(0, 0);
        }
        if (edge.getStartNodeId().equals(nodeId)) {
            return centerline.get(1).subtract(centerline.getFirst());
        }
        if (edge.getEndNodeId().equals(nodeId)) {
            return centerline.get(centerline.size() - 2).subtract(centerline.getLast());
        }
        return new Vec2d(0, 0);
    }

    /**
     * 从节点出发沿中心线向外截取指定长度（用于路口标线延续）。
     */
    public static List<Vec2d> extractApproachCenterline(
            List<Vec2d> centerline,
            RoadEdge edge,
            String nodeId,
            double maxLength) {
        if (edge == null || nodeId == null || maxLength <= 0 || centerline == null || centerline.size() < 2) {
            return List.of();
        }

        List<Vec2d> ordered = new ArrayList<>();
        if (edge.getStartNodeId().equals(nodeId)) {
            ordered.addAll(centerline);
        } else if (edge.getEndNodeId().equals(nodeId)) {
            for (int i = centerline.size() - 1; i >= 0; i--) {
                ordered.add(centerline.get(i));
            }
        } else {
            return List.of();
        }
        return truncatePolylineFromStart(ordered, maxLength);
    }

    static List<Vec2d> truncatePolylineFromStart(List<Vec2d> ordered, double maxLength) {
        if (ordered == null || ordered.isEmpty()) {
            return List.of();
        }
        List<Vec2d> result = new ArrayList<>();
        result.add(ordered.getFirst());
        double accumulated = 0.0;
        for (int i = 1; i < ordered.size(); i++) {
            Vec2d prev = ordered.get(i - 1);
            Vec2d curr = ordered.get(i);
            double segmentLength = prev.distance(curr);
            if (segmentLength < 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= maxLength) {
                double t = (maxLength - accumulated) / segmentLength;
                result.add(prev.lerp(curr, t));
                return result;
            }
            accumulated += segmentLength;
            result.add(curr);
        }
        return result;
    }

    static Vec2d resolveNodeCenter(
            String nodeId,
            List<RoadEdge> edges,
            Function<RoadEdge, List<Vec2d>> centerlineResolver) {
        if (centerlineResolver == null) {
            return new Vec2d(0, 0);
        }
        for (RoadEdge edge : edges) {
            List<Vec2d> points = centerlineResolver.apply(edge);
            if (points == null || points.isEmpty()) {
                continue;
            }
            if (edge.getStartNodeId().equals(nodeId)) {
                return points.getFirst();
            }
            if (edge.getEndNodeId().equals(nodeId)) {
                return points.getLast();
            }
        }
        return new Vec2d(0, 0);
    }

    public static double resolveEffectiveJunctionRadius(
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver,
            double baseRadius) {
        double maxHalfWidth = 0;
        for (RoadEdge edge : edges) {
            maxHalfWidth = Math.max(maxHalfWidth, halfWidthResolver.applyAsDouble(edge));
        }
        return Math.max(baseRadius, maxHalfWidth + 1.0);
    }

    /**
     * 按进口最大半宽拟合广场基准半径（半宽 + 1），再夹到节点允许范围。
     */
    public static double fitPlazaRadiusToApproaches(
            List<RoadEdge> edges,
            ToDoubleFunction<RoadEdge> halfWidthResolver) {
        double maxHalfWidth = 0;
        if (edges != null && halfWidthResolver != null) {
            for (RoadEdge edge : edges) {
                maxHalfWidth = Math.max(maxHalfWidth, halfWidthResolver.applyAsDouble(edge));
            }
        }
        double fitted = maxHalfWidth + 1.0;
        return Math.clamp(fitted, RoadNode.MIN_PLAZA_RADIUS, RoadNode.MAX_PLAZA_RADIUS);
    }

    static List<Vec2d> sortVerticesByPolarAngle(List<Vec2d> vertices, Vec2d center) {
        if (vertices == null || vertices.isEmpty()) {
            return List.of();
        }
        List<Vec2d> sorted = new ArrayList<>(vertices);
        sorted.sort(Comparator.comparingDouble(v -> Math.atan2(v.y - center.y, v.x - center.x)));
        return sorted;
    }

    static List<Vec2d> dedupeNearbyVertices(List<Vec2d> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return List.of();
        }
        List<Vec2d> deduped = new ArrayList<>();
        for (Vec2d vertex : vertices) {
            boolean duplicate = false;
            for (Vec2d existing : deduped) {
                if (existing.distanceSquared(vertex) <= VERTEX_DEDUP_EPSILON * VERTEX_DEDUP_EPSILON) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(vertex);
            }
        }
        return deduped;
    }

    public static boolean isSimplePolygon(List<Vec2d> vertices) {
        int count = vertices.size();
        if (count < 3) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            Vec2d a1 = vertices.get(i);
            Vec2d a2 = vertices.get((i + 1) % count);
            for (int j = i + 1; j < count; j++) {
                if (j == i || j == (i + 1) % count || (i + 1) % count == j || (j + 1) % count == i) {
                    continue;
                }
                Vec2d b1 = vertices.get(j);
                Vec2d b2 = vertices.get((j + 1) % count);
                if (segmentsIntersectProperly(a1, a2, b1, b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    static List<Vec2d> convexHull(List<Vec2d> points) {
        if (points == null || points.size() <= 3) {
            return points == null ? List.of() : new ArrayList<>(points);
        }

        List<Vec2d> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble((Vec2d p) -> p.x).thenComparingDouble(p -> p.y));

        List<Vec2d> lower = new ArrayList<>();
        for (Vec2d point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.getLast(), point) <= 0) {
                lower.removeLast();
            }
            lower.add(point);
        }

        List<Vec2d> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Vec2d point = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.getLast(), point) <= 0) {
                upper.removeLast();
            }
            upper.add(point);
        }

        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return lower;
    }

    private static double cross(Vec2d origin, Vec2d a, Vec2d b) {
        return (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x);
    }

    private static boolean segmentsIntersectProperly(Vec2d a1, Vec2d a2, Vec2d b1, Vec2d b2) {
        double d1 = cross(b1, b2, a1);
        double d2 = cross(b1, b2, a2);
        double d3 = cross(a1, a2, b1);
        double d4 = cross(a1, a2, b2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
            && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    public static List<Vec2d> extractNearNodeSegment(
            List<Vec2d> centerline,
            RoadEdge edge,
            String nodeId,
            double maxDistance) {
        if (edge == null || nodeId == null || centerline == null || centerline.size() < 2) {
            return List.of();
        }

        boolean atStart = edge.getStartNodeId().equals(nodeId);
        List<Vec2d> segment = new ArrayList<>();
        if (atStart) {
            segment.add(centerline.getFirst());
            segment.add(centerline.get(Math.min(1, centerline.size() - 1)));
            if (centerline.size() > 2) {
                segment.add(RoadGeometryUtils.pointAlongPolylineFrom(centerline.getFirst(), centerline, maxDistance));
            }
        } else {
            List<Vec2d> reversed = new ArrayList<>(centerline);
            Collections.reverse(reversed);
            segment.add(RoadGeometryUtils.pointAlongPolylineFrom(centerline.getLast(), reversed, maxDistance));
            segment.add(centerline.get(Math.max(0, centerline.size() - 2)));
            segment.add(centerline.getLast());
        }
        return segment;
    }

    /**
     * 在路口中心附近生成简化圆形包络的画布坐标采样点
     */
    public static List<Vec2d> collectSimpleEnvelopePoints(Vec2d center, int radius) {
        List<Vec2d> points = new ArrayList<>();
        if (center == null || radius <= 0) {
            return points;
        }

        int rSquared = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= rSquared) {
                    points.add(new Vec2d(center.x + dx, center.y + dz));
                }
            }
        }
        return points;
    }
}
