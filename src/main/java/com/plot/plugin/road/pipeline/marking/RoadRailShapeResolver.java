package com.plot.plugin.road.pipeline.marking;

import com.plot.core.block.BlockSpec;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 根据四邻铁轨占用与可选轴线提示求解 Minecraft {@code shape}（无注册表依赖）。
 * <p>
 * 普通轨支持弯轨与爬坡；动力/探测/激活轨仅直线与爬坡。
 * 回写 shape 时保留输入 BlockSpec 的其它属性。
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

    /** 期望的贯通轴线（来自中心线切向或邻格贯通推断）。 */
    public enum AxisHint {
        NONE,
        NORTH_SOUTH,
        EAST_WEST
    }

    public enum Cardinal {
        NORTH,
        SOUTH,
        EAST,
        WEST;

        public Cardinal opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
            };
        }
    }

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

    /** @return 带更新 {@code shape} 的 /setblock 参数；保留原有其它属性；非铁轨原样返回 */
    public static String resolvePlacement(String materialOrSpec, RailNeighbors neighbors) {
        return resolvePlacement(materialOrSpec, neighbors, AxisHint.NONE);
    }

    public static String resolvePlacement(
            String materialOrSpec,
            RailNeighbors neighbors,
            AxisHint hint) {
        if (materialOrSpec == null || materialOrSpec.isBlank() || neighbors == null) {
            return materialOrSpec;
        }
        BlockSpec spec = BlockSpec.parse(materialOrSpec);
        if (!isRailBlockId(spec.blockId())) {
            return materialOrSpec;
        }
        AxisHint effective = hint != null && hint != AxisHint.NONE
            ? hint
            : inferAxisHint(neighbors);
        String shape = resolveShape(spec.blockId(), neighbors, effective);
        return withShapePreservingProperties(spec, shape).toSetBlockArgument();
    }

    /** 仅替换 shape，保留 powered 等其它属性。 */
    public static BlockSpec withShapePreservingProperties(BlockSpec spec, String shape) {
        if (spec == null) {
            return BlockSpec.with("minecraft:rail", "shape", shape);
        }
        Map<String, String> props = new LinkedHashMap<>(spec.properties());
        props.put("shape", shape);
        return BlockSpec.with(spec.blockId(), props);
    }

    public static String resolveShape(String blockId, RailNeighbors n) {
        return resolveShape(blockId, n, inferAxisHint(n));
    }

    public static String resolveShape(String blockId, RailNeighbors n, AxisHint hint) {
        if (n == null) {
            return SHAPE_NORTH_SOUTH;
        }
        AxisHint axis = hint != null ? hint : AxisHint.NONE;

        String ascending = resolveAscending(n, axis);
        if (ascending != null) {
            return ascending;
        }

        boolean north = n.north();
        boolean south = n.south();
        boolean east = n.east();
        boolean west = n.west();
        boolean curvesAllowed = !STRAIGHT_ONLY.contains(baseRailId(blockId).toLowerCase(Locale.ROOT));

        if (axis == AxisHint.NORTH_SOUTH && (north || south)) {
            if (north && south) {
                return SHAPE_NORTH_SOUTH;
            }
            if (!east && !west) {
                return SHAPE_NORTH_SOUTH;
            }
        }
        if (axis == AxisHint.EAST_WEST && (east || west)) {
            if (east && west) {
                return SHAPE_EAST_WEST;
            }
            if (!north && !south) {
                return SHAPE_EAST_WEST;
            }
        }

        // 直股优先（三通/十字压成直线）
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

    /**
     * 由邻格贯通推断期望轴线：双端贯通优先于单侧爬坡。
     */
    public static AxisHint inferAxisHint(RailNeighbors n) {
        if (n == null) {
            return AxisHint.NONE;
        }
        boolean nsThrough = n.north() && n.south();
        boolean ewThrough = n.east() && n.west();
        if (nsThrough && !ewThrough) {
            return AxisHint.NORTH_SOUTH;
        }
        if (ewThrough && !nsThrough) {
            return AxisHint.EAST_WEST;
        }
        if (nsThrough) {
            return AxisHint.NORTH_SOUTH;
        }
        if (ewThrough) {
            return AxisHint.EAST_WEST;
        }
        boolean ns = n.north() || n.south();
        boolean ew = n.east() || n.west();
        if (ns && !ew) {
            return AxisHint.NORTH_SOUTH;
        }
        if (ew && !ns) {
            return AxisHint.EAST_WEST;
        }
        return AxisHint.NONE;
    }

    /** 该 shape 是否朝给定世界方位伸出连接端。 */
    public static boolean connectsToward(String shapeOrSpec, Cardinal dir) {
        if (dir == null) {
            return false;
        }
        String shape = shapeOf(shapeOrSpec);
        return switch (shape) {
            case SHAPE_NORTH_SOUTH, SHAPE_ASCENDING_NORTH, SHAPE_ASCENDING_SOUTH ->
                dir == Cardinal.NORTH || dir == Cardinal.SOUTH;
            case SHAPE_EAST_WEST, SHAPE_ASCENDING_EAST, SHAPE_ASCENDING_WEST ->
                dir == Cardinal.EAST || dir == Cardinal.WEST;
            case SHAPE_NORTH_EAST -> dir == Cardinal.NORTH || dir == Cardinal.EAST;
            case SHAPE_NORTH_WEST -> dir == Cardinal.NORTH || dir == Cardinal.WEST;
            case SHAPE_SOUTH_EAST -> dir == Cardinal.SOUTH || dir == Cardinal.EAST;
            case SHAPE_SOUTH_WEST -> dir == Cardinal.SOUTH || dir == Cardinal.WEST;
            default -> false;
        };
    }

    /** A→B 方向上两格 shape 是否互相伸出端点。 */
    public static boolean mutuallyConnected(String shapeA, Cardinal fromAtoB, String shapeB) {
        if (fromAtoB == null) {
            return false;
        }
        return connectsToward(shapeA, fromAtoB) && connectsToward(shapeB, fromAtoB.opposite());
    }

    public static String shapeOf(String shapeOrSpec) {
        if (shapeOrSpec == null || shapeOrSpec.isBlank()) {
            return SHAPE_NORTH_SOUTH;
        }
        if (!shapeOrSpec.contains("[") && !shapeOrSpec.contains(":")) {
            return shapeOrSpec;
        }
        String shape = BlockSpec.parse(shapeOrSpec).property("shape");
        return shape != null ? shape : SHAPE_NORTH_SOUTH;
    }

    private static String resolveAscending(RailNeighbors n, AxisHint axis) {
        boolean allowNs = axis == AxisHint.NONE || axis == AxisHint.NORTH_SOUTH;
        boolean allowEw = axis == AxisHint.NONE || axis == AxisHint.EAST_WEST;
        // 有明确水平贯通时，不因旁支爬坡抢占直股
        if (axis == AxisHint.NORTH_SOUTH && n.north() && n.south()
                && n.northDeltaY() == 0 && n.southDeltaY() == 0) {
            return null;
        }
        if (axis == AxisHint.EAST_WEST && n.east() && n.west()
                && n.eastDeltaY() == 0 && n.westDeltaY() == 0) {
            return null;
        }
        if (allowEw) {
            if (n.eastDeltaY() == 1 || n.westDeltaY() == -1) {
                return SHAPE_ASCENDING_EAST;
            }
            if (n.westDeltaY() == 1 || n.eastDeltaY() == -1) {
                return SHAPE_ASCENDING_WEST;
            }
        }
        if (allowNs) {
            if (n.northDeltaY() == 1 || n.southDeltaY() == -1) {
                return SHAPE_ASCENDING_NORTH;
            }
            if (n.southDeltaY() == 1 || n.northDeltaY() == -1) {
                return SHAPE_ASCENDING_SOUTH;
            }
        }
        return null;
    }
}
