package com.plot.plugin.powerline.placement;

/** 铁塔四脚基础方案：统一建造基准面 + 各脚地面/水面高度。 */
public record TowerFoundationPlan(
        int referenceBuildBaseY,
        int[] terrainGroundY,
        int[] buildBaseY,
        int unevenDeltaBlocks) {

    public static final int CORNER_COUNT = 4;

    public TowerFoundationPlan {
        terrainGroundY = copyCornerArray(terrainGroundY);
        buildBaseY = copyCornerArray(buildBaseY);
    }

    public boolean requiresLegFill(int cornerIndex) {
        if (cornerIndex < 0 || cornerIndex >= CORNER_COUNT) {
            return false;
        }
        return referenceBuildBaseY > terrainGroundY[cornerIndex];
    }

    public int fillTopY(int cornerIndex) {
        return referenceBuildBaseY;
    }

    public int fillBottomY(int cornerIndex) {
        return terrainGroundY[cornerIndex] + 1;
    }

    private static int[] copyCornerArray(int[] values) {
        if (values == null || values.length != CORNER_COUNT) {
            return new int[CORNER_COUNT];
        }
        return values.clone();
    }
}
