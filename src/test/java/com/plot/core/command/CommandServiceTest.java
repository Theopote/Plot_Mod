package com.plot.core.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandServiceTest {

    private CommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = CommandService.getInstance();
        commandService.clear();
    }

    @Test
    void undoFailureKeepsHistoryPointer() {
        FailingCommand command = new FailingCommand(false, true, false);
        commandService.pushExecuted(command);

        assertTrue(commandService.canUndo());
        assertEquals(0, commandService.getCurrentIndex());

        assertFalse(commandService.undo());

        assertTrue(commandService.canUndo());
        assertEquals(0, commandService.getCurrentIndex());
        assertEquals(1, commandService.size());
    }

    @Test
    void redoFailureKeepsHistoryPointer() {
        FailingCommand command = new FailingCommand(false, false, true);
        commandService.pushExecuted(command);
        assertTrue(commandService.undo());

        assertTrue(commandService.canRedo());
        assertEquals(-1, commandService.getCurrentIndex());

        assertFalse(commandService.redo());

        assertTrue(commandService.canRedo());
        assertEquals(-1, commandService.getCurrentIndex());
    }

    @Test
    void transactionRollbackUndoesExecutedCommands() {
        AtomicCounter counter = new AtomicCounter();
        commandService.beginTransaction();
        commandService.execute(new CounterCommand(counter, 1));
        commandService.execute(new CounterCommand(counter, 2));
        commandService.rollbackTransaction();

        assertEquals(0, counter.value);
        assertEquals(0, commandService.size());
    }

    @Test
    void transactionCommitRecordsCompositeCommand() {
        AtomicCounter counter = new AtomicCounter();
        commandService.beginTransaction();
        commandService.execute(new CounterCommand(counter, 1));
        commandService.execute(new CounterCommand(counter, 2));
        commandService.commitTransaction();

        assertEquals(3, counter.value);
        assertEquals(1, commandService.size());
        assertTrue(commandService.history().get(0) instanceof CompositeCommand);

        commandService.undo();
        assertEquals(0, counter.value);
    }

    private static final class AtomicCounter {
        int value = 0;
    }

    private static final class CounterCommand implements Command {
        private final AtomicCounter counter;
        private final int delta;

        CounterCommand(AtomicCounter counter, int delta) {
            this.counter = counter;
            this.delta = delta;
        }

        @Override
        public void execute() {
            counter.value += delta;
        }

        @Override
        public void undo() {
            counter.value -= delta;
        }

        @Override
        public void redo() {
            execute();
        }

        @Override
        public String getDescription() {
            return "counter";
        }

        @Override
        public String getDetailedDescription() {
            return getDescription();
        }
    }

    private static final class FailingCommand implements Command {
        private final boolean failExecute;
        private final boolean failUndo;
        private final boolean failRedo;

        FailingCommand(boolean failExecute, boolean failUndo, boolean failRedo) {
            this.failExecute = failExecute;
            this.failUndo = failUndo;
            this.failRedo = failRedo;
        }

        @Override
        public void execute() {
            if (failExecute) {
                throw new IllegalStateException("execute failed");
            }
        }

        @Override
        public void undo() {
            if (failUndo) {
                throw new IllegalStateException("undo failed");
            }
        }

        @Override
        public void redo() {
            if (failRedo) {
                throw new IllegalStateException("redo failed");
            }
        }

        @Override
        public String getDescription() {
            return "failing";
        }

        @Override
        public String getDetailedDescription() {
            return getDescription();
        }
    }
}
