package com.masterplanner.core.model;

import java.util.UUID;

/**
 * 图元基础类
 * 表示图层中的各种图形元素
 */
public class Element {
    /** 元素唯一标识符 */
    private final String id;
    
    /** 元素类型 */
    private ElementType type;
    
    /** 元素名称 */
    private String name;
    
    /** 是否可见 */
    private boolean visible = true;
    
    /** 是否锁定 */
    private boolean locked = false;
    
    /** 是否选中 */
    private boolean selected = false;
    
    /**
     * 默认构造函数
     * 自动生成唯一ID
     */
    public Element() {
        this.id = UUID.randomUUID().toString();
        this.type = ElementType.UNKNOWN;
        this.name = "元素";
    }
    
    /**
     * 带类型的构造函数
     * @param type 元素类型
     */
    public Element(ElementType type) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.name = type.getDisplayName();
    }
    
    /**
     * 带类型和名称的构造函数
     * @param type 元素类型
     * @param name 元素名称
     */
    public Element(ElementType type, String name) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.name = name;
    }
    
    /**
     * 获取元素ID
     * @return 元素ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取元素类型
     * @return 元素类型
     */
    public ElementType getType() {
        return type;
    }
    
    /**
     * 设置元素类型
     * @param type 元素类型
     */
    public void setType(ElementType type) {
        this.type = type;
    }
    
    /**
     * 获取元素名称
     * @return 元素名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置元素名称
     * @param name 元素名称
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 检查元素是否可见
     * @return 是否可见
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * 设置元素可见性
     * @param visible 是否可见
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 检查元素是否锁定
     * @return 是否锁定
     */
    public boolean isLocked() {
        return locked;
    }
    
    /**
     * 设置元素锁定状态
     * @param locked 是否锁定
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
    
    /**
     * 检查元素是否被选中
     * @return 是否被选中
     */
    public boolean isSelected() {
        return selected;
    }
    
    /**
     * 设置元素选中状态
     * @param selected 是否选中
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    /**
     * 复制元素
     * @return 元素的副本
     */
    public Element copy() {
        Element copy = new Element(this.type, this.name);
        copy.setVisible(this.visible);
        copy.setLocked(this.locked);
        copy.setSelected(this.selected);
        return copy;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Element element = (Element) obj;
        return id.equals(element.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "Element{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", visible=" + visible +
                ", locked=" + locked +
                ", selected=" + selected +
                '}';
    }
} 