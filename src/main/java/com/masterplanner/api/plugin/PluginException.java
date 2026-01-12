package com.masterplanner.api.plugin;

/**
 * 插件相关操作异常
 */
public class PluginException extends Exception {
    
    public PluginException(String message) {
        super(message);
    }
    
    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PluginException(Throwable cause) {
        super(cause);
    }
} 