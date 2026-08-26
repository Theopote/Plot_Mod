package com.plot.core.command;

import com.plot.core.context.ApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命令历史灾难场景：指针一致性、事务原子性、失败不污染历史。
 * <p>
 * 覆盖原 CommandManager / CommandHistory 职责（已统一到 {@link CommandService}）。
 */
class CommandServiceTest {

    private CommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = ApplicationContext.getInstance().getCommandService();
        commandService.clear();
    }

    @Test
    void executeFailureDoesNotEnterHistory() {
        assertFalse(commandService.execute(new FailingCommand(true, false, false)));
        assertEquals(0, commandService.size());
        assertEquals(-1, commandService.getCurrentIndex());
        assertFalse(commandService.canUndo());
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
    void newExecuteAfterUndoDiscardsRedoBranch() {
        AtomicCounter counter = new AtomicCounter();
        commandService.execute(new CounterCommand(counter, 1));
        commandService.execute(new CounterCommand(counter, 10));
        assertEquals(11, counter.value);

        assertTrue(commandService.undo());
        assertEquals(1, counter.value);
        assertTrue(commandService.canRedo());
        assertEquals(2, commandService.size());

        commandService.execute(new CounterCommand(counter, 100));
        assertEquals(101, counter.value);
        assertFalse(commandService.canRedo());
        assertEquals(2, commandService.size());
        assertEquals(1, commandService.getCurrentIndex());
    }

    @Test
    void nestedBeginTransactionThrows() {
        commandService.beginTransaction();
        assertThrows(IllegalStateException.class, commandService::beginTransaction);
        commandService.rollbackTransaction();
    }

    @Test
    void failedExecuteInsideTransactionIsNotRecorded() {
        AtomicCounter counter = new AtomicCounter();
        commandService.beginTransaction();
        assertTrue(commandService.execute(new CounterCommand(counter, 1)));
        assertFalse(commandService.execute(new FailingCommand(true, false, false)));
        commandService.commitTransaction();

        assertEquals(1, counter.value);
        assertEquals(1, commandService.size());
        assertFalse(commandService.history().get(0) instanceof CompositeCommand);
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
        assertFalse(commandService.isInTransaction());
    }

    @Test
    void transactionRollbackContinuesWhenUndoFails() {
        AtomicCounter counter = new AtomicCounter();
        commandService.beginTransaction();
        commandService.execute(new CounterCommand(counter, 5));
        commandService.pushExecuted(new FailingCommand(false, true, false));
        commandService.rollbackTransaction();

        // CounterCommand 仍应被撤销；失败 undo 不阻断回滚循环
        assertEquals(0, counter.value);
        assertFalse(commandService.isInTransaction());
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

    @Test
    void clearAbortsOpenTransaction() {
        commandService.beginTransaction();
        commandService.execute(new CounterCommand(new AtomicCounter(), 1));
        commandService.clear();

        assertFalse(commandService.isInTransaction());
        assertEquals(0, commandService.size());
        assertEquals(-1, commandService.getCurrentIndex());
    }

    @Test
    void historyTrimKeepsPointerConsistent() {
        AtomicCounter counter = new AtomicCounter();
        for (int i = 0; i < 120; i++) {
            commandService.execute(new CounterCommand(counter, 1));
        }
        assertEquals(100, commandService.size());
        assertEquals(99, commandService.getCurrentIndex());
        assertTrue(commandService.canUndo());
        assertFalse(commandService.canRedo());
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
