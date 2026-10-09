package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.RoadDimensionUtils;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 道路实体图元集合（几何层输出，供 rasterizer 或未来 mesh exporter 消费）。
 */
public final class RoadSolidModel {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSolidModel");
    static final int DEFAULT_MAX_DEDUP_KEYS = 100_000;

    /**
     * 去重表达到上限时的分块回调：调用方应先把当前图元落地，再 {@link #clear()}。
     * 未设置时超限图元会被丢弃。
     */
    @FunctionalInterface
    public interface OverflowHandler {
        void onCapacityReached(RoadSolidModel model);
    }

    private final int maxDedupKeys;
    private final List<RoadSolidPrimitive> primitives = new ArrayList<>();
    private final Set<String> dedupKeys = new LinkedHashSet<>();
    private OverflowHandler overflowHandler;
    private ICoordinateService coordinateService;
    private int droppedDueToLimit;
    private int overflowFlushCount;
    private boolean limitLogged;

    public RoadSolidModel() {
        this(DEFAULT_MAX_DEDUP_KEYS);
    }

    /** 测试与分块验证可传入更小上限。 */
    RoadSolidModel(int maxDedupKeys) {
        this.maxDedupKeys = Math.max(1, maxDedupKeys);
    }

    public void setOverflowHandler(OverflowHandler overflowHandler) {
        this.overflowHandler = overflowHandler;
    }

    /** 去重键按投影后的世界格计算；null 时回退为画布 round。 */
    public void setCoordinateService(ICoordinateService coordinateService) {
        this.coordinateService = coordinateService;
    }

    public boolean add(RoadSolidPrimitive primitive) {
        if (primitive == null) {
            return false;
        }

        if (dedupKeys.size() >= maxDedupKeys) {
            flushOverflowChunk();
        }
        if (dedupKeys.size() >= maxDedupKeys) {
            droppedDueToLimit++;
            if (!limitLogged) {
                limitLogged = true;
                LOGGER.error(
                    "道路实体图元达到上限 {}，后续方块将被丢弃（已丢弃 {} 个）",
                    maxDedupKeys,
                    droppedDueToLimit);
            }
            return false;
        }

        if (!dedupKeys.add(dedupKey(primitive))) {
            return false;
        }
        primitives.add(primitive);
        return true;
    }

    private String dedupKey(RoadSolidPrimitive primitive) {
        if (coordinateService == null) {
            return primitive.dedupKey();
        }
        BlockPos pos = RoadVoxelRasterizer.toBlockPos(
            primitive.planPoint(), primitive.elevation(), coordinateService);
        return primitive.layer().name()
            + '@' + pos.getX()
            + ',' + pos.getY()
            + ',' + pos.getZ();
    }

    private void flushOverflowChunk() {
        if (overflowHandler == null || primitives.isEmpty()) {
            return;
        }
        overflowFlushCount++;
        LOGGER.info(
            "道路实体图元达到上限 {}，分块落地后继续（第 {} 块）",
            maxDedupKeys,
            overflowFlushCount);
        overflowHandler.onCapacityReached(this);
    }

    public boolean add(Vec2d planPoint, int elevation, RoadSolidLayer layer) {
        return add(new RoadSolidPrimitive(planPoint, elevation, layer));
    }

    public boolean add(Vec2d planPoint, int elevation, RoadSolidLayer layer, String materialId) {
        return add(new RoadSolidPrimitive(planPoint, elevation, layer, materialId));
    }

    public void addSpan(Vec2d left, Vec2d right, int elevation, RoadSolidLayer layer, String materialId) {
        for (Vec2d point : RoadVoxelRasterizer.sampleSpanPoints(left, right)) {
            add(point, elevation, layer, materialId);
        }
    }

    /**
     * 沿法线方向铺设固定方块宽度的条带（1 格 = 1 方块）。
     * 使用默认画布缩放（1 画布单位 = 1 方块），适用于无坐标变换器的测试场景。
     */
    public void addLateralStrip(
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int elevation,
            RoadSolidLayer layer,
            String materialId) {
        addLateralStrip(center, leftNormal, widthBlocks, elevation, layer, materialId, 1.0);
    }

    /**
     * 沿法线方向铺设固定方块宽度的条带，横向步进按画布/世界缩放补偿。
     *
     * @param canvasUnitsPerBlock 1 个世界方块对应的画布坐标长度；1.0 表示画布单位已与方块对齐
     */
    public void addLateralStrip(
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int elevation,
            RoadSolidLayer layer,
            String materialId,
            double canvasUnitsPerBlock) {
        if (center == null || leftNormal == null || widthBlocks <= 0) {
            return;
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            add(center.add(normal.multiply(lateral * scale)), elevation, layer, materialId);
        }
    }

    public List<RoadSolidPrimitive> primitives() {
        return Collections.unmodifiableList(primitives);
    }

    public List<RoadSolidPrimitive> byLayer(RoadSolidLayer layer) {
        return primitives.stream()
            .filter(primitive -> primitive.layer() == layer)
            .collect(Collectors.toList());
    }

    public int count(RoadSolidLayer layer) {
        return (int) primitives.stream().filter(primitive -> primitive.layer() == layer).count();
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    /** 因硬顶上限而丢弃的图元数量（去重命中不计入）。 */
    public int getDroppedDueToLimit() {
        return droppedDueToLimit;
    }

    public boolean isAtCapacity() {
        return dedupKeys.size() >= maxDedupKeys;
    }

    /** 因超限而分块落地的次数（不含最终一次 flush）。 */
    public int getOverflowFlushCount() {
        return overflowFlushCount;
    }

    /**
     * 清空所有图元和去重键，释放内存。保留 overflow handler，便于后续分块继续写入。
     */
    public void clear() {
        primitives.clear();
        dedupKeys.clear();
        droppedDueToLimit = 0;
        limitLogged = false;
    }

    public void addAll(RoadSolidModel other) {
        if (other == null) {
            return;
        }
        for (RoadSolidPrimitive primitive : other.primitives) {
            add(primitive);
        }
        // 合并对端已丢弃计数（用于聚合结果可观测）
        this.droppedDueToLimit += other.droppedDueToLimit;
        if (other.limitLogged) {
            this.limitLogged = true;
        }
    }
}
