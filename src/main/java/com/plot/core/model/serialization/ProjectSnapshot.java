package com.plot.core.model.serialization;

import com.plot.core.persistence.VersionedDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目存档的 JSON 数据结构（当前 formatVersion = {@link #CURRENT_FORMAT_VERSION}）。
 * <p>
 * 旧版本通过 {@link com.plot.core.model.serialization.migration.ProjectMigrationRegistry} 链式升级。
 */
public final class ProjectSnapshot implements VersionedDocument {
    public static final int CURRENT_FORMAT_VERSION = 2;

    public int formatVersion = CURRENT_FORMAT_VERSION;
    public String name;
    public String id;
    public String description;
    public String filePath;
    public boolean modified;
    public String activeLayerId;
    public CanvasSnapshot canvas;
    public List<LayerSnapshot> layers = new ArrayList<>();

    @Override
    public int formatVersion() {
        return formatVersion;
    }

    public static final class CanvasSnapshot {
        public String id;
        public String name;
        public int width;
        public int height;
    }

    public static final class LayerSnapshot {
        public String id;
        public String name;
        public boolean visible;
        public boolean locked;
        public double opacity;
        public int zOrder;
        public ColorSnapshot color;
        public LineStyleSnapshot lineStyle;
        public List<ShapeSnapshot> shapes = new ArrayList<>();
    }

    public static final class ShapeSnapshot {
        public String type;
        public String data;
    }

    public static final class ColorSnapshot {
        public int r;
        public int g;
        public int b;
        public int a;
    }

    public static final class LineStyleSnapshot {
        public String type;
        public float width;
        public boolean visible;
        public ColorSnapshot color;
    }
}
