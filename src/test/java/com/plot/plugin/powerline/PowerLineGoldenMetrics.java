package com.plot.plugin.powerline;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.placement.PlacementCategory;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Golden acceptance 场景的一次生成结果摘要。
 * <p>
 * {@link #towerGapCount()} 为物理杆间跨数；{@link #conductorSpanCount()} 为挂点通道匹配后的导线几何条数。
 * 结构正面镜像对称暂未在全线场景启用（需按 {@link PoleFrame} 局部坐标逐塔校验）。
 */
public final class PowerLineGoldenMetrics {
    private final int poleCount;
    /** 相邻杆塔之间的物理跨数（open path 为 poleCount - 1）。 */
    private final int towerGapCount;
    /** 按挂点通道匹配的导线几何跨数（每跨 × 每通道一条）。 */
    private final int conductorSpanCount;
    private final int totalAttachmentCount;
    private final int blockCount;
    private final int structureBlockCount;
    private final int invalidPoleCount;
    private final int warningCount;
    private final Map<TowerRole, Integer> roleCounts;
    private final IntCuboid boundingBox;
    private final boolean structuralFrontSymmetry;

    public PowerLineGoldenMetrics(
            int poleCount,
            int towerGapCount,
            int conductorSpanCount,
            int totalAttachmentCount,
            int blockCount,
            int structureBlockCount,
            int invalidPoleCount,
            int warningCount,
            Map<TowerRole, Integer> roleCounts,
            IntCuboid boundingBox,
            boolean structuralFrontSymmetry) {
        this.poleCount = poleCount;
        this.towerGapCount = towerGapCount;
        this.conductorSpanCount = conductorSpanCount;
        this.totalAttachmentCount = totalAttachmentCount;
        this.blockCount = blockCount;
        this.structureBlockCount = structureBlockCount;
        this.invalidPoleCount = invalidPoleCount;
        this.warningCount = warningCount;
        this.roleCounts = roleCounts;
        this.boundingBox = boundingBox;
        this.structuralFrontSymmetry = structuralFrontSymmetry;
    }

    public static PowerLineGoldenMetrics from(PowerLineGenerationResult result) {
        int attachments = 0;
        int invalidPoles = 0;
        for (PolePlacement placement : result.polePlacements) {
            if (!placement.isValid()) {
                invalidPoles++;
            }
            attachments += placement.attachments().size();
        }
        Map<TowerRole, Integer> roles = new EnumMap<>(TowerRole.class);
        roles.putAll(result.towersByRole);

        int towerGapCount = Math.max(0, result.poleCount - 1);
        return new PowerLineGoldenMetrics(
            result.poleCount,
            towerGapCount,
            result.conductorSpans.size(),
            attachments,
            result.blockCount(),
            result.structureBlockCount,
            invalidPoles,
            result.warnings.size(),
            roles,
            IntCuboid.fromPlacementRecords(result.placementRecords),
            checkStructuralFrontSymmetry(result));
    }

    public int poleCount() {
        return poleCount;
    }

    public int towerGapCount() {
        return towerGapCount;
    }

    public int conductorSpanCount() {
        return conductorSpanCount;
    }

    public int totalAttachmentCount() {
        return totalAttachmentCount;
    }

    public int blockCount() {
        return blockCount;
    }

    public int structureBlockCount() {
        return structureBlockCount;
    }

    public int invalidPoleCount() {
        return invalidPoleCount;
    }

    public int warningCount() {
        return warningCount;
    }

    public Map<TowerRole, Integer> roleCounts() {
        return roleCounts;
    }

    public IntCuboid boundingBox() {
        return boundingBox;
    }

    public boolean structuralFrontSymmetry() {
        return structuralFrontSymmetry;
    }

    private static boolean checkStructuralFrontSymmetry(PowerLineGenerationResult result) {
        if (result.placementRecords.isEmpty()) {
            return true;
        }
        Set<String> occupied = new HashSet<>();
        for (Map.Entry<BlockPos, PlacementCategory> entry : result.placementCategories.entrySet()) {
            if (!isStructural(entry.getValue())) {
                continue;
            }
            BlockPos pos = entry.getKey();
            occupied.add(voxelKey(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (occupied.isEmpty()) {
            return true;
        }
        for (String key : occupied) {
            int[] parts = parseVoxelKey(key);
            int z = parts[2];
            if (z == 0) {
                continue;
            }
            String mirrorKey = voxelKey(parts[0], parts[1], -z);
            if (!occupied.contains(mirrorKey)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStructural(PlacementCategory category) {
        return category == PlacementCategory.LEG
            || category == PlacementCategory.BRACE
            || category == PlacementCategory.ARM
            || category == PlacementCategory.STRUCTURE;
    }

    private static String voxelKey(int x, int y, int z) {
        return x + "|" + y + "|" + z;
    }

    private static int[] parseVoxelKey(String key) {
        String[] parts = key.split("\\|");
        return new int[] {
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        };
    }

    /** 轴对齐整数包围盒（世界方块坐标）。 */
    public static final class IntCuboid {
        private final boolean empty;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        private IntCuboid(boolean empty, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.empty = empty;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        static IntCuboid empty() {
            return new IntCuboid(true, 0, 0, 0, 0, 0, 0);
        }

        static IntCuboid fromPlacementRecords(Map<BlockPos, BlockRecord> records) {
            if (records.isEmpty()) {
                return empty();
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : records.keySet()) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new IntCuboid(false, minX, maxX, minY, maxY, minZ, maxZ);
        }

        public int spanX() {
            return empty ? 0 : maxX - minX + 1;
        }

        public int spanY() {
            return empty ? 0 : maxY - minY + 1;
        }

        public int spanZ() {
            return empty ? 0 : maxZ - minZ + 1;
        }
    }
}
