package com.masterplanner.infrastructure.event.block;

/**
 * 方块投影事件
 * 当需要在3D空间中投影方块时触发
 */
public class BlockProjectionEvent extends BlockEvent {
    public enum ProjectionMode {
        GROUND,
        ELEVATION
    }

    private final double x;
    private final double y;
    private final double z;
    private final ProjectionMode projectionMode;
    private final Integer elevation;
    private final float rotation;  // 添加旋转属性
    private final boolean preview; // 是否为预览状态

    public BlockProjectionEvent(String blockId, double x, double y, double z, float rotation, boolean preview) {
        this(blockId, x, y, z, rotation, preview, ProjectionMode.GROUND, null);
    }

    public BlockProjectionEvent(
            String blockId,
            double x,
            double y,
            double z,
            float rotation,
            boolean preview,
            ProjectionMode projectionMode,
            Integer elevation
    ) {
        super(blockId, null);
        this.x = x;
        this.y = y;
        this.z = z;
        this.projectionMode = projectionMode == null ? ProjectionMode.GROUND : projectionMode;
        this.elevation = elevation;
        this.rotation = rotation;
        this.preview = preview;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getRotation() { return rotation; }
    public boolean isPreview() { return preview; }
    public ProjectionMode getProjectionMode() { return projectionMode; }
    public Integer getElevation() { return elevation; }

    @Override
    public String toString() {
        return "BlockProjectionEvent[]";
    }
} 
