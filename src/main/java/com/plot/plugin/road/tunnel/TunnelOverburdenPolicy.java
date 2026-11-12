package com.plot.plugin.road.tunnel;

/**
 * 隧道覆土与壳外空腔策略：区分地层噪点小孔与真正的大洞穴。
 */
public final class TunnelOverburdenPolicy {
    /**
     * 竖直方向连续空气/流体格数 ≤ 此值时，视为可忽略的小孔洞：
     * 洞顶封填按正常开挖补实，连续覆土判定可跨越。
     * 更大空腔按大洞穴处理：不整穴填死、不连锁打穿地表。
     * <p>
     * 取 2 而非 3：洞顶上连续 3 格气隙已属「浮石/断覆土」场景，不得再当成可跨越噪点。
     */
    public static final int SMALL_AIR_POCKET_MAX_BLOCKS = 2;

    private TunnelOverburdenPolicy() {
    }

    /**
     * 自 {@code fromY} 向上数连续「可封填空洞」（非工程实心、非可清装饰）。
     * 重力块不算空洞（由稳定化路径处理）。
     */
    public static int measureSealableVoidRun(
            com.plot.core.terrain.TerrainSampler terrain,
            int worldX,
            int fromY,
            int worldZ,
            int maxYInclusive) {
        if (terrain == null || fromY > maxYInclusive) {
            return 0;
        }
        int run = 0;
        for (int y = fromY; y <= maxYInclusive; y++) {
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                break;
            }
            if (terrain.isGravityAffectedBlock(worldX, y, worldZ)) {
                break;
            }
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                break;
            }
            // 空气、流体、其它非承重空隙
            run++;
            if (run > SMALL_AIR_POCKET_MAX_BLOCKS) {
                return run;
            }
        }
        return run;
    }

    public static boolean isSmallSealableVoidRun(int voidRunBlocks) {
        return voidRunBlocks > 0 && voidRunBlocks <= SMALL_AIR_POCKET_MAX_BLOCKS;
    }
}
