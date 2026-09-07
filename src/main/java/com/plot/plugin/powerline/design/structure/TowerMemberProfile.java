package com.plot.plugin.powerline.design.structure;

/** 杆件截面厚度（体素膨胀）。 */
public class TowerMemberProfile {
    private int thickness = 1;

    public TowerMemberProfile() {
    }

    public TowerMemberProfile(int thickness) {
        setThickness(thickness);
    }

    public int getThickness() {
        return thickness;
    }

    public void setThickness(int thickness) {
        this.thickness = Math.max(1, Math.min(2, thickness));
    }

    public TowerMemberProfile copy() {
        TowerMemberProfile copy = new TowerMemberProfile();
        copy.thickness = thickness;
        return copy;
    }
}
