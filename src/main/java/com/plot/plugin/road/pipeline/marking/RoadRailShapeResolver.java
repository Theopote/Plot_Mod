package com.plot.plugin.road.pipeline.marking;

import com.plot.core.block.BlockSpec;

import java.util.Locale;
import java.util.Set;

/**
 * 根据四邻铁轨占用求解 Minecraft {@code shape} BlockState（不含注册表依赖，便于单测）。
 * <p>
 * 普通轨支持弯轨与爬坡；动力/探测/激活轨仅直线与爬坡。
 */
public final class RoadRailShapeResolver {
    private static final Set<String> STRAIGHT_ONLY = Set.of(
        "minecraft:powered_rail",
        "minecraft:detector_rail",
        "minecraft:activator_rail");

    public static final String SHAPE_NORTH_SOUTH = "north_south";
    public static final String SHAPE_EAST_WEST = "east_west";
    public static final String SHAPE_ASCENDING_NORTH = "ascending_north";
    public static final String SHAPE_ASCENDING_SOUTH = "ascending_south";
    public static final String SHAPE_ASCENDING_EAST = "ascending_east";
    public static final String SHAPE_ASCENDING_WEST = "ascending_west";
    public static final String SHAPE_NORTH_EAST = "north_east";
    public static final String SHAPE_NORTH_WEST = "north_west";
    public static final String SHAPE_SOUTH_EAST = "south_east";
    public static final String SHAPE_SOUTH_WEST = "south_west";

    private RoadRailShapeResolver() {
    }

    /**
     * 四邻占用：同列或邻列 ±1 高程上存在铁轨即视为连通。
     * deltaY：该向邻轨相对本格的高差（+1 邻高，-1 邻低，0 同高或无爬坡信息）。
     */
    public record RailNeighbors(
            boolean north,
            boolean south,
            boolean east,
            boolean west,
            int northDeltaY,
            int southDeltaY,
            int eastDeltaY,
            int westDeltaY) {
        public static RailNeighbors flat(boolean north, boolean south, boolean east, boolean west) {
            return new RailNeighbors(north, south, east, west, 0, 0, 0, 0);
        }
    }

    public static boolean isRailBlockId(String blockIdOrSpec) {
        if (blockIdOrSpec == null || blockIdOrSpec.isBlank()) {
            return false;
        }
        String id = BlockSpec.parse(blockIdOrSpec).blockId().toLowerCase(Locale.ROOT);
        return id.equals("minecraft:rail")
            || id.equals("minecraft:powered_rail")
            || id.equals("minecraft:detector_rail")
            || id.equals("minecraft:activator_rail")
            || id.endsWith("_rail");
    }

    public static String baseRailId(String materialOrSpec) {
        return BlockSpec.parse(materialOrSpec == null ? "minecraft:rail" : materialOrSpec).blockId();
    }

    /** @return 带 {@code shape} 的 /setblock 参数；非铁轨原样返回 */
    public static String resolvePlacement(String materialOrSpec, RailNeighbors neighbors) {
        if (materialOrSpec == null || materialOrSpec.isBlank() || neighbors == null) {
            return materialOrSpec;
        }
        BlockSpec spec = BlockSpec.parse(materialOrSpec);
        if (!isRailBlockId(spec.blockId())) {
            return materialOrSpec;
        }
        String shape = resolveShape(spec.blockId(), neighbors);
        return BlockSpec.with(spec.blockId(), "shape", shape).toSetBlockArgument();
    }

    public static String resolveShape(String blockId, RailNeighbors n) {
        if (n == null) {
            return SHAPE_NORTH_SOUTH;
        }
        String ascending = resolveAscending(n);
        if (ascending != null) {
            return ascending;
        }

        boolean north = n.north();
        boolean south = n.south();
        boolean east = n.east();
        boolean west = n.west();
        boolean curvesAllowed = !STRAIGHT_ONLY.contains(baseRailId(blockId).toLowerCase(Locale.ROOT));

        // 直股优先（三通/十字压成直线，避免原版无法表达的交叉）
        if (north && south) {
            return SHAPE_NORTH_SOUTH;
        }
        if (east && west) {
            return SHAPE_EAST_WEST;
        }
        if (curvesAllowed) {
            if (north && east) {
                return SHAPE_NORTH_EAST;
            }
            if (north && west) {
                return SHAPE_NORTH_WEST;
            }
            if (south && east) {
                return SHAPE_SOUTH_EAST;
            }
            if (south && west) {
                return SHAPE_SOUTH_WEST;
            }
        }
        if (east || west) {
            return SHAPE_EAST_WEST;
        }
        return SHAPE_NORTH_SOUTH;
    }

    private static String resolveAscending(RailNeighbors n) {
        // ascending_<dir> = 该方向端点高一格
        if (n.eastDeltaY() == 1 || n.westDeltaY() == -1) {
            return SHAPE_ASCENDING_EAST;
        }
        if (n.westDeltaY() == 1 || n.eastDeltaY() == -1) {
            return SHAPE_ASCENDING_WEST;
        }
        if (n.northDeltaY() == 1 || n.southDeltaY() == -1) {
            return SHAPE_ASCENDING_NORTH;
        }
        if (n.southDeltaY() == 1 || n.northDeltaY() == -1) {
            return SHAPE_ASCENDING_SOUTH;
        }
        return null;
    }
}
