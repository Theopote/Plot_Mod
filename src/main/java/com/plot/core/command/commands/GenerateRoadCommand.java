package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.utils.PlotI18n;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 道路落地命令（支持撤销/重做）
 */
public class GenerateRoadCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateRoadCommand.class);

    public static class BlockRecord {
        public final BlockPos pos;
        public final String previousBlockId;
        public final String newBlockId;

        public BlockRecord(BlockPos pos, String previousBlockId, String newBlockId) {
            this.pos = pos;
            this.previousBlockId = previousBlockId;
            this.newBlockId = newBlockId;
        }
    }

    private final List<BlockRecord> records;
    private final Date timestamp;
    private final BlockWriter blockWriter;

    @FunctionalInterface
    interface BlockWriter {
        boolean setBlockAt(BlockPos pos, String blockId);
    }

    public GenerateRoadCommand(List<BlockRecord> records) {
        this(records, BlockProjectionHandler.getInstance()::setBlockAt);
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter) {
        this.records = records != null ? new ArrayList<>(records) : new ArrayList<>();
        this.timestamp = new Date();
        this.blockWriter = blockWriter;
    }

    @Override
    public void execute() {
        int success = 0;
        for (BlockRecord record : records) {
            if (blockWriter.setBlockAt(record.pos, record.newBlockId)) {
                success++;
            }
        }
        LOGGER.info("道路落地完成: {}/{}", success, records.size());
    }

    @Override
    public void undo() {
        int restored = 0;
        for (int i = records.size() - 1; i >= 0; i--) {
            BlockRecord record = records.get(i);
            if (blockWriter.setBlockAt(record.pos, record.previousBlockId)) {
                restored++;
            }
        }
        LOGGER.info("道路撤销完成: {}/{}", restored, records.size());
    }

    @Override
    public void redo() {
        execute();
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("plugin.road.history.generate", records.size());
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("plugin.road.history.generate.detail", records.size());
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public int getRecordCount() {
        return records.size();
    }
}
