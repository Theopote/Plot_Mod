package com.plot.core.gallery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.model.Shape;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.core.model.serialization.ShapeSerialization;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.utils.PlotI18n;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 图库持久化与放置逻辑。
 */
public final class GalleryRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GalleryRepository");
    private static final String GALLERY_FILE_NAME = "gallery.json";
    private static final Set<String> RESERVED_CATEGORY_IDS = Set.of(
        "ALL", "BUILDING", "LANDSCAPE", "SHAPE", "SYMBOL");
    private static final String FALLBACK_CATEGORY_ID = "BUILDING";
    private static volatile GalleryRepository INSTANCE;
    private static final Object INSTANCE_LOCK = new Object();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path galleryFile;
    private final List<GalleryItem> items = new ArrayList<>();
    private final List<String> customCategories = new ArrayList<>();
    private final List<String> removedPresetIds = new ArrayList<>();
    private boolean loaded;

    private GalleryRepository() {
        Path configDir = FabricLoader.getInstance().getGameDir().resolve("plot");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("创建图库目录失败", e);
        }
        this.galleryFile = configDir.resolve(GALLERY_FILE_NAME);
    }

    public static GalleryRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (INSTANCE_LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new GalleryRepository();
                }
            }
        }
        return INSTANCE;
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }
        items.clear();
        customCategories.clear();
        removedPresetIds.clear();

        if (Files.exists(galleryFile)) {
            try (Reader reader = Files.newBufferedReader(galleryFile)) {
                GallerySnapshot snapshot = gson.fromJson(reader, GallerySnapshot.class);
                if (snapshot != null) {
                    if (snapshot.customCategories != null) {
                        customCategories.addAll(snapshot.customCategories);
                    }
                    if (snapshot.removedPresetIds != null) {
                        removedPresetIds.addAll(snapshot.removedPresetIds);
                    }
                    if (snapshot.items != null) {
                        for (GallerySnapshot.ItemSnapshot itemSnap : snapshot.items) {
                            GalleryItem item = GalleryItem.fromSnapshot(itemSnap);
                            if (item == null) {
                                continue;
                            }
                            if ("SYMBOL".equals(item.getCategory())) {
                                item = GalleryItem.userItem(
                                    item.getId(),
                                    item.getDisplayName(),
                                    item.getDisplayDescription(),
                                    "SHAPE",
                                    item.getShapes());
                            }
                            items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("加载图库失败，将使用默认预设", e);
            }
        }

        mergeMissingPresets();
        syncPresetsFromFactory();
        loaded = true;
        LOGGER.info("图库已加载 {} 个条目", items.size());
    }

    public synchronized void save() {
        GallerySnapshot snapshot = new GallerySnapshot();
        snapshot.customCategories = new ArrayList<>(customCategories);
        snapshot.removedPresetIds = new ArrayList<>(removedPresetIds);
        for (GalleryItem item : items) {
            snapshot.items.add(item.toSnapshot());
        }
        try (Writer writer = Files.newBufferedWriter(galleryFile)) {
            gson.toJson(snapshot, writer);
        } catch (IOException e) {
            LOGGER.error("保存图库失败", e);
        }
    }

    public synchronized List<GalleryItem> getItems() {
        return List.copyOf(items);
    }

    public synchronized List<String> getCustomCategories() {
        return List.copyOf(customCategories);
    }

    public synchronized void addCustomCategory(String name) {
        if (name == null || name.isBlank() || customCategories.contains(name) || isReservedCategoryId(name)) {
            return;
        }
        customCategories.add(name);
        save();
    }

    public synchronized boolean renameCustomCategory(String oldName, String newName) {
        if (oldName == null || newName == null) {
            return false;
        }
        String trimmed = newName.trim();
        if (trimmed.isBlank() || !customCategories.contains(oldName)) {
            return false;
        }
        if (trimmed.equals(oldName)) {
            return true;
        }
        if (customCategories.contains(trimmed) || isReservedCategoryId(trimmed)) {
            return false;
        }
        int index = customCategories.indexOf(oldName);
        customCategories.set(index, trimmed);
        reassignItemCategory(oldName, trimmed);
        save();
        return true;
    }

    public synchronized boolean deleteCustomCategory(String name) {
        if (!customCategories.remove(name)) {
            return false;
        }
        reassignItemCategory(name, FALLBACK_CATEGORY_ID);
        save();
        return true;
    }

    public synchronized boolean isCustomCategory(String categoryId) {
        return categoryId != null && customCategories.contains(categoryId);
    }

    public synchronized boolean isCategoryNameTaken(String name, String except) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (except != null && trimmed.equals(except)) {
            return false;
        }
        if (isReservedCategoryId(trimmed)) {
            return true;
        }
        return customCategories.contains(trimmed);
    }

    public synchronized void removeCustomCategory(String name) {
        deleteCustomCategory(name);
    }

    private void reassignItemCategory(String fromCategory, String toCategory) {
        for (int i = 0; i < items.size(); i++) {
            GalleryItem item = items.get(i);
            if (!fromCategory.equals(item.getCategory())) {
                continue;
            }
            GallerySnapshot.ItemSnapshot snap = item.toSnapshot();
            snap.category = toCategory;
            items.set(i, GalleryItem.fromSnapshot(snap));
        }
    }

    private static boolean isReservedCategoryId(String categoryId) {
        return categoryId != null && RESERVED_CATEGORY_IDS.contains(categoryId);
    }

    public synchronized Optional<GalleryItem> findById(String id) {
        return items.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public synchronized GalleryItem saveFromSelection(List<Shape> selected, String name, String description, String category) {
        if (selected == null || selected.isEmpty()) {
            throw new IllegalArgumentException("empty selection");
        }
        List<ProjectSnapshot.ShapeSnapshot> snapshots = new ArrayList<>();
        for (Shape shape : selected) {
            ProjectSnapshot.ShapeSnapshot snap = new ProjectSnapshot.ShapeSnapshot();
            snap.type = ShapeSerialization.getTypeName(shape);
            snap.data = shape.serialize();
            snapshots.add(snap);
        }
        GalleryItem item = GalleryItem.userItem(
            "user_" + UUID.randomUUID(),
            name.trim(),
            description != null ? description.trim() : "",
            category,
            snapshots);
        items.add(item);
        save();
        return item;
    }

    public synchronized void updateItem(String id, String name, String description, String category) {
        for (int i = 0; i < items.size(); i++) {
            GalleryItem existing = items.get(i);
            if (!existing.getId().equals(id)) {
                continue;
            }
            GalleryItem updated = GalleryItem.userItem(
                id,
                name.trim(),
                description != null ? description.trim() : "",
                category,
                existing.getShapes());
            items.set(i, updated);
            save();
            return;
        }
    }

    public synchronized boolean deleteItem(String id) {
        Optional<GalleryItem> existing = findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        if (existing.get().isPreset() && !removedPresetIds.contains(id)) {
            removedPresetIds.add(id);
        }
        boolean removed = items.removeIf(item -> item.getId().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public List<Shape> instantiateAt(GalleryItem item, Vec2d targetCenter) {
        if (item == null || targetCenter == null) {
            return List.of();
        }
        List<Shape> shapes = new ArrayList<>();
        for (ProjectSnapshot.ShapeSnapshot snap : item.getShapes()) {
            Shape shape = ShapeSerialization.deserialize(snap.type, snap.data);
            if (shape != null) {
                shapes.add(shape.clone());
            }
        }
        BoundingBox bounds = combinedBounds(shapes);
        if (bounds != null) {
            Vec2d offset = targetCenter.subtract(bounds.getCenter());
            for (Shape shape : shapes) {
                shape.translate(offset);
            }
        }
        return shapes;
    }

    public void placeOnCanvas(GalleryItem item, Vec2d targetCenter, AppState appState) {
        List<Shape> placed = instantiateAt(item, targetCenter);
        if (placed.isEmpty() || appState == null) {
            return;
        }
        appState.getCommandHistory().execute(
            new ModifyCommand(List.of(), placed, appState, PlotI18n.tr("history.plot.gallery.place")));
        appState.setSelectedShapes(placed);
        publishStatus("status.plot.gallery.placed", item.getDisplayName());
    }

    public void placeAtViewportCenter(GalleryItem item, AppState appState) {
        if (appState == null || appState.getCanvas() == null) {
            return;
        }
        float centerX = appState.getCanvas().getWidth() * 0.5f;
        float centerY = appState.getCanvas().getHeight() * 0.5f;
        Vec2d worldCenter = appState.getCanvas().screenToWorld(new Vec2d(centerX, centerY));
        placeOnCanvas(item, worldCenter, appState);
    }

    private void mergeMissingPresets() {
        Map<String, GalleryItem> existingById = new LinkedHashMap<>();
        for (GalleryItem item : items) {
            existingById.put(item.getId(), item);
        }
        boolean changed = false;
        for (GalleryItem preset : GalleryPresetFactory.createPresets()) {
            if (removedPresetIds.contains(preset.getId())) {
                continue;
            }
            if (!existingById.containsKey(preset.getId())) {
                items.add(preset);
                changed = true;
            }
        }
        if (changed && Files.exists(galleryFile)) {
            save();
        } else if (!Files.exists(galleryFile)) {
            items.clear();
            items.addAll(GalleryPresetFactory.createPresets());
            save();
        }
    }

    private void syncPresetsFromFactory() {
        Map<String, GalleryItem> factoryById = new LinkedHashMap<>();
        for (GalleryItem preset : GalleryPresetFactory.createPresets()) {
            factoryById.put(preset.getId(), preset);
        }
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            GalleryItem item = items.get(i);
            if (!item.isPreset()) {
                continue;
            }
            GalleryItem fresh = factoryById.get(item.getId());
            if (fresh != null) {
                items.set(i, fresh);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static BoundingBox combinedBounds(List<Shape> shapes) {
        BoundingBox combined = null;
        for (Shape shape : shapes) {
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds == null) {
                continue;
            }
            combined = combined == null ? bounds : combined.union(bounds);
        }
        return combined;
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
