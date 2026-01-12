package com.masterplanner.infrastructure.event.block;

/**
 * 方块投影事件
 * 当需要在3D空间中投影方块时触发
 */
public class BlockProjectionEvent extends BlockEvent {
    private final double x;
    private final double y;
    private final double z;
    private final float rotation;  // 添加旋转属性
    private final boolean preview; // 是否为预览状态

    public BlockProjectionEvent(String blockId, double x, double y, double z, float rotation, boolean preview) {
        super(blockId, null);
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
        this.preview = preview;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getRotation() { return rotation; }
    public boolean isPreview() { return preview; }

    @Override
    public String toString() {
        return "BlockProjectionEvent[]";
    }
} 