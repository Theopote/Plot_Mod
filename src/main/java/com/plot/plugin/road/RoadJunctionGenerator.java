package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import com.plot.plugin.road.pipeline.corridor.JunctionCrossSectionFill;
import com.plot.plugin.road.pipeline.corridor.JunctionFilletElevationLoft;
import com.plot.plugin.road.pipeline.corridor.JunctionExpectedProtectedCollector;
import com.plot.plugin.road.pipeline.corridor.JunctionSurfaceExpectation;
import com.plot.plugin.road.pipeline.corridor.JunctionSurfaceMask;
import com.plot.plugin.road.pipeline.corridor.JunctionTerrainClearance;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.TunnelJunctionSeedResolver;
import com.plot.plugin.road.pipeline.corridor.TunnelPortalSeed;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路口路面生成
 */
public class RoadJunctionGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadJunctionGenerator");

    /**
     * 路口实体蓝图（平面坐标 + 标高 + 层），落地前不含 BlockPos。
     */
    public static class JunctionBlocks {
        private final RoadSolidModel solids = new RoadSolidModel();
        private JunctionConstructionReport report = JunctionConstructionReport.empty("");
        private JunctionValidationManifest validationManifest = JunctionValidationManifest.empty("");

        public RoadSolidModel getSolids() {
            return solids;
        }

        public JunctionConstructionReport report() {
            return report;
        }

        public JunctionValidationManifest validationManifest() {
            return validationManifest;
        }

        void setReport(JunctionConstructionReport report) {
            this.report = report != null ? report : JunctionConstructionReport.empty("");
        }

        void setValidationManifest(JunctionValidationManifest validationManifest) {
            this.validationManifest = validationManifest != null
                ? validationManifest
                : JunctionValidationManifest.empty("");
        }

        public boolean isEmpty() {
            return solids.isEmpty();
        }
    }

    private final RoadGenerator generator;
    private final RoadJunctionMarkingGenerator markingGenerator;

    public RoadJunctionGenerator(RoadGenerator generator) {
        this.generator = generator;
        this.markingGenerator = new RoadJunctionMarkingGenerator(generator);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, World world) {
        if (node == null || network == null || generator == null || world == null) {
            return new JunctionBlocks();
        }
        return generateJunction(node, network, generator.createTerrainSampler(world), null);
    }

    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, TerrainSampler terrain) {
        return generateJunction(node, network, terrain, null);
    }

    /**
     * @param networkNodeElevations 路网统一节点标高；非 null 时路口面直接使用该高度
     */
    public JunctionBlocks generateJunction(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations) {
        return generateJunction(node, network, terrain, networkNodeElevations, null);
    }

    /**
     * @param edgeResults connected edge results for leftover clearance tops / protected upper deck;
     *     plaza surface itself stays on consensus {@code junctionY} (endpoint snap + corridor trim
     *     keep approach ROAD meeting that plane — see {@code JunctionApproachElevationTest})
     */
    public JunctionBlocks generateJunction(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain,
            Map<String, Integer> networkNodeElevations,
            Map<String, RoadGenerationResult> edgeResults) {
        JunctionBlocks blocks = new JunctionBlocks();
        if (generator != null) {
            blocks.getSolids().setCoordinateService(generator.coordinateTransformer());
        }
        if (node == null || network == null || generator == null || terrain == null) {
            return blocks;
        }

        int degree = node.getDegree();
        if (degree <= 2) {
            return blocks;
        }

        // 自动上跨判定结果钉到节点，材质策略/走廊过滤与路口填充共用同一上层道路 ID。
        pinResolvedElevatedRoadId(node, network, terrain);

        int junctionY;
        if (networkNodeElevations != null && networkNodeElevations.containsKey(node.getId())) {
            junctionY = networkNodeElevations.get(node.getId());
        } else {
            junctionY = generator.computeJunctionTargetHeight(node, network, terrain);
        }
        List<RoadEdge> connectedEdges = collectUnderpassJunctionEdges(node, network, terrain);
        if (connectedEdges.isEmpty()) {
            return blocks;
        }

        // 立交过滤上层后可能只剩两臂：区域几何会拒绝虚构四臂广场并回退简单包络，
        // 仍需走清障/保护清单，不能整段跳过路口生成。
        if (degree >= 5) {
            LOGGER.debug("复杂路口（{}条道路汇聚），使用多边形填充（必要时回退凸包）", degree);
        }

        List<Vec2d> polygon = generatePolygonJunction(
            blocks, node, network, connectedEdges, junctionY, terrain, edgeResults,
            networkNodeElevations);
        markingGenerator.generateStopLines(blocks, node, network, connectedEdges, junctionY);
        markingGenerator.generateMarkings(blocks, node, network, connectedEdges, polygon, junctionY);
        Integer elevatedDeckY = resolveElevatedDeckY(node, junctionY, network, generator.getConfig());
        if (elevatedDeckY != null) {
            List<RoadEdge> elevatedEdges = collectElevatedJunctionEdges(node, network, terrain);
            markingGenerator.generateElevatedRailwayContinuation(
                blocks, node, network, elevatedEdges, elevatedDeckY);
        }
        return blocks;
    }

    private List<RoadEdge> collectElevatedJunctionEdges(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        List<RoadEdge> elevated = new ArrayList<>();
        if (node == null || network == null) {
            return elevated;
        }
        if (node.isStackGradeSeparated()) {
            for (String edgeId : node.getConnectedEdgeIds()) {
                if (!node.isStackUpperEdge(edgeId)) {
                    continue;
                }
                RoadEdge edge = network.getEdge(edgeId);
                if (edge != null) {
                    elevated.add(edge);
                }
            }
            return elevated;
        }
        String elevatedRoadId = node.isGradeSeparated()
            ? generator.resolveElevatedRoadId(node, network, terrain)
            : null;
        if (elevatedRoadId == null) {
            return elevated;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && elevatedRoadId.equals(edge.getRoadId())) {
                elevated.add(edge);
            }
        }
        return elevated;
    }

    private List<Vec2d> generatePolygonJunction(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        Vec2d center = node.getPosition();
        // 中心线为画布坐标；半宽/半径是方块数，必须乘 unitsPerBlock 才与走廊断面同尺度。
        // 否则投影下倒圆角缩在交点旁，Minecraft 里看起来像「没有生成圆角」。
        double unitsPerBlock = estimateUnitsPerBlock(network, edges);
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        java.util.function.ToDoubleFunction<RoadEdge> canvasHalfWidth =
            edge -> hardSurfaceHalfExtent(network, edge) * scale;
        double plazaCanvas = node.getEffectivePlazaRadius(
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS) * scale;
        double cornerCanvas = resolveCornerRadius(node, network, generator.getConfig()) * scale;
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            edges, canvasHalfWidth, plazaCanvas);
        JunctionGeometryResult geometry = RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            edges,
            canvasHalfWidth,
            junctionRadius,
            cornerCanvas,
            RoadJunctionCenterlineResolver.forNetwork(network),
            // 变宽断面按方块求出后再 × scale，与 canvasHalfWidth 同单位
            network,
            generator.getConfig(),
            scale);
        List<Vec2d> polygon = geometry.fillPolygon();

        if (polygon.size() < 3) {
            // 立交下层两臂：保护区与铺装区分离——扩大圆仅用于净空/保护，不铺圆形广场路面
            if (node.isGradeSeparated() || node.isStackGradeSeparated()) {
                generateUnderpassClearanceWithoutPlaza(
                    blocks, node, network, center, edges, junctionY, terrain, edgeResults,
                    networkNodeElevations);
                return List.of();
            }
            generateSimpleEnvelope(
                blocks, node, network, center, edges, junctionY, terrain, edgeResults, networkNodeElevations);
            return List.of();
        }

        // 清障外形用完整倒圆角足迹；表面铺装责任区优先为重叠核∪圆角补铺，
        // 进口路臂交给各道路走廊保留原材质。
        fillPolygon(
            blocks, node, network, edges, polygon, geometry.sharpPolygon(), geometry,
            center, junctionY, terrain, edgeResults, networkNodeElevations);
        return polygon;
    }

    private void pinResolvedElevatedRoadId(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        if (node == null || !node.isGradeSeparated()) {
            return;
        }
        if (node.getElevatedRoadId() != null && !node.getElevatedRoadId().isBlank()) {
            return;
        }
        String resolved = generator.resolveElevatedRoadId(node, network, terrain);
        if (resolved != null && !resolved.isBlank()) {
            node.setElevatedRoadId(resolved);
        }
    }

    private void generateSimpleEnvelope(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        // 边缘情况保护：如果边列表为空，至少生成节点位置的方块
        if (edges.isEmpty()) {
            LOGGER.warn("路口节点边列表为空，生成最小覆盖（中心点）");
            fillJunctionCells(
                blocks, List.of(center), List.of(center), null, node, network, edges, junctionY, terrain,
                edgeResults, networkNodeElevations, true, List.of());
            return;
        }

        List<Vec2d> circle = protectionCircle(center, edges, network);
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            circle, generator.coordinateTransformer());
        if (cells.isEmpty()) {
            LOGGER.warn("路口世界格扫描为空，回退到中心点");
            fillJunctionCells(
                blocks, List.of(center), List.of(center), circle, node, network, edges, junctionY, terrain,
                edgeResults, networkNodeElevations, true, List.of());
            return;
        }
        fillJunctionCells(
            blocks, cells, cells, circle, node, network, edges, junctionY, terrain, edgeResults,
            networkNodeElevations, true, List.of());
    }

    /**
     * 立交下层连续道路：ProtectionFootprint 用扩大圆做净空与上层保护；
     * SurfaceFootprint 为空——路面留给走廊自身，禁止圆形广场误铺。
     */
    private void generateUnderpassClearanceWithoutPlaza(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            Vec2d center,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        if (center == null) {
            return;
        }
        List<Vec2d> protection = protectionCircle(center, edges, network);
        if (protection.size() < 3) {
            protection = approximateCirclePolygon(center, 6);
        }
        List<Vec2d> clearanceCells = RoadGeometryUtils.collectWorldCellPlanPoints(
            protection, generator.coordinateTransformer());
        if (clearanceCells.isEmpty()) {
            clearanceCells = List.of(center);
        }
        fillJunctionCells(
            blocks, List.of(), clearanceCells, protection, node, network, edges, junctionY, terrain,
            edgeResults, networkNodeElevations, /*stampSurface*/ false, List.of());
    }

    private List<Vec2d> protectionCircle(Vec2d center, List<RoadEdge> edges, RoadNetwork network) {
        int maxWidth = 5;
        if (edges != null) {
            for (RoadEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                maxWidth = Math.max(
                    maxWidth,
                    RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                        .totalHardSurfaceWidthBlocks());
            }
        }
        return approximateCirclePolygon(center, maxWidth + 2);
    }

    private static List<Vec2d> approximateCirclePolygon(Vec2d center, int radius) {
        if (center == null || radius <= 0) {
            return List.of();
        }
        int segments = Math.max(12, radius * 4);
        List<Vec2d> polygon = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            polygon.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)));
        }
        return polygon;
    }

    private void fillPolygon(
            JunctionBlocks blocks,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            List<Vec2d> filletedPolygon,
            List<Vec2d> sharpPolygon,
            JunctionGeometryResult geometry,
            Vec2d center,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations) {
        // 清障外形：完整倒圆角足迹。表面铺装：区域式时仅重叠核∪圆角补铺。
        List<Vec2d> clearance = filletedPolygon != null && filletedPolygon.size() >= 3
            ? filletedPolygon
            : sharpPolygon;
        if (clearance == null || clearance.size() < 3) {
            clearance = filletedPolygon;
        }
        List<Vec2d> stampFootprint = junctionSurfaceFootprint(geometry, clearance, sharpPolygon);
        List<Vec2d> surfaceCells = new ArrayList<>(RoadGeometryUtils.collectWorldCellPlanPoints(
            stampFootprint,
            generator.coordinateTransformer()));
        // 圆角补铺：additions + fill\sharp + 扇形栅格（投影下薄三角容易漏世界格）
        if (geometry != null && geometry.hasRegionZones()) {
            for (List<Vec2d> addition : geometry.filletAdditions()) {
                if (addition == null || addition.size() < 3) {
                    continue;
                }
                surfaceCells.addAll(RoadGeometryUtils.collectWorldCellPlanPoints(
                    addition, generator.coordinateTransformer()));
            }
            List<Vec2d> sharp = geometry.sharpPolygon();
            List<Vec2d> filleted = geometry.fillPolygon();
            if (filleted != null && filleted.size() >= 3) {
                for (Vec2d cell : RoadGeometryUtils.collectWorldCellPlanPoints(
                        filleted, generator.coordinateTransformer())) {
                    if (cell == null) {
                        continue;
                    }
                    if (sharp == null || sharp.size() < 3
                            || !RoadGeometryUtils.pointInPolygon(cell, sharp)) {
                        surfaceCells.add(cell);
                    }
                }
            }
            surfaceCells.addAll(collectFilletGoreCells(geometry, generator.coordinateTransformer()));
            surfaceCells = dedupePlanCellsByWorldColumn(surfaceCells, geometry);
        } else if (geometry != null && !geometry.fillets().isEmpty()) {
            // 旧路径无 region zones 时仍按街角补铺 gore 栅格，避免只有预览弧、世界无铺装
            surfaceCells.addAll(collectFilletGoreCells(geometry, generator.coordinateTransformer()));
            surfaceCells = dedupePlanCellsByWorldColumn(surfaceCells, geometry);
        }
        // 清障按完整倒圆角外形栅格；表面按核区∪补铺——二者不得绑死
        List<Vec2d> clearanceCells = clearance != null && clearance.size() >= 3
            ? new ArrayList<>(RoadGeometryUtils.collectWorldCellPlanPoints(
                clearance, generator.coordinateTransformer()))
            : List.of();
        if (surfaceCells.isEmpty() && center != null) {
            surfaceCells = List.of(center);
        }
        if (clearanceCells.isEmpty()) {
            clearanceCells = surfaceCells;
        }
        fillJunctionCells(
            blocks, surfaceCells, clearanceCells, clearance, node, network, edges, junctionY, terrain,
            edgeResults, networkNodeElevations, true,
            geometry != null ? geometry.fillets() : List.of());
    }

    /**
     * 区域式：路口表面责任区 = 重叠核（圆角补铺另栅格合并）。
     * 旧路径：整块 fill/sharp 足迹。
     */
    private static List<Vec2d> junctionSurfaceFootprint(
            JunctionGeometryResult geometry,
            List<Vec2d> clearance,
            List<Vec2d> sharpPolygon) {
        if (geometry != null && geometry.hasRegionZones()) {
            return geometry.overlapRegion();
        }
        if (clearance != null && clearance.size() >= 3) {
            return clearance;
        }
        return sharpPolygon != null ? sharpPolygon : List.of();
    }

    /**
     * 按 Minecraft 世界 XZ 列去重；同列多个画布采样取均值作为代表点，
     * 避免投影下重复铺装/多标高写入。
     */
    private List<Vec2d> dedupePlanCellsByWorldColumn(List<Vec2d> cells) {
        return dedupePlanCellsByWorldColumn(cells, null);
    }

    /**
     * 世界列去重。若提供路口几何，同列在均值落到 gore 外时改选仍位于 gore 内的采样点，
     * 避免放样标高误回退到 junctionY。
     */
    private List<Vec2d> dedupePlanCellsByWorldColumn(List<Vec2d> cells, JunctionGeometryResult geometry) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<List<Vec2d>> gorePolygons = geometry != null ? filletGorePolygons(geometry) : List.of();
        java.util.LinkedHashMap<Long, List<Vec2d>> byColumn = new java.util.LinkedHashMap<>();
        for (Vec2d cell : cells) {
            if (cell == null) {
                continue;
            }
            BlockPos world = generator != null
                ? generator.toBlockPos(cell, 0)
                : new BlockPos((int) Math.floor(cell.x), 0, (int) Math.floor(cell.y));
            long key = (((long) world.getX()) << 32) ^ (world.getZ() & 0xffffffffL);
            byColumn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
        }
        List<Vec2d> unique = new ArrayList<>(byColumn.size());
        for (List<Vec2d> group : byColumn.values()) {
            unique.add(pickWorldColumnRepresentative(group, gorePolygons));
        }
        return unique;
    }

    private static List<List<Vec2d>> filletGorePolygons(JunctionGeometryResult geometry) {
        if (geometry == null || geometry.fillets().isEmpty()) {
            return List.of();
        }
        List<List<Vec2d>> polygons = new ArrayList<>();
        for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
            List<Vec2d> gore = filletGorePolygon(fillet);
            if (gore.size() >= 3) {
                polygons.add(gore);
            }
        }
        return polygons;
    }

    private static Vec2d pickWorldColumnRepresentative(List<Vec2d> group, List<List<Vec2d>> gorePolygons) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        double sx = 0.0;
        double sy = 0.0;
        for (Vec2d p : group) {
            sx += p.x;
            sy += p.y;
        }
        Vec2d average = new Vec2d(sx / group.size(), sy / group.size());
        if (gorePolygons == null || gorePolygons.isEmpty()) {
            return average;
        }
        boolean averageInGore = false;
        for (List<Vec2d> gore : gorePolygons) {
            if (RoadGeometryUtils.pointInPolygon(average, gore)) {
                averageInGore = true;
                break;
            }
        }
        if (averageInGore) {
            return average;
        }
        // 均值已离开 gore：优先选仍在 gore 内的采样，保留放样标高资格
        for (Vec2d candidate : group) {
            for (List<Vec2d> gore : gorePolygons) {
                if (RoadGeometryUtils.pointInPolygon(candidate, gore)) {
                    return candidate;
                }
            }
        }
        return average;
    }

    /**
     * 街角补铺 gore（锐角点 → 短弧），不是圆心→弧的草地扇形。
     * 圆心在铺装外侧：扇形会落到弧外草地，锐角凹口仍空，Minecraft 里就像「没倒圆」。
     * 另做径向加密采样，避免投影下薄三角漏世界格。
     */
    static List<Vec2d> collectFilletGoreCells(
            JunctionGeometryResult geometry,
            com.plot.api.world.ICoordinateService transformer) {
        if (geometry == null || geometry.fillets().isEmpty()) {
            return List.of();
        }
        List<Vec2d> cells = new ArrayList<>();
        for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
            List<Vec2d> gore = filletGorePolygon(fillet);
            if (gore.size() < 3) {
                continue;
            }
            cells.addAll(RoadGeometryUtils.collectWorldCellPlanPoints(gore, transformer));
            // 径向加密：锐点到弧上采样点的插值，不依赖多边形栅格命中
            Vec2d sharp = gore.getFirst();
            for (int i = 1; i < gore.size(); i++) {
                Vec2d onArc = gore.get(i);
                if (onArc == null) {
                    continue;
                }
                cells.add(onArc);
                cells.add(sharp.add(onArc.subtract(sharp).multiply(0.35)));
                cells.add(sharp.add(onArc.subtract(sharp).multiply(0.65)));
            }
            cells.add(sharp);
        }
        return cells;
    }

    /**
     * 补铺多边形：锐角交点 + 切点短弧（与 {@link JunctionRegionBuilder} 的 filletAddition 同侧）。
     */
    public static List<Vec2d> filletGorePolygon(JunctionGeometryResult.FilletCorner fillet) {
        if (fillet == null
                || fillet.center() == null
                || fillet.tangentStart() == null
                || fillet.tangentEnd() == null
                || fillet.radius() <= 1e-6) {
            return List.of();
        }
        Vec2d center = fillet.center();
        double radius = fillet.radius();
        double sweep = fillet.sweepRadians();
        if (!Double.isFinite(sweep) || Math.abs(sweep) < 1e-9) {
            sweep = FilletArcSweep.resolveShort(center, fillet.tangentStart(), fillet.tangentEnd());
        }
        if (Math.abs(sweep) < 1e-9 || Math.abs(sweep) > Math.PI + 1e-6) {
            return List.of();
        }
        Vec2d sharp = fillet.sharpCorner() != null
            ? fillet.sharpCorner()
            : reconstructFilletSharpCorner(fillet, sweep);
        if (sharp == null) {
            return List.of();
        }
        double startAngle = Math.atan2(
            fillet.tangentStart().y - center.y, fillet.tangentStart().x - center.x);
        int segs = Math.max(6, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 12.0)));
        List<Vec2d> ring = new ArrayList<>(segs + 2);
        ring.add(sharp);
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            ring.add(FilletArcSweep.pointOnArc(center, radius, startAngle + sweep * t));
        }
        return ring;
    }

    /**
     * 路缘圆角圆心在草地楔形内，锐角点在圆心沿短弧中点方向、距圆心 {@code R / sin(φ/2)} 处。
     */
    public static Vec2d reconstructFilletSharpCorner(
            JunctionGeometryResult.FilletCorner fillet,
            double sweepRadians) {
        if (fillet == null || fillet.center() == null || fillet.radius() <= 1e-6) {
            return null;
        }
        double phi = fillet.cornerAngleRadians();
        double sinHalf = Math.sin(phi * 0.5);
        if (sinHalf < 1e-6) {
            return null;
        }
        double startAngle = Math.atan2(
            fillet.tangentStart().y - fillet.center().y,
            fillet.tangentStart().x - fillet.center().x);
        Vec2d mid = FilletArcSweep.pointOnArc(
            fillet.center(), fillet.radius(), startAngle + sweepRadians * 0.5);
        Vec2d fromCenter = mid.subtract(fillet.center());
        if (fromCenter.lengthSquared() < 1e-12) {
            return null;
        }
        // |mid−center|=R；锐点更远，在同一射线上
        return fillet.center().add(fromCenter.multiply(1.0 / sinHalf));
    }

    private void fillJunctionCells(
            JunctionBlocks blocks,
            List<Vec2d> surfaceCells,
            List<Vec2d> clearanceCells,
            List<Vec2d> clearancePolygon,
            RoadNode node,
            RoadNetwork network,
            List<RoadEdge> edges,
            int junctionY,
            TerrainSampler terrain,
            Map<String, RoadGenerationResult> edgeResults,
            Map<String, Integer> networkNodeElevations,
            boolean stampSurface,
            List<JunctionGeometryResult.FilletCorner> fillets) {
        if (node == null || network == null || edges == null) {
            return;
        }
        List<Vec2d> stamp = surfaceCells != null ? surfaceCells : List.of();
        List<Vec2d> clearCells = clearanceCells != null && !clearanceCells.isEmpty()
            ? clearanceCells
            : stamp;
        if (stamp.isEmpty() && clearCells.isEmpty()) {
            return;
        }
        int tunnelThreshold = RoadConstructionHeuristics.tunnelThreshold(generator.getConfig());
        int bridgeThreshold = RoadConstructionHeuristics.bridgeThreshold(generator.getConfig());
        String fillMaterialId = generator.getBlockIdFromMaterial(generator.getConfig().getFillSlopeMaterial());
        JunctionCrossSectionFill.MaterialResolver materials = new JunctionCrossSectionFill.MaterialResolver() {
            @Override
            public String resolve(String materialId) {
                return generator.getBlockIdFromMaterial(materialId);
            }

            @Override
            public BlockPos canvasToBlockPos(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0);
            }
        };
        RoadTerrainClearanceUtils.BlockColumnResolver columnResolver =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return generator.toBlockPos(planPoint, 0).getX();
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return generator.toBlockPos(planPoint, 0).getZ();
                }
            };
        double unitsPerBlock = estimateUnitsPerBlock(network, edges);
        Integer elevatedDeckY = resolveElevatedDeckY(node, junctionY, network, generator.getConfig());
        Map<String, Integer> elevations = networkNodeElevations != null
            ? networkNodeElevations
            : Map.of(node.getId(), junctionY);
        Map<String, RoadGenerationResult> safeEdgeResults =
            edgeResults != null ? edgeResults : Map.of();
        // 必须在广场清障/放坡之前收集隧道进口，否则会按露天逻辑掏穿洞顶。
        List<TunnelJunctionSeedResolver.TunnelApproach> tunnelApproaches =
            TunnelJunctionSeedResolver.collectTunnelApproaches(
                network,
                node,
                safeEdgeResults,
                elevations,
                generator.getConfig(),
                (path, segments) -> unitsPerBlock);
        Integer tunnelCavityCap = TunnelJunctionSeedResolver.resolveTunnelCavityClearCap(tunnelApproaches);
        // 净空上限：立交桥底与隧道洞腔顶取更严者。
        Integer maxClearY = elevatedDeckY != null
            ? elevatedDeckY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH - 1
            : null;
        if (tunnelCavityCap != null) {
            maxClearY = maxClearY == null ? tunnelCavityCap : Math.min(maxClearY, tunnelCavityCap);
        }
        Map<ColumnKey, Integer> leftoverTops = JunctionTerrainClearance.leftoverTopByColumn(edgeResults);
        JunctionFilletElevationLoft filletLoft = JunctionFilletElevationLoft.build(
            fillets, safeEdgeResults, edges, network, columnResolver, junctionY);
        JunctionSurfaceMask surfaceMask = new JunctionSurfaceMask();
        List<JunctionSurfaceExpectation> surfaceExpectations = new ArrayList<>();
        int unresolvedClearance = 0;

        if (stampSurface) {
            for (Vec2d point : stamp) {
                if (point == null) {
                    continue;
                }
                ColumnKey columnKey = new ColumnKey(columnResolver.worldX(point), columnResolver.worldZ(point));
                surfaceMask.expectColumn(columnKey);
                int surfaceY = filletLoft.surfaceY(point);
                boolean stamped = JunctionCrossSectionFill.fillCell(
                    blocks.getSolids(),
                    point,
                    junctionY,
                    node.getId(),
                    edges,
                    network,
                    generator.getConfig(),
                    materials,
                    columnResolver,
                    unitsPerBlock,
                    terrain,
                    tunnelThreshold,
                    bridgeThreshold,
                    fillMaterialId,
                    maxClearY,
                    surfaceY);
                if (stamped) {
                    surfaceMask.markStamped(columnKey);
                    RoadSolidLayer surfaceLayer = JunctionCrossSectionFill.resolveSurfaceLayer(
                        point, node.getId(), edges, network, generator.getConfig(), unitsPerBlock);
                    BlockPos surfacePos = JunctionCrossSectionFill.surfaceBlockPos(point, surfaceY, materials);
                    surfaceExpectations.add(new JunctionSurfaceExpectation(surfacePos, surfaceLayer));
                } else if (clearancePolygon != null && clearancePolygon.size() >= 3) {
                    unresolvedClearance++;
                }
            }
        }

        if (clearancePolygon != null && clearancePolygon.size() >= 3) {
            // 清障列 = 外形栅格 ∪ 实际铺装列，避免核区/补铺落点因栅格缝漏清树木
            List<Vec2d> overheadCells = new ArrayList<>(clearCells);
            if (stampSurface && !stamp.isEmpty()) {
                overheadCells.addAll(stamp);
                overheadCells = dedupePlanCellsByWorldColumn(overheadCells);
            }
            for (Vec2d point : overheadCells) {
                if (point == null) {
                    continue;
                }
                ColumnKey columnKey = new ColumnKey(columnResolver.worldX(point), columnResolver.worldZ(point));
                BlockPos column = generator.toBlockPos(point, 0);
                int surfaceY = filletLoft.surfaceY(point);
                int leftoverTop = leftoverTops.getOrDefault(columnKey, surfaceY);
                // 列集已由外形∪铺装栅格确定；勿再 point-in-polygon，否则圆角补铺列会漏清树木
                JunctionTerrainClearance.clearOverheadColumn(
                    blocks.getSolids(),
                    surfaceY,
                    tunnelThreshold,
                    terrain,
                    column.getX(),
                    column.getZ(),
                    point,
                    elevatedDeckY,
                    leftoverTop,
                    tunnelCavityCap);
            }
        }

        blocks.setReport(new JunctionConstructionReport(
            node.getId(),
            surfaceMask.expectedColumnCount(),
            surfaceMask.missingColumnCount(),
            0,
            0,
            unresolvedClearance,
            false,
            0,
            0,
            0,
            0,
            0));

        // 隧道进口禁止开敞切坡：与走廊 RoadEarthworkPolicy 一致，避免洞内十字边坡掏山。
        if (stampSurface && tunnelApproaches.isEmpty()) {
            JunctionCrossSectionFill.stampApproachSlopeBatters(
                blocks.getSolids(),
                node.getPosition(),
                junctionY,
                node.getId(),
                edges,
                network,
                generator.getConfig(),
                materials,
                columnResolver,
                unitsPerBlock,
                terrain,
                bridgeThreshold,
                maxClearY,
                fillets,
                filletLoft);
        }

        List<Vec2d> footprint = clearancePolygon != null && clearancePolygon.size() >= 3
            ? List.copyOf(clearancePolygon)
            : List.of();
        List<TunnelPortalSeed> portalSeeds = TunnelJunctionSeedResolver.collectPortalSeeds(
            tunnelApproaches, footprint, generator.coordinateTransformer());
        TunnelJunctionSeedResolver.UnderpassYBand underpassBand =
            TunnelJunctionSeedResolver.resolveUnderpassYBand(tunnelApproaches);
        // Batters can extend past the plaza polygon; their excavation must also
        // preserve the real sloping upper deck in those world columns.
        var constructionColumns = new java.util.HashSet<ColumnKey>();
        if (elevatedDeckY != null) {
            for (var primitive : blocks.getSolids().primitives()) {
                if (!"minecraft:air".equals(primitive.materialId())) continue;
                var column = generator.toBlockPos(primitive.planPoint(), 0);
                constructionColumns.add(new ColumnKey(column.getX(), column.getZ()));
            }
        }
        List<BlockPos> expectedProtected = JunctionExpectedProtectedCollector.collect(
            network,
            node,
            safeEdgeResults,
            elevations,
            generator.getConfig(),
            generator.coordinateTransformer(),
            (n, net) -> generator.resolveElevatedRoadId(n, net, terrain), constructionColumns);

        blocks.setValidationManifest(new JunctionValidationManifest(
            node.getId(),
            List.copyOf(surfaceExpectations),
            expectedProtected,
            portalSeeds,
            footprint,
            junctionY,
            elevatedDeckY,
            underpassBand.minY(),
            underpassBand.maxY()));
    }

    private List<RoadEdge> collectUnderpassJunctionEdges(
            RoadNode node,
            RoadNetwork network,
            TerrainSampler terrain) {
        List<RoadEdge> connectedEdges = new ArrayList<>();
        String elevatedRoadId = node.isGradeSeparated()
            ? generator.resolveElevatedRoadId(node, network, terrain)
            : null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            // Self-intersect overpass arms keep their own corridor; only fill underpass.
            if (node.isStackGradeSeparated() && node.isStackUpperEdge(edgeId)) {
                continue;
            }
            if (elevatedRoadId != null && elevatedRoadId.equals(edge.getRoadId())) {
                continue;
            }
            connectedEdges.add(edge);
        }
        return connectedEdges;
    }

    public static Integer resolveElevatedDeckY(
            RoadNode node, int junctionY, RoadNetwork network, RoadSystemConfig config) {
        if (node == null) {
            return null;
        }
        double clearance;
        if (node.isStackGradeSeparated()) {
            String roadId = null;
            if (network != null) {
                for (String edgeId : node.getConnectedEdgeIds()) {
                    RoadEdge edge = network.getEdge(edgeId);
                    if (edge != null && edge.getRoadId() != null) {
                        roadId = edge.getRoadId();
                        break;
                    }
                }
            }
            clearance = com.plot.plugin.road.crossing.GradeSeparationClearance.resolve(
                node.getStackCrossingClearance(),
                network != null && roadId != null ? network.getRoad(roadId) : null,
                config);
        } else if (node.isGradeSeparated()) {
            clearance = com.plot.plugin.road.crossing.GradeSeparationClearance.forNode(
                node.getCrossingClearance(),
                node,
                network,
                node.getElevatedRoadId(),
                config);
        } else {
            return null;
        }
        return junctionY + (int) Math.ceil(Math.max(1.0, clearance));
    }

    public static Integer resolveElevatedDeckY(RoadNode node, int junctionY, RoadSystemConfig config) {
        return resolveElevatedDeckY(node, junctionY, null, config);
    }

    private double estimateUnitsPerBlock(RoadNetwork network, List<RoadEdge> edges) {
        if (generator == null || edges == null || edges.isEmpty()) {
            return 1.0;
        }
        for (RoadEdge edge : edges) {
            List<Vec2d> centerline = RoadJunctionCenterlineResolver.forNetwork(network).apply(edge);
            if (centerline != null && centerline.size() >= 2) {
                double units = generator.estimateCanvasUnitsPerBlock(centerline, List.of());
                if (Double.isFinite(units) && units > 1e-9) {
                    return units;
                }
            }
        }
        return 1.0;
    }

    private double hardSurfaceHalfExtent(RoadNetwork network, RoadEdge edge) {
        return RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, generator.getConfig())
                .totalHardSurfaceWidthBlocks());
    }

    /**
     * 与画布预览一致：平交圆角以 Crossing 为权威，否则用节点/网络默认。
     */
    public static double resolveCornerRadius(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        double networkDefault = config != null ? config.getDefaultCornerRadius() : RoadNode.DEFAULT_CORNER_RADIUS;
        if (node != null && (node.isGradeSeparated() || node.isStackGradeSeparated())) {
            return 0.0;
        }
        RoadCrossing crossing = findAtGradeCrossingNear(network, node);
        if (crossing != null) {
            return crossing.effectiveCornerRadius(networkDefault);
        }
        return node != null ? node.getEffectiveCornerRadius(networkDefault) : networkDefault;
    }

    private static RoadCrossing findAtGradeCrossingNear(RoadNetwork network, RoadNode node) {
        if (network == null || node == null || node.getPosition() == null) {
            return null;
        }
        RoadCrossing best = null;
        double bestDistance = 0.75;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (crossing == null
                    || crossing.type() != CrossingType.AT_GRADE
                    || crossing.position() == null) {
                continue;
            }
            double distance = crossing.position().distance(node.getPosition());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = crossing;
            }
        }
        return best;
    }
}
