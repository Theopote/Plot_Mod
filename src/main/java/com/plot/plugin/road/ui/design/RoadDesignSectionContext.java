package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;

import java.util.function.Consumer;

/** Shared render context for advanced-design sections. */
public final class RoadDesignSectionContext {
    public final RoadUiContext ui;
    public final RoadNetwork network;
    public final Road road;
    public final RoadEdge primaryEdge;
    private ChainageDisplayMode chainageDisplayMode;
    private final Consumer<ChainageDisplayMode> onChainageModeChanged;

    public RoadDesignSectionContext(
            RoadUiContext ui,
            RoadNetwork network,
            Road road,
            RoadEdge primaryEdge,
            ChainageDisplayMode chainageDisplayMode,
            Consumer<ChainageDisplayMode> onChainageModeChanged) {
        this.ui = ui;
        this.network = network;
        this.road = road;
        this.primaryEdge = primaryEdge;
        this.chainageDisplayMode = chainageDisplayMode;
        this.onChainageModeChanged = onChainageModeChanged;
    }

    public ChainageDisplayContext chainageDisplay() {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            chainageDisplayMode,
            RoadStationFormat.DISTANCE_METERS);
    }

    public boolean chainageDisplayFromEnd() {
        return chainageDisplayMode == ChainageDisplayMode.FROM_END;
    }

    public void setChainageDisplayFromEnd(boolean fromEnd) {
        ChainageDisplayMode next = fromEnd
            ? ChainageDisplayMode.FROM_END
            : ChainageDisplayMode.FROM_START;
        if (next == chainageDisplayMode) {
            return;
        }
        chainageDisplayMode = next;
        if (onChainageModeChanged != null) {
            onChainageModeChanged.accept(next);
        }
    }
}
