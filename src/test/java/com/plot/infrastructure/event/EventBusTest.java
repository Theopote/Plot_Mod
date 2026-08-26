package com.plot.infrastructure.event;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    private EventBus bus;

    static final class TestEvent extends Event {
        TestEvent() {
            super(EventType.WARNING);
        }

        @Override
        public String getSource() {
            return "EventBusTest";
        }
    }

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clear();
    }

    @AfterEach
    void tearDown() {
        bus.clear();
    }

    @Test
    void duplicateSubscribeIsIgnored() {
        AtomicInteger count = new AtomicInteger();
        EventListener listener = e -> count.incrementAndGet();

        bus.subscribe(TestEvent.class, listener);
        bus.subscribe(TestEvent.class, listener);

        bus.publish(new TestEvent());
        assertEquals(1, count.get());
    }

    @Test
    void tokenUnsubscribesReliably() {
        AtomicInteger count = new AtomicInteger();
        EventListener listener = e -> count.incrementAndGet();

        SubscriptionToken token = bus.subscribe(TestEvent.class, listener);
        assertTrue(token.isActive());
        token.unsubscribe();
        assertFalse(token.isActive());

        bus.publish(new TestEvent());
        assertEquals(0, count.get());
    }

    @Test
    void unsubscribeOwnerRemovesAll() {
        Object owner = new Object();
        AtomicInteger count = new AtomicInteger();

        bus.subscribe(owner, TestEvent.class, e -> count.incrementAndGet());
        bus.subscribe(owner, TestEvent.class, e -> count.incrementAndGet());
        bus.unsubscribeOwner(owner);

        bus.publish(new TestEvent());
        assertEquals(0, count.get());
    }
}
