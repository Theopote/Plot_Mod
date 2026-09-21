package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 plan 坐标系方向映射为 Minecraft BlockState（lightning_rod / chain 等）。
 * <p>
 * Plan (x, y) → World (X, Z)；横担法向 lateral 沿 plan X，forward 沿 plan Z。
 */
public final class DirectionalBlockSpecs {
    private static final String LIGHTNING_ROD = "minecraft:lightning_rod";
    private static final String IRON_BARS = "minecraft:iron_bars";
    /** iron_bars 仅支持水平四向连接；竖向靠连续堆叠表现。 */
    private static final List<String> IRON_BAR_CONNECTION_AXES = List.of(
        "north", "south", "east", "west");
    private static final String CHAIN = "minecraft:chain";
    private static final String SOUL_LANTERN = "minecraft:soul_lantern";
    private static final String LANTERN = "minecraft:lantern";
    private static final String VINE = "minecraft:vine";
    private static final String IRON_TRAPDOOR = "minecraft:iron_trapdoor";

    private DirectionalBlockSpecs() {
    }

    /** 水平避雷针：沿 plan 方向伸出。 */
    public static BlockSpec lightningRodAlong(Vec2d planDirection) {
        return BlockSpec.with(LIGHTNING_ROD, "facing", facingFromPlan(planDirection));
    }

    /** 竖向避雷针（塔顶天线等）。 */
    public static BlockSpec verticalLightningRod() {
        return BlockSpec.with(LIGHTNING_ROD, "facing", "up");
    }

    /** 塔体成员方向：世界坐标 delta → {@code facing}。 */
    public static BlockSpec lightningRodAlongMember(double deltaX, double deltaY, double deltaZ) {
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absY >= absX && absY >= absZ) {
            return BlockSpec.with(LIGHTNING_ROD, "facing", deltaY >= 0.0 ? "up" : "down");
        }
        if (absX >= absZ) {
            return BlockSpec.with(LIGHTNING_ROD, "facing", deltaX >= 0.0 ? "east" : "west");
        }
        return BlockSpec.with(LIGHTNING_ROD, "facing", deltaZ >= 0.0 ? "south" : "north");
    }

    /** 竖向铁栏杆（无水平连接；竖向本体由方块模型表现）。 */
    public static BlockSpec verticalIronBars() {
        return BlockSpec.of(IRON_BARS);
    }

    /**
     * 合并同一 voxel 上两根（或多根）iron_bars 的连接轴。
     * 非 iron_bars 或与现有方块类型不同则返回 {@code incomingPlacement}。
     */
    public static String mergeIronBarsPlacements(String existingPlacement, String incomingPlacement) {
        BlockSpec existing = BlockSpec.parse(existingPlacement);
        BlockSpec incoming = BlockSpec.parse(incomingPlacement);
        if (!IRON_BARS.equals(existing.blockId()) || !IRON_BARS.equals(incoming.blockId())) {
            return incomingPlacement;
        }
        Map<String, String> merged = new LinkedHashMap<>();
        for (String axis : IRON_BAR_CONNECTION_AXES) {
            if (isIronBarConnection(existing.property(axis)) || isIronBarConnection(incoming.property(axis))) {
                merged.put(axis, "true");
            }
        }
        if (merged.isEmpty()) {
            return incomingPlacement;
        }
        return BlockSpec.with(IRON_BARS, merged).toSetBlockArgument();
    }

    private static boolean isIronBarConnection(String value) {
        return "true".equalsIgnoreCase(value);
    }

    /** 塔体成员方向：世界坐标 delta → iron_bars 水平连接轴（竖向成员无属性）。 */
    public static BlockSpec ironBarsAlongMember(double deltaX, double deltaY, double deltaZ) {
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absY >= absX && absY >= absZ) {
            return verticalIronBars();
        }
        if (absX >= absZ) {
            return BlockSpec.with(IRON_BARS, deltaX >= 0.0 ? "east" : "west", "true");
        }
        return BlockSpec.with(IRON_BARS, deltaZ >= 0.0 ? "south" : "north", "true");
    }

    /**
     * 按 6-连通体素路径设置 iron_bars 连接，使斜撑在 Minecraft 中首尾相连。
     */
    public static BlockSpec ironBarsAlongVoxelPath(List<BlockPos> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return verticalIronBars();
        }
        Map<String, String> props = new LinkedHashMap<>();
        BlockPos current = path.get(index);
        if (index > 0) {
            mergeIronBarConnection(props, current, path.get(index - 1));
        }
        if (index < path.size() - 1) {
            mergeIronBarConnection(props, current, path.get(index + 1));
        }
        if (props.isEmpty()) {
            return verticalIronBars();
        }
        return BlockSpec.with(IRON_BARS, props);
    }

    /** 厚度扩展体素：朝向中心线锚点连接。 */
    public static BlockSpec ironBarsTowardCore(BlockPos offset, BlockPos core) {
        if (offset == null || core == null || offset.equals(core)) {
            return verticalIronBars();
        }
        Map<String, String> props = new LinkedHashMap<>();
        mergeIronBarConnection(props, offset, core);
        if (props.isEmpty()) {
            return verticalIronBars();
        }
        return BlockSpec.with(IRON_BARS, props);
    }

    private static void mergeIronBarConnection(Map<String, String> props, BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (dx > 0) {
            props.put("east", "true");
        } else if (dx < 0) {
            props.put("west", "true");
        }
        if (dz > 0) {
            props.put("south", "true");
        } else if (dz < 0) {
            props.put("north", "true");
        }
    }

    /** 竖向锁链（灯头下垂等）。 */
    public static BlockSpec verticalChain() {
        return verticalChain(CHAIN);
    }

    public static BlockSpec verticalChain(String blockId) {
        return BlockSpec.with(normalizeChainBlockId(blockId), "axis", "y");
    }

    /** 是否为沿 {@code axis} 属性首尾相接的链子类方块。 */
    public static boolean usesAxisChainPlacement(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        String baseId = BlockSpec.parse(blockId).blockId();
        if (CHAIN.equals(baseId) || baseId.endsWith(":chain") || baseId.endsWith("_chain")) {
            return true;
        }
        return hasAxisBlockProperty(baseId);
    }

    /**
     * 按 6-连通路径设置链子轴向，使相邻链子首尾相接。
     */
    public static BlockSpec chainAlongVoxelPath(List<BlockPos> path, int index) {
        return chainAlongVoxelPath(CHAIN, path, index);
    }

    public static BlockSpec chainAlongVoxelPath(String blockId, List<BlockPos> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return verticalChain(blockId);
        }
        BlockPos current = path.get(index);
        BlockPos next = nextDistinct(path, index, 1);
        if (next != null) {
            return chainAlongBlockStep(blockId, current, next);
        }
        BlockPos previous = nextDistinct(path, index, -1);
        if (previous != null) {
            return chainAlongBlockStep(blockId, previous, current);
        }
        return verticalChain(blockId);
    }

    /**
     * 导线用链子：直线段保持 axis 链；转角/折线节点改用 iron_bars 多向连接（chain 无法同时表达 x+z）。
     */
    public static BlockSpec wireChainPlacementAlongPath(String blockId, List<BlockPos> path, int index) {
        if (isWireTurnVoxel(path, index)) {
            return ironBarsAlongVoxelPath(path, index);
        }
        return chainAlongVoxelPath(blockId, path, index);
    }

    private static boolean isWireTurnVoxel(List<BlockPos> path, int index) {
        BlockPos current = path.get(index);
        BlockPos previous = nextDistinct(path, index, -1);
        BlockPos next = nextDistinct(path, index, 1);
        if (previous == null || next == null) {
            return false;
        }
        int inX = previous.getX() - current.getX();
        int inY = previous.getY() - current.getY();
        int inZ = previous.getZ() - current.getZ();
        int outX = next.getX() - current.getX();
        int outY = next.getY() - current.getY();
        int outZ = next.getZ() - current.getZ();
        return inX != -outX || inY != -outY || inZ != -outZ;
    }

    private static BlockPos nextDistinct(List<BlockPos> path, int index, int direction) {
        BlockPos current = path.get(index);
        for (int i = index + direction; i >= 0 && i < path.size(); i += direction) {
            BlockPos candidate = path.get(i);
            if (!candidate.equals(current)) {
                return candidate;
            }
        }
        return null;
    }

    /** 水平锁链：沿 plan 方向。 */
    public static BlockSpec chainAlong(Vec2d planDirection) {
        Vec2d direction = normalize(planDirection);
        if (Math.abs(direction.x) >= Math.abs(direction.y)) {
            return BlockSpec.with(CHAIN, "axis", "x");
        }
        return BlockSpec.with(CHAIN, "axis", "z");
    }

    /** 下垂灯头（挂于 chain 下方）。 */
    public static BlockSpec hangingSoulLantern() {
        return BlockSpec.with(SOUL_LANTERN, "hanging", "true");
    }

    /** 柱顶灯笼（坐在下方方块上，非悬挂）。 */
    public static BlockSpec poleTopLantern() {
        return BlockSpec.with(LANTERN, "hanging", "false");
    }

    /** 柱顶藤蔓帽：四向垂挂，与下方柱身衔接。 */
    public static BlockSpec rusticVineCap() {
        return BlockSpec.with(VINE, Map.of(
            "north", "true",
            "south", "true",
            "east", "true",
            "west", "true",
            "up", "false"));
    }

    /** 横担板/slab：下半砖贴附横担。 */
    public static BlockSpec crossarmSlab(String blockId) {
        if (blockId != null && blockId.endsWith("_slab")) {
            return BlockSpec.with(blockId, "type", "bottom");
        }
        return BlockSpec.of(blockId);
    }

    /**
     * 沿成员方向解析 BlockState（塔体成员、绝缘子串等）。
     * 无成员向量时避雷针默认为竖向朝上。
     */
    public static BlockSpec resolveMemberPlacement(
            String blockId,
            Double deltaX,
            Double deltaY,
            Double deltaZ) {
        if ("minecraft:lightning_rod".equals(blockId)) {
            if (deltaX != null && deltaY != null && deltaZ != null) {
                return lightningRodAlongMember(deltaX, deltaY, deltaZ);
            }
            return verticalLightningRod();
        }
        if ("minecraft:iron_bars".equals(blockId)) {
            if (deltaX != null && deltaY != null && deltaZ != null) {
                return ironBarsAlongMember(deltaX, deltaY, deltaZ);
            }
            return verticalIronBars();
        }
        if (usesAxisChainPlacement(blockId) && deltaX != null && deltaY != null && deltaZ != null) {
            return chainAlongMember(blockId, deltaX, deltaY, deltaZ);
        }
        return BlockSpec.of(blockId);
    }

    /**
     * 导线/绝缘子串沿路径放置：链子、避雷针、铁栏杆等按相邻体素逐步定向。
     */
    public static BlockSpec resolveWirePlacementAlongPath(String blockId, List<BlockPos> path, int index) {
        if (usesAxisChainPlacement(blockId)) {
            return wireChainPlacementAlongPath(blockId, path, index);
        }
        if (LIGHTNING_ROD.equals(blockId)) {
            return lightningRodAlongVoxelPath(path, index);
        }
        if (IRON_BARS.equals(blockId)) {
            return ironBarsAlongVoxelPath(path, index);
        }
        return BlockSpec.of(blockId);
    }

    /**
     * 水平铁活板门帽（风电轮毂等）：贴在下方柱顶，{@code half=bottom} 为水平盖板。
     */
    public static BlockSpec ironTrapdoorHorizontalHub(Vec2d planDirection) {
        return BlockSpec.with(IRON_TRAPDOOR, "facing", facingFromPlan(planDirection))
            .withProperty("half", "bottom")
            .withProperty("open", "false");
    }

    private static BlockSpec chainAlongMember(String blockId, double deltaX, double deltaY, double deltaZ) {
        String resolvedId = normalizeChainBlockId(blockId);
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absX < 1e-9 && absY < 1e-9 && absZ < 1e-9) {
            return verticalChain(resolvedId);
        }
        if (absY > 0 && absY >= absX && absY >= absZ) {
            return verticalChain(resolvedId);
        }
        if (absX >= absZ) {
            return BlockSpec.with(resolvedId, "axis", "x");
        }
        return BlockSpec.with(resolvedId, "axis", "z");
    }

    private static BlockSpec chainAlongBlockStep(String blockId, BlockPos from, BlockPos to) {
        return chainAlongMember(
            blockId,
            to.getX() - from.getX(),
            to.getY() - from.getY(),
            to.getZ() - from.getZ());
    }

    private static String normalizeChainBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return CHAIN;
        }
        return BlockSpec.parse(blockId).blockId();
    }

    private static boolean hasAxisBlockProperty(String blockId) {
        try {
            Block block = resolveBlock(blockId);
            return block != null && block.getStateManager().getProperty("axis") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Block resolveBlock(String blockId) {
        String namespace = "minecraft";
        String path = blockId;
        if (blockId.contains(":")) {
            String[] parts = blockId.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        Identifier identifier = Identifier.of(namespace, path);
        Block block = Registries.BLOCK.get(identifier);
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
            return null;
        }
        return block;
    }

    private static BlockSpec lightningRodAlongVoxelPath(List<BlockPos> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return verticalLightningRod();
        }
        BlockPos current = path.get(index);
        if (index < path.size() - 1) {
            return lightningRodAlongBlockStep(current, path.get(index + 1));
        }
        if (index > 0) {
            return lightningRodAlongBlockStep(path.get(index - 1), current);
        }
        return verticalLightningRod();
    }

    private static BlockSpec lightningRodAlongBlockStep(BlockPos from, BlockPos to) {
        return lightningRodAlongMember(
            to.getX() - from.getX(),
            to.getY() - from.getY(),
            to.getZ() - from.getZ());
    }

    private static String facingFromPlan(Vec2d planDirection) {
        Vec2d direction = normalize(planDirection);
        if (Math.abs(direction.x) >= Math.abs(direction.y)) {
            return direction.x >= 0.0 ? "east" : "west";
        }
        return direction.y >= 0.0 ? "south" : "north";
    }

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() <= 1e-12) {
            return new Vec2d(1, 0);
        }
        return vector.normalize();
    }
}
