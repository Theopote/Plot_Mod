package com.plot.core.gallery;

import com.plot.core.model.serialization.ProjectSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 图库持久化 JSON 结构。
 */
public final class GallerySnapshot {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public int formatVersion = CURRENT_FORMAT_VERSION;
    public List<ItemSnapshot> items = new ArrayList<>();
    public List<String> customCategories = new ArrayList<>();
    public List<String> removedPresetIds = new ArrayList<>();

    public static final class ItemSnapshot {
        public String id;
        public String name;
        public String nameKey;
        public String description;
        public String descriptionKey;
        public String category;
        public boolean preset;
        public List<ProjectSnapshot.ShapeSnapshot> shapes = new ArrayList<>();
        public long createdAt;
    }
}
