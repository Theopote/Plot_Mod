package com.plot.core.gallery;

import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 图库条目：一组可重复放置到画布上的图形快照。
 */
public final class GalleryItem {
    private final String id;
    private final String name;
    private final String nameKey;
    private final String description;
    private final String descriptionKey;
    private final String category;
    private final boolean preset;
    private final List<ProjectSnapshot.ShapeSnapshot> shapes;
    private final long createdAt;

    public GalleryItem(
            String id,
            String name,
            String nameKey,
            String description,
            String descriptionKey,
            String category,
            boolean preset,
            List<ProjectSnapshot.ShapeSnapshot> shapes,
            long createdAt) {
        this.id = id;
        this.name = name;
        this.nameKey = nameKey;
        this.description = description;
        this.descriptionKey = descriptionKey;
        this.category = category;
        this.preset = preset;
        this.shapes = shapes != null ? List.copyOf(shapes) : List.of();
        this.createdAt = createdAt;
    }

    public static GalleryItem userItem(
            String id,
            String name,
            String description,
            String category,
            List<ProjectSnapshot.ShapeSnapshot> shapes) {
        return new GalleryItem(id, name, null, description, null, category, false, shapes, System.currentTimeMillis());
    }

    public static GalleryItem presetItem(
            String id,
            String nameKey,
            String descriptionKey,
            String category,
            List<ProjectSnapshot.ShapeSnapshot> shapes) {
        return new GalleryItem(id, null, nameKey, null, descriptionKey, category, true, shapes, 0L);
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public boolean isPreset() {
        return preset;
    }

    public List<ProjectSnapshot.ShapeSnapshot> getShapes() {
        return shapes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getDisplayName() {
        if (nameKey != null && !nameKey.isBlank()) {
            return PlotI18n.tr(nameKey);
        }
        return name != null && !name.isBlank() ? name : id;
    }

    public String getDisplayDescription() {
        if (descriptionKey != null && !descriptionKey.isBlank()) {
            return PlotI18n.tr(descriptionKey);
        }
        return description != null ? description : "";
    }

    public GallerySnapshot.ItemSnapshot toSnapshot() {
        GallerySnapshot.ItemSnapshot snap = new GallerySnapshot.ItemSnapshot();
        snap.id = id;
        snap.name = name;
        snap.nameKey = nameKey;
        snap.description = description;
        snap.descriptionKey = descriptionKey;
        snap.category = category;
        snap.preset = preset;
        snap.shapes = new ArrayList<>(shapes);
        snap.createdAt = createdAt;
        return snap;
    }

    public static GalleryItem fromSnapshot(GallerySnapshot.ItemSnapshot snap) {
        if (snap == null || snap.id == null) {
            return null;
        }
        return new GalleryItem(
            snap.id,
            snap.name,
            snap.nameKey,
            snap.description,
            snap.descriptionKey,
            snap.category,
            snap.preset,
            snap.shapes,
            snap.createdAt);
    }
}
