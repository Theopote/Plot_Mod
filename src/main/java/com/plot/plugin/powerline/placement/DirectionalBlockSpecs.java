package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;

/**
 * 将 plan 坐标系方向映射为 Minecraft BlockState（lightning_rod / chain 等）。
 * <p>
 * Plan (x, y) → World (X, Z)；横担法向 lateral 沿 plan X，forward 沿 plan Z。
 */
public final class DirectionalBlockSpecs {
    private static final String LIGHTNING_ROD = "minecraft:lightning_rod";
    private static final String CHAIN = "minecraft:chain";

    private DirectionalBlockSpecs() {
    }

    /** 水平避雷针：沿 plan 方向伸出。 */
    public static BlockSpec lightningRodAlong(Vec2d planDirection) {
        Vec2d direction = normalize(planDirection);
        if (Math.abs(direction.x) >= Math.abs(direction.y)) {
            return direction.x >= 0.0
                ? BlockSpec.with(LIGHTNING_ROD, "facing", "east")
                : BlockSpec.with(LIGHTNING_ROD, "facing", "west");
        }
        return direction.y >= 0.0
            ? BlockSpec.with(LIGHTNING_ROD, "facing", "south")
            : BlockSpec.with(LIGHTNING_ROD, "facing", "north");
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

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() <= 1e-12) {
            return new Vec2d(1, 0);
        }
        return vector.normalize();
    }
}
