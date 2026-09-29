package com.plot.plugin.road.manager;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetworkChangeKindNotificationTest {

    private RoadNetworkManager manager;
    private final AtomicReference<RoadChangeKind> receivedKind = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        manager.setOnNetworkChanged(receivedKind::set);
    }

    @Test
    void pushHistoryVerticalProfileNotifiesVerticalProfileKind() {
        manager.pushHistory(RoadChangeKind.VERTICAL_PROFILE);
        assertEquals(RoadChangeKind.VERTICAL_PROFILE, receivedKind.get());
    }

    @Test
    void pushHistoryJunctionNotifiesJunctionKind() {
        manager.pushHistory(RoadChangeKind.JUNCTION);
        assertEquals(RoadChangeKind.JUNCTION, receivedKind.get());
    }

    @Test
    void beginFinishNetworkEditNotifiesVerticalProfileKind() {
        manager.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
        manager.finishNetworkEdit();
        assertEquals(RoadChangeKind.VERTICAL_PROFILE, receivedKind.get());
    }

    @Test
    void defaultPushHistoryNotifiesGeneralKind() {
        manager.pushHistory();
        assertEquals(RoadChangeKind.GENERAL, receivedKind.get());
    }
}
