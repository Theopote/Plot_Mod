package com.plot.plugin.powerline.geometry;

import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.model.PowerPoleSite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 生成后的线路几何中间表示（供工程分析与预览）。 */
public class PowerLineGeometryModel {
    private final List<PowerPoleSite> sites = new ArrayList<>();
    private final List<PolePlacement> placements = new ArrayList<>();
    private final List<ConductorSpanGeometry> conductorSpans = new ArrayList<>();

    public List<PowerPoleSite> getSites() {
        return Collections.unmodifiableList(sites);
    }

    public void setSites(List<PowerPoleSite> sites) {
        this.sites.clear();
        if (sites != null) {
            for (PowerPoleSite site : sites) {
                if (site != null) {
                    this.sites.add(site.copy());
                }
            }
        }
    }

    public List<PolePlacement> getPlacements() {
        return Collections.unmodifiableList(placements);
    }

    public void setPlacements(List<PolePlacement> placements) {
        this.placements.clear();
        if (placements != null) {
            this.placements.addAll(placements);
        }
    }

    public List<ConductorSpanGeometry> getConductorSpans() {
        return Collections.unmodifiableList(conductorSpans);
    }

    public void addConductorSpan(ConductorSpanGeometry span) {
        if (span != null) {
            conductorSpans.add(span);
        }
    }

    public static PowerLineGeometryModel from(
            List<PowerPoleSite> sites,
            List<PolePlacement> placements,
            List<ConductorSpanGeometry> spans) {
        PowerLineGeometryModel model = new PowerLineGeometryModel();
        model.setSites(sites);
        model.setPlacements(placements);
        if (spans != null) {
            for (ConductorSpanGeometry span : spans) {
                model.addConductorSpan(span);
            }
        }
        return model;
    }
}
