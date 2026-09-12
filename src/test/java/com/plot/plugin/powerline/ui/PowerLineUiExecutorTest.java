package com.plot.plugin.powerline.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineUiExecutorTest {

    @Test
    void runsInlineWhenClientUnavailable() {
        AtomicBoolean ran = new AtomicBoolean();
        PowerLineUiExecutor.runOnClientThread(() -> ran.set(true));
        assertTrue(ran.get());
    }
}
