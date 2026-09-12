package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;

import java.util.Map;

/**
 * 将 plan 坐标系方向映射为 Minecraft BlockState（lightning_rod / chain 等）。
 * <p>
 * Plan (x, y) → World (X, Z)；横担法向 lateral 沿 plan X，forward 沿 plan Z。
 */
public final class DirectionalBlockSpecs {
    private static final String LIGHTNING_ROD = "minecraft:lightning_rod";
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

    /** 竖向锁链（灯头下垂等）。 */
    public static BlockSpec verticalChain() {
        return BlockSpec.with(CHAIN, "axis", "y");
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

    /**
     * 水平铁活板门帽（风电轮毂等）：贴在下方柱顶，{@code half=bottom} 为水平盖板。
     */
    public static BlockSpec ironTrapdoorHorizontalHub(Vec2d planDirection) {
        return BlockSpec.with(IRON_TRAPDOOR, "facing", facingFromPlan(planDirection))
            .withProperty("half", "bottom")
            .withProperty("open", "false");
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
