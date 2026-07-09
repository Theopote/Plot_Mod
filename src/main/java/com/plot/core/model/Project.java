package com.plot.core.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.plot.api.model.ILayer;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.core.model.serialization.ShapeSerialization;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 表示一个工程项目
 */
public class Project {
    private static final Logger LOGGER = LoggerFactory.getLogger(Project.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String name;
    private final String id;
    private String description;
    private final List<ILayer> layers = new CopyOnWriteArrayList<>();
    private ILayer activeLayer;
    private boolean modified;
    private String filePath;
    private CanvasModel canvasModel;

    public Project(String name) {
        this(UUID.randomUUID().toString(), name, true);
    }

    private Project(String id, String name, boolean initializeDefaults) {
        this.id = id;
        this.name = name != null && !name.isBlank() ? name : PlotI18n.defaultProjectName();
        this.modified = false;
        this.canvasModel = new CanvasModel(PlotI18n.defaultCanvasName(), 800, 600);

        if (initializeDefaults) {
            ILayer defaultLayer = new Layer(PlotI18n.defaultLayerName());
            layers.add(defaultLayer);
            activeLayer = defaultLayer;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setModified(true);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取所有图层
     */
    public List<ILayer> getLayers() {
        return new ArrayList<>(layers);
    }

    /**
     * 获取当前活动图层
     */
    public ILayer getActiveLayer() {
        return activeLayer;
    }

    /**
     * 设置当前活动图层
     */
    public void setActiveLayer(ILayer layer) {
        if (layer != null && layers.contains(layer)) {
            activeLayer = layer;
        }
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public CanvasModel getCanvas() {
        return canvasModel;
    }

    public void setCanvas(CanvasModel canvasModel) {
        this.canvasModel = canvasModel;
    }

    /**
     * 从运行时图层管理器捕获当前项目状态。
     */
    public static Project captureFromAppState(AppState appState) {
        if (appState == null) {
            return new Project(PlotI18n.defaultProjectName());
        }

        Project existing = appState.getCurrentProject();
        String projectName = existing != null ? existing.getName() : PlotI18n.defaultProjectName();
        Project project = existing != null
                ? new Project(existing.getId(), projectName, false)
                : new Project(projectName);

        if (existing != null) {
            project.description = existing.getDescription();
            project.filePath = existing.getFilePath();
            project.modified = existing.isModified();
            if (existing.getCanvas() != null) {
                project.canvasModel = existing.getCanvas();
            }
        }

        LayerManager layerManager = appState.getLayerManager();
        if (layerManager != null) {
            project.syncLayersFrom(layerManager);
        } else if (existing != null) {
            project.replaceLayers(existing.getLayers(), existing.getActiveLayer());
        }

        return project;
    }

    /**
     * 将项目内容写回运行时状态。
     */
    public void applyToAppState(AppState appState) {
        if (appState == null) {
            return;
        }

        appState.setCurrentProject(this);
        LayerManager layerManager = appState.getLayerManager();
        if (layerManager != null) {
            syncLayersTo(layerManager);
            if (activeLayer != null) {
                appState.setActiveLayer(activeLayer);
            }
        }
        setModified(false);
    }

    /**
     * 从图层管理器复制图层数据到项目。
     */
    public void syncLayersFrom(LayerManager layerManager) {
        if (layerManager == null) {
            return;
        }
        replaceLayers(layerManager.getLayers(), layerManager.getActiveLayer());
    }

    /**
     * 将项目图层写回图层管理器。
     */
    public void syncLayersTo(LayerManager layerManager) {
        if (layerManager == null) {
            return;
        }

        layerManager.clear();
        for (ILayer sourceLayer : layers) {
            Layer restoredLayer = copyLayer(sourceLayer);
            layerManager.addLayer(restoredLayer);
        }

        if (activeLayer != null) {
            ILayer matchingLayer = findLayerById(layerManager.getLayers(), activeLayer.getId());
            if (matchingLayer != null) {
                layerManager.setActiveLayer(matchingLayer);
                activeLayer = matchingLayer;
            } else if (!layerManager.getLayers().isEmpty()) {
                ILayer fallback = layerManager.getLayers().getFirst();
                layerManager.setActiveLayer(fallback);
                activeLayer = fallback;
            }
        } else if (!layerManager.getLayers().isEmpty()) {
            layerManager.setActiveLayer(layerManager.getLayers().getFirst());
            activeLayer = layerManager.getActiveLayer();
        }
    }

    public String serialize() {
        ProjectSnapshot snapshot = toSnapshot();
        return GSON.toJson(snapshot);
    }

    public static Project deserialize(String data) {
        if (data == null || data.isBlank()) {
            LOGGER.warn("项目反序列化失败：输入为空");
            return new Project(PlotI18n.defaultProjectName());
        }

        try {
            ProjectSnapshot snapshot = GSON.fromJson(data, ProjectSnapshot.class);
            if (snapshot == null) {
                LOGGER.warn("项目反序列化失败：JSON 解析结果为空");
                return new Project(PlotI18n.defaultProjectName());
            }
            return fromSnapshot(snapshot);
        } catch (JsonSyntaxException e) {
            LOGGER.error("项目反序列化失败：JSON 格式无效", e);
            return new Project(PlotI18n.defaultProjectName());
        }
    }

    private ProjectSnapshot toSnapshot() {
        ProjectSnapshot snapshot = new ProjectSnapshot();
        snapshot.formatVersion = ProjectSnapshot.CURRENT_FORMAT_VERSION;
        snapshot.name = name;
        snapshot.id = id;
        snapshot.description = description;
        snapshot.filePath = filePath;
        snapshot.modified = modified;
        snapshot.activeLayerId = activeLayer != null ? activeLayer.getId() : null;
        snapshot.canvas = toCanvasSnapshot(canvasModel);

        for (ILayer layer : layers) {
            snapshot.layers.add(toLayerSnapshot(layer));
        }
        return snapshot;
    }

    private static Project fromSnapshot(ProjectSnapshot snapshot) {
        if (snapshot.formatVersion != ProjectSnapshot.CURRENT_FORMAT_VERSION) {
            LOGGER.warn("项目格式版本不匹配: expected={}, actual={}",
                    ProjectSnapshot.CURRENT_FORMAT_VERSION, snapshot.formatVersion);
        }

        String projectId = snapshot.id != null && !snapshot.id.isBlank()
                ? snapshot.id
                : UUID.randomUUID().toString();
        String projectName = snapshot.name != null && !snapshot.name.isBlank()
                ? snapshot.name
                : PlotI18n.defaultProjectName();

        Project project = new Project(projectId, projectName, false);
        project.description = snapshot.description;
        project.filePath = snapshot.filePath;
        project.modified = snapshot.modified;
        project.canvasModel = fromCanvasSnapshot(snapshot.canvas);

        List<ILayer> restoredLayers = new ArrayList<>();
        RestoreStats restoreStats = new RestoreStats();
        if (snapshot.layers != null) {
            for (ProjectSnapshot.LayerSnapshot layerSnapshot : snapshot.layers) {
                Layer layer = fromLayerSnapshot(layerSnapshot, restoreStats);
                if (layer != null) {
                    restoredLayers.add(layer);
                }
            }
        }

        if (restoreStats.skippedShapes > 0) {
            String message = buildRestoreWarningMessage(restoreStats);
            LOGGER.warn(message);
            EventBus.getInstance().publish(new Events.WarningEvent("Project", message));
        }

        if (restoredLayers.isEmpty()) {
            restoredLayers.add(new Layer(PlotI18n.defaultLayerName()));
        }

        ILayer restoredActiveLayer = findLayerById(restoredLayers, snapshot.activeLayerId);
        project.replaceLayers(restoredLayers, restoredActiveLayer);
        return project;
    }

    private static ProjectSnapshot.CanvasSnapshot toCanvasSnapshot(CanvasModel canvas) {
        if (canvas == null) {
            return null;
        }

        ProjectSnapshot.CanvasSnapshot snapshot = new ProjectSnapshot.CanvasSnapshot();
        snapshot.id = canvas.getId().toString();
        snapshot.name = canvas.getName();
        snapshot.width = canvas.getWidth();
        snapshot.height = canvas.getHeight();
        return snapshot;
    }

    private static CanvasModel fromCanvasSnapshot(ProjectSnapshot.CanvasSnapshot snapshot) {
        if (snapshot == null) {
            return new CanvasModel(PlotI18n.defaultCanvasName(), 800, 600);
        }

        UUID canvasId = snapshot.id != null ? UUID.fromString(snapshot.id) : UUID.randomUUID();
        String canvasName = snapshot.name != null && !snapshot.name.isBlank() ? snapshot.name : PlotI18n.defaultCanvasName();
        int width = snapshot.width > 0 ? snapshot.width : 800;
        int height = snapshot.height > 0 ? snapshot.height : 600;
        return new CanvasModel(canvasId, canvasName, width, height);
    }

    private static ProjectSnapshot.LayerSnapshot toLayerSnapshot(ILayer layer) {
        ProjectSnapshot.LayerSnapshot snapshot = new ProjectSnapshot.LayerSnapshot();
        snapshot.id = layer.getId();
        snapshot.name = layer.getName();
        snapshot.visible = layer.isVisible();
        snapshot.locked = layer.isLocked();
        snapshot.opacity = layer.getOpacity();
        snapshot.zOrder = layer.getZOrder();
        snapshot.color = toColorSnapshot(layer.getColor());
        snapshot.lineStyle = toLineStyleSnapshot(layer.getLineStyle());

        for (Shape shape : layer.getShapes()) {
            if (shape == null || shape.isDeleted()) {
                continue;
            }
            ProjectSnapshot.ShapeSnapshot shapeSnapshot = new ProjectSnapshot.ShapeSnapshot();
            shapeSnapshot.type = ShapeSerialization.getTypeName(shape);
            shapeSnapshot.data = shape.serialize();
            snapshot.shapes.add(shapeSnapshot);
        }
        return snapshot;
    }

    private static Layer fromLayerSnapshot(ProjectSnapshot.LayerSnapshot snapshot, RestoreStats restoreStats) {
        if (snapshot == null) {
            return null;
        }

        String layerId = snapshot.id != null && !snapshot.id.isBlank()
                ? snapshot.id
                : UUID.randomUUID().toString();
        String layerName = snapshot.name != null && !snapshot.name.isBlank()
                ? snapshot.name
                : PlotI18n.fallbackLayerName();

        Layer layer = new Layer(layerId, layerName);
        layer.setVisible(snapshot.visible);
        layer.setLocked(snapshot.locked);
        layer.setOpacity(snapshot.opacity);
        layer.setZOrder(snapshot.zOrder);

        Color color = fromColorSnapshot(snapshot.color);
        if (color != null) {
            layer.setColor(color);
        }

        LineStyle lineStyle = fromLineStyleSnapshot(snapshot.lineStyle);
        if (lineStyle != null) {
            layer.setLineStyle(lineStyle);
        }

        if (snapshot.shapes != null) {
            for (ProjectSnapshot.ShapeSnapshot shapeSnapshot : snapshot.shapes) {
                Shape shape = ShapeSerialization.deserialize(shapeSnapshot.type, shapeSnapshot.data);
                if (shape != null) {
                    layer.addShape(shape);
                } else if (restoreStats != null) {
                    restoreStats.skippedShapes++;
                    if ("AnnotationShape".equals(shapeSnapshot.type)) {
                        restoreStats.skippedAnnotations++;
                    }
                }
            }
        }
        return layer;
    }

    private static String buildRestoreWarningMessage(RestoreStats restoreStats) {
        if (restoreStats.skippedAnnotations > 0 && restoreStats.skippedAnnotations == restoreStats.skippedShapes) {
            return PlotI18n.status("status.plot.project.restore.annotations_only", restoreStats.skippedAnnotations);
        }
        if (restoreStats.skippedAnnotations > 0) {
            return PlotI18n.status("status.plot.project.restore.with_annotations",
                    restoreStats.skippedShapes, restoreStats.skippedAnnotations);
        }
        return PlotI18n.status("status.plot.project.restore.generic", restoreStats.skippedShapes);
    }

    private static final class RestoreStats {
        private int skippedShapes;
        private int skippedAnnotations;
    }

    private static Layer copyLayer(ILayer sourceLayer) {
        ProjectSnapshot.LayerSnapshot snapshot = toLayerSnapshot(sourceLayer);
        return fromLayerSnapshot(snapshot, null);
    }

    private void replaceLayers(List<ILayer> newLayers, ILayer preferredActiveLayer) {
        layers.clear();
        if (newLayers != null) {
            layers.addAll(newLayers);
        }

        if (layers.isEmpty()) {
            ILayer defaultLayer = new Layer(PlotI18n.defaultLayerName());
            layers.add(defaultLayer);
            activeLayer = defaultLayer;
            return;
        }

        if (preferredActiveLayer != null) {
            ILayer matched = findLayerById(layers, preferredActiveLayer.getId());
            activeLayer = matched != null ? matched : layers.getFirst();
        } else {
            activeLayer = layers.getFirst();
        }
    }

    private static ILayer findLayerById(List<ILayer> layerList, String layerId) {
        if (layerList == null || layerId == null || layerId.isBlank()) {
            return null;
        }
        for (ILayer layer : layerList) {
            if (layerId.equals(layer.getId())) {
                return layer;
            }
        }
        return null;
    }

    private static ProjectSnapshot.ColorSnapshot toColorSnapshot(Color color) {
        if (color == null) {
            return null;
        }
        ProjectSnapshot.ColorSnapshot snapshot = new ProjectSnapshot.ColorSnapshot();
        snapshot.r = color.getRed();
        snapshot.g = color.getGreen();
        snapshot.b = color.getBlue();
        snapshot.a = color.getAlpha();
        return snapshot;
    }

    private static Color fromColorSnapshot(ProjectSnapshot.ColorSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new Color(snapshot.r, snapshot.g, snapshot.b, snapshot.a);
    }

    private static ProjectSnapshot.LineStyleSnapshot toLineStyleSnapshot(LineStyle lineStyle) {
        if (lineStyle == null) {
            return null;
        }
        ProjectSnapshot.LineStyleSnapshot snapshot = new ProjectSnapshot.LineStyleSnapshot();
        snapshot.type = lineStyle.getType() != null ? lineStyle.getType().name() : LineStyle.LineType.SOLID.name();
        snapshot.width = lineStyle.getWidth();
        snapshot.visible = lineStyle.isVisible();
        snapshot.color = toColorSnapshot(lineStyle.getColor());
        return snapshot;
    }

    private static LineStyle fromLineStyleSnapshot(ProjectSnapshot.LineStyleSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        LineStyle.LineType lineType = LineStyle.LineType.SOLID;
        if (snapshot.type != null) {
            try {
                lineType = LineStyle.LineType.valueOf(snapshot.type);
            } catch (IllegalArgumentException ignored) {
                LOGGER.warn("未知线型: {}", snapshot.type);
            }
        }

        LineStyle lineStyle = new LineStyle(lineType, snapshot.width > 0 ? snapshot.width : 1.0f);
        lineStyle.setVisible(snapshot.visible);
        Color color = fromColorSnapshot(snapshot.color);
        if (color != null) {
            lineStyle.setColor(color);
        }
        return lineStyle;
    }

    @Override
    public String toString() {
        return String.format("Project[name=%s, layers=%d]", name, layers.size());
    }
}
