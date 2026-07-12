package com.plot.plugin.road.model.section;

/**
 * 排水沟横断面组件。
 */
public class Drain {
    private Boolean enabled;

    public Drain() {
    }

    public Drain(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    Drain copy() {
        return new Drain(enabled);
    }
}
