package com.plot.core.model;

import java.util.UUID;

/**
 * 实体基类，提供基本的实体功能
 */
public abstract class Entity {
    private final String id;
    private String name;
    private boolean visible;
    private boolean locked;
    private boolean selected;
    private boolean highlighted;

    protected Entity() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.visible = true;
        this.locked = false;
        this.selected = false;
        this.highlighted = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;
        Entity entity = (Entity) o;
        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, name=%s]", 
            getClass().getSimpleName(), id, name);
    }
} 