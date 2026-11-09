package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平交路口世界方块列责任掩膜：走廊裁剪与路口铺装/清障共用同一组 {@link ColumnKey}，
 * 保证「走廊让出的路面列」一定有路口接管，避免接缝缺块与原地形残留。
 */
public final class JunctionWorldFootprint {
    private final Set<ColumnKey> roadCore;
    private final Set<ColumnKey> filletAdditions;
    private final Set<ColumnKey> seamTransition;
    private final Set<ColumnKey> clearance;
    private final Map<ColumnKey, Vec2d> planPoints;

    private JunctionWorldFootprint(
            Set<ColumnKey> roadCore,
            Set<ColumnKey> filletAdditions,
            Set<ColumnKey> seamTransition,
            Set<ColumnKey> clearance,
            Map<ColumnKey, Vec2d> planPoints) {
        this.roadCore = Set.copyOf(roadCore);
        this.filletAdditions = Set.copyOf(filletAdditions);
        this.seamTransition = Set.copyOf(seamTransition);
        this.clearance = Set.copyOf(clearance);
        this.planPoints = Collections.unmodifiableMap(new LinkedHashMap<>(planPoints));
    }

    public static JunctionWorldFootprint empty() {
        return new JunctionWorldFootprint(
            Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
    }

    /**
     * 由路口几何直接栅格化世界列。{@code transformer} 可空（画布整数格 ≡ 世界列）。
     */
    public static JunctionWorldFootprint from(
            JunctionGeometryResult geometry,
            ICoordinateService transformer) {
        if (geometry == null || !geometry.hasFillPolygon()) {
            return empty();
        }
        Map<ColumnKey, Vec2d> planPoints = new LinkedHashMap<>();
        Set<ColumnKey> roadCore = new LinkedHashSet<>();
        Set<ColumnKey> filletAdditions = new LinkedHashSet<>();
        Set<ColumnKey> seamTransition = new LinkedHashSet<>();
        Set<ColumnKey> clearance = new LinkedHashSet<>();

        if (geometry.hasRegionZones()) {
            addPolygonColumns(geometry.overlapRegion(), transformer, planPoints, roadCore);
            for (List<Vec2d> addition : geometry.filletAdditions()) {
                addPolygonColumns(addition, transformer, planPoints, filletAdditions);
            }
            addGoreColumns(geometry, transformer, planPoints, filletAdditions);
            List<Vec2d> fill = geometry.fillPolygon();
            List<Vec2d> sharp = geometry.sharpPolygon();
            if (fill != null && fill.size() >= 3) {
                for (ColumnKey key : rasterizePolygonColumns(fill, transformer, planPoints)) {
                    if (sharp != null && sharp.size() >= 3) {
                        Vec2d plan = planPoints.get(key);
                        if (plan != null && RoadGeometryUtils.pointInPolygon(plan, sharp)) {
                            continue;
                        }
                    }
                    if (!roadCore.contains(key) && !filletAdditions.contains(key)) {
                        seamTransition.add(key);
                    }
                }
            }
        } else {
            List<Vec2d> footprint = geometry.fillPolygon().size() >= 3
                ? geometry.fillPolygon()
                : geometry.sharpPolygon();
            addPolygonColumns(footprint, transformer, planPoints, roadCore);
            if (!geometry.fillets().isEmpty()) {
                addGoreColumns(geometry, transformer, planPoints, filletAdditions);
            }
        }

        Set<ColumnKey> pave = new LinkedHashSet<>();
        pave.addAll(roadCore);
        pave.addAll(filletAdditions);
        pave.addAll(seamTransition);

        List<Vec2d> clearancePoly = geometry.fillPolygon().size() >= 3
            ? geometry.fillPolygon()
            : geometry.sharpPolygon();
        addPolygonColumns(clearancePoly, transformer, planPoints, clearance);
        clearance.addAll(pave);
        // 清障比铺装外扩 1 格，接住边界上走廊已让出但栅格未命中的原地形列
        for (ColumnKey key : dilate(pave, 1)) {
            clearance.add(key);
            planPoints.putIfAbsent(key, synthesizePlanPoint(key, transformer));
        }

        return new JunctionWorldFootprint(
            roadCore, filletAdditions, seamTransition, clearance, planPoints);
    }

    public JunctionWorldFootprint union(JunctionWorldFootprint other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Set<ColumnKey> core = new LinkedHashSet<>(roadCore);
        core.addAll(other.roadCore);
        Set<ColumnKey> fillets = new LinkedHashSet<>(filletAdditions);
        fillets.addAll(other.filletAdditions);
        Set<ColumnKey> seams = new LinkedHashSet<>(seamTransition);
        seams.addAll(other.seamTransition);
        Set<ColumnKey> clear = new LinkedHashSet<>(clearance);
        clear.addAll(other.clearance);
        Map<ColumnKey, Vec2d> plans = new LinkedHashMap<>(planPoints);
        other.planPoints.forEach(plans::putIfAbsent);
        return new JunctionWorldFootprint(core, fillets, seams, clear, plans);
    }

    public Set<ColumnKey> roadCore() {
        return roadCore;
    }

    public Set<ColumnKey> filletAdditions() {
        return filletAdditions;
    }

    public Set<ColumnKey> seamTransition() {
        return seamTransition;
    }

    public Set<ColumnKey> clearance() {
        return clearance;
    }

    public Map<ColumnKey, Vec2d> planPoints() {
        return planPoints;
    }

    /** 路口必须铺装的世界列 = 核 ∪ 圆角补铺 ∪ 接缝过渡。 */
    public Set<ColumnKey> paveColumns() {
        Set<ColumnKey> all = new LinkedHashSet<>(roadCore.size() + filletAdditions.size() + seamTransition.size());
        all.addAll(roadCore);
        all.addAll(filletAdditions);
        all.addAll(seamTransition);
        return all;
    }

    /** 走廊必须让出的路面列；与 {@link #paveColumns()} 恒等。 */
    public Set<ColumnKey> corridorTrimColumns() {
        return paveColumns();
    }

    public boolean isEmpty() {
        return paveColumns().isEmpty() && clearance.isEmpty();
    }

    public Vec2d planPoint(ColumnKey key) {
        return key == null ? null : planPoints.get(key);
    }

    public List<Vec2d> planPointsFor(Set<ColumnKey> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<Vec2d> out = new java.util.ArrayList<>(columns.size());
        for (ColumnKey key : columns) {
            Vec2d plan = planPoints.get(key);
            if (plan != null) {
                out.add(plan);
            }
        }
        return out;
    }

    private static void addPolygonColumns(
            List<Vec2d> polygon,
            ICoordinateService transformer,
            Map<ColumnKey, Vec2d> planPoints,
            Set<ColumnKey> target) {
        target.addAll(rasterizePolygonColumns(polygon, transformer, planPoints));
    }

    /**
     * 与 {@link RoadGeometryUtils#collectWorldCellPlanPoints} 同一世界格判定，
     * 但直接保留整数 {@link ColumnKey}，避免画布往返取整漂移。
     */
    private static Set<ColumnKey> rasterizePolygonColumns(
            List<Vec2d> canvasPolygon,
            ICoordinateService transformer,
            Map<ColumnKey, Vec2d> planPoints) {
        if (canvasPolygon == null || canvasPolygon.size() < 3) {
            return Set.of();
        }
        WorldProjectionSnapshot snapshot = transformer != null ? transformer.captureProjection() : null;
        if (snapshot != null && snapshot.isValid()) {
            try {
                List<Vec2d> worldPolygon = new java.util.ArrayList<>(canvasPolygon.size());
                double minX = Double.POSITIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                for (Vec2d canvas : canvasPolygon) {
                    if (canvas == null) {
                        continue;
                    }
                    Vec2d world = snapshot.toWorld(canvas);
                    worldPolygon.add(world);
                    minX = Math.min(minX, world.x);
                    minZ = Math.min(minZ, world.y);
                    maxX = Math.max(maxX, world.x);
                    maxZ = Math.max(maxZ, world.y);
                }
                if (worldPolygon.size() >= 3) {
                    int minWx = (int) Math.floor(minX);
                    int maxWx = (int) Math.ceil(maxX);
                    int minWz = (int) Math.floor(minZ);
                    int maxWz = (int) Math.ceil(maxZ);
                    long spanX = (long) maxWx - minWx + 1L;
                    long spanZ = (long) maxWz - minWz + 1L;
                    if (spanX > 0 && spanZ > 0 && spanX * spanZ <= 200_000L) {
                        Set<ColumnKey> keys = new LinkedHashSet<>();
                        for (int wx = minWx; wx <= maxWx; wx++) {
                            for (int wz = minWz; wz <= maxWz; wz++) {
                                if (!RoadGeometryUtils.pointInPolygon(new Vec2d(wx, wz), worldPolygon)) {
                                    continue;
                                }
                                ColumnKey key = new ColumnKey(wx, wz);
                                planPoints.putIfAbsent(key, snapshot.toCanvas(new Vec2d(wx, wz)));
                                keys.add(key);
                            }
                        }
                        return keys;
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to canvas integer scan
            }
        }
        Set<ColumnKey> keys = new LinkedHashSet<>();
        for (Vec2d cell : RoadGeometryUtils.collectWorldCellPlanPoints(canvasPolygon, null)) {
            if (cell == null) {
                continue;
            }
            ColumnKey key = new ColumnKey((int) Math.round(cell.x), (int) Math.round(cell.y));
            planPoints.putIfAbsent(key, cell);
            keys.add(key);
        }
        return keys;
    }

    private static void addGoreColumns(
            JunctionGeometryResult geometry,
            ICoordinateService transformer,
            Map<ColumnKey, Vec2d> planPoints,
            Set<ColumnKey> target) {
        for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
            List<Vec2d> gore = RoadJunctionGenerator.filletGorePolygon(fillet);
            if (gore.size() >= 3) {
                addPolygonColumns(gore, transformer, planPoints, target);
            }
            if (gore.isEmpty()) {
                continue;
            }
            Vec2d sharp = gore.getFirst();
            for (int i = 1; i < gore.size(); i++) {
                Vec2d onArc = gore.get(i);
                if (onArc == null) {
                    continue;
                }
                addPoint(onArc, transformer, planPoints, target);
                addPoint(sharp.add(onArc.subtract(sharp).multiply(0.35)), transformer, planPoints, target);
                addPoint(sharp.add(onArc.subtract(sharp).multiply(0.65)), transformer, planPoints, target);
            }
            addPoint(sharp, transformer, planPoints, target);
        }
    }

    private static void addPoint(
            Vec2d point,
            ICoordinateService transformer,
            Map<ColumnKey, Vec2d> planPoints,
            Set<ColumnKey> target) {
        if (point == null) {
            return;
        }
        ColumnKey key = keyOf(point, transformer);
        planPoints.putIfAbsent(key, point);
        target.add(key);
    }

    static ColumnKey keyOf(Vec2d planPoint, ICoordinateService transformer) {
        BlockPos block = RoadGeometryUtils.canvasToBlockXZ(planPoint, transformer);
        return new ColumnKey(block.getX(), block.getZ());
    }

    private static Vec2d synthesizePlanPoint(ColumnKey key, ICoordinateService transformer) {
        if (key == null) {
            return null;
        }
        WorldProjectionSnapshot snapshot = transformer != null ? transformer.captureProjection() : null;
        if (snapshot != null && snapshot.isValid()) {
            try {
                return snapshot.toCanvas(new Vec2d(key.worldX(), key.worldZ()));
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return new Vec2d(key.worldX(), key.worldZ());
    }

    private static Set<ColumnKey> dilate(Set<ColumnKey> columns, int radius) {
        if (columns == null || columns.isEmpty() || radius <= 0) {
            return Set.of();
        }
        Set<ColumnKey> out = new LinkedHashSet<>();
        for (ColumnKey key : columns) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    out.add(new ColumnKey(key.worldX() + dx, key.worldZ() + dz));
                }
            }
        }
        return out;
    }
}
