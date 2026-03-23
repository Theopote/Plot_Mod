package com.plot.infrastructure.event.file;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;

/**
 * 文件导入事件
 * 当用户从导入对话框中选择并导入文件时触发
 */
public class FileImportedEvent extends Event {
    private final String filePath;
    private static final String SOURCE = "ImportFileDialog";

    /**
     * 构造函数
     * @param filePath 导入文件的完整路径
     */
    public FileImportedEvent(String filePath) {
        super(EventType.FILE_IMPORT);
        this.filePath = filePath;
    }

    /**
     * 获取导入文件的路径
     * @return 文件路径
     */
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public String toString() {
        return "FileImportedEvent{" +
                "filePath='" + filePath + '\'' +
                ", source='" + SOURCE + '\'' +
                '}';
    }
}