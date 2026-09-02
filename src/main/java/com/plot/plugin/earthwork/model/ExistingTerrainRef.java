package com.plot.plugin.earthwork.model;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;

/**
 * 现状地形快照引用（元数据 + 可选侧车文件路径）。
 */
public class ExistingTerrainRef {
    private long capturedAtEpochMs;
    private String worldKey = "";
    private long outlineFingerprint;
    private long contentFingerprint;
    private int columnCount;
    private String snapshotFile = "";

    public long getCapturedAtEpochMs() {
        return capturedAtEpochMs;
    }

    public void setCapturedAtEpochMs(long capturedAtEpochMs) {
        this.capturedAtEpochMs = capturedAtEpochMs;
    }

    public String getWorldKey() {
        return worldKey != null ? worldKey : "";
    }

    public void setWorldKey(String worldKey) {
        this.worldKey = worldKey != null ? worldKey : "";
    }

    public long getOutlineFingerprint() {
        return outlineFingerprint;
    }

    public void setOutlineFingerprint(long outlineFingerprint) {
        this.outlineFingerprint = outlineFingerprint;
    }

    public long getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(long contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public String getSnapshotFile() {
        return snapshotFile != null ? snapshotFile : "";
    }

    public void setSnapshotFile(String snapshotFile) {
        this.snapshotFile = snapshotFile != null ? snapshotFile.trim() : "";
    }

    public boolean isEmpty() {
        return columnCount <= 0 && capturedAtEpochMs <= 0L;
    }

    public static ExistingTerrainRef fromSnapshot(TerrainSnapshot snapshot, String snapshotFile) {
        ExistingTerrainRef ref = new ExistingTerrainRef();
        if (snapshot == null || snapshot.isEmpty()) {
            return ref;
        }
        TerrainSnapshot.Metadata metadata = snapshot.metadata();
        ref.capturedAtEpochMs = metadata.capturedAtEpochMs();
        ref.worldKey = metadata.worldKey();
        ref.outlineFingerprint = metadata.outlineFingerprint();
        ref.contentFingerprint = metadata.contentFingerprint();
        ref.columnCount = metadata.columnCount();
        ref.snapshotFile = snapshotFile != null ? snapshotFile : "";
        return ref;
    }
}
