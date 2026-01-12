package com.masterplanner.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.masterplanner.core.layer.Layer;
import com.masterplanner.api.model.ILayer;

/**
 * 表示一个工程项目
 */
public class Project {
    private String name;
    private final String id;
    private String description;
    private final List<ILayer> layers = new CopyOnWriteArrayList<>();
    private ILayer activeLayer;
    private boolean modified;
    private String filePath;
    private CanvasModel canvasModel;
    
    public Project(String name) {
        this.name = name;
        this.id = UUID.randomUUID().toString();
        this.modified = false;
        this.canvasModel = new CanvasModel("默认画布", 800, 600);
        
        // 创建默认图层
        ILayer defaultLayer = new Layer("默认图层");
        layers.add(defaultLayer);
        activeLayer = defaultLayer;
    }
    
    public Project(String name, String description) {
        this(name);
        this.description = description;
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
    
    /**
     * 添加图层
     */
    public void addLayer(ILayer layer) {
        if (layer != null && !layers.contains(layer)) {
            layers.add(layer);
        }
    }
    
    /**
     * 移除图层
     */
    public void removeLayer(ILayer layer) {
        if (layer != null) {
            layers.remove(layer);
            if (layer == activeLayer) {
                activeLayer = layers.isEmpty() ? null : layers.getFirst();
            }
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
    
    // 项目序列化和反序列化方法
    public String serialize() {
        // TODO: 实现项目序列化
        return "";
    }
    
    public static Project deserialize(String data) {
        // TODO: 实现项目反序列化
        return new Project("未命名项目");
    }
    
    public int getTotalShapes() {
        return layers.stream()
                .mapToInt(layer -> layer.getShapes().size())
                .sum();
    }
    
    public int getTotalBlocks() {
        return layers.stream()
                .mapToInt(layer -> layer.getShapes().stream()
                        .mapToInt(Shape::getBlockCount)
                        .sum())
                .sum();
    }
    
    @Override
    public String toString() {
        return String.format("Project[name=%s, layers=%d]", name, layers.size());
    }
}
