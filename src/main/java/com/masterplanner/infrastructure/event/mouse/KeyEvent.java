package com.masterplanner.infrastructure.event.mouse;

/**
 * 键盘事件类
 */
public class KeyEvent {
    public enum Type {
        PRESSED,
        RELEASED,
        TYPED
    }

    private final Type type;
    private final int keyCode;
    private final boolean isShiftDown;
    private final boolean isControlDown;
    private final boolean isAltDown;
    
    public KeyEvent(Type type, int keyCode, boolean isShiftDown, boolean isControlDown, boolean isAltDown) {
        this.type = type;
        this.keyCode = keyCode;
        this.isShiftDown = isShiftDown;
        this.isControlDown = isControlDown;
        this.isAltDown = isAltDown;
    }
    
    public Type getType() {
        return type;
    }
    
    public int getKeyCode() {
        return keyCode;
    }
    
    public boolean isShiftDown() {
        return isShiftDown;
    }
    
    public boolean isControlDown() {
        return isControlDown;
    }
    
    public boolean isAltDown() {
        return isAltDown;
    }
} 