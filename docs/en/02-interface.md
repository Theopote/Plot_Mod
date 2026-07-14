# Interface Guide

Plot uses a dockable ImGui panel layout with a full-screen canvas over the live Minecraft world.

## Layout Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  Control Panel (top)                          System Panel (top-right) │
│  Undo/Snap/Blocks/View…                       Theme toggle + Close ✕  │
├──────────┬───────────────────────────────────────┬─────────────┤
│ Tool     │         Canvas (full-screen)           │ Property /  │
│ Panel    │         Draw and edit here             │ Gallery /   │
│ (left)   │                                        │ Extension   │
└──────────┴───────────────────────────────────────┴─────────────┘
```

All panels support **drag-and-drop docking**. The default layout is created on first open.

> **Tip:** Drawing input only works in the central canvas area. Hovering over panels captures mouse input.

---

## Top Control Panel

### Plot Logo
Opens **Settings & Help** — shortcuts, snap feedback, built-in tutorials. See [Settings & Shortcuts](05-settings-shortcuts.md).

### File Tools
| Button | Action |
|--------|--------|
| Undo | Undo last operation |
| Redo | Redo |

### Tool Settings
| Button | Left-click | Right-click |
|--------|------------|-------------|
| **Snap** | Toggle snap | Snap settings dialog |
| **Grid** | Toggle grid | Grid settings dialog |
| **Clear** | Clear entire canvas (undoable) | — |

### Block Operations
| Button | Left-click | Right-click |
|--------|------------|-------------|
| **Block Config** | Open block selection screen | — |
| **Line to Block** | Preview selected shapes as ghost blocks | Line-to-block settings |
| **Project Blocks** | Place ghost blocks in the world | Projection settings |

> **Line to Block** is disabled when nothing is selected.

### View Tools
| Button | Left-click | Right-click |
|--------|------------|-------------|
| **Camera Toggle** | Orthographic ↔ Perspective | Ortho camera settings |
| **Lock View** | Lock/unlock pan and zoom | — |

### Sliders
| Slider | Range | Notes |
|--------|-------|-------|
| View range | 40–310 | Disabled when view is locked |
| Canvas opacity | 0–100% | Overlay transparency over the world |

---

## Left Tool Panel

Tools are grouped with separators:

| Group | Tools |
|-------|-------|
| Basic | Select, Eraser |
| Drawing | Line, Free Draw, Circle, Rectangle, Spline, Ellipse, Semicircle, Arc, Polyline, Polygon |
| Special | Star, Spiral, Catenary, Sine Wave |
| Edit | Move, Rotate, Scale, Mirror, Align, Array, Offset, Break, Fillet, Chamfer, Extend, Trim, Transform |
| Annotation | Text, Annotation |

---

## Right Sidebar (Property / Gallery / Extension)

The right sidebar uses **tabs**:

| Tab | Contents |
|-----|----------|
| **Property** | Tool properties, layers, history, status |
| **Gallery** | Presets, save selection, place on canvas — see [Gallery](07-gallery.md) |
| **Extension** | Built-in plugins — see [Extension Plugins](08-plugins.md) |

### Tool Properties
Current tool name, description, mode options, and usage hints.

### Layers
| Action | Description |
|--------|-------------|
| New layer | Naming dialog |
| Delete | Requires selection; cannot delete last or locked layer |
| Merge | Combine 2+ layers into "Merged Layer" |
| Move up/down | Reorder Z-order |
| Select all | Select all shapes on active layer |

Per-layer controls: visibility, lock, editable name, color/line style, context menu, drag reorder.

### History
Last 30 commands with timestamps. Click to jump to that state.

### Status
Canvas and selection statistics.

---

## Top-Right System Panel

- **Theme toggle** — Dark / Light
- **Close ✕** — Exit Plot

---

## Dialogs

| Dialog | How to open |
|--------|-------------|
| Settings & Help | Click Plot logo |
| Snap settings | Right-click Snap button |
| Grid settings | Right-click Grid button |
| Line to Block settings | Right-click Line to Block button |
| Projection settings | Right-click Project Blocks button |
| Ortho camera settings | Right-click Camera Toggle button |
| Block Config | Left-click Block Config (native Minecraft UI) |

---

## Global Modifier Keys

| Key | Action |
|-----|--------|
| **Shift** | Orthogonal/angle constraints; may temporarily disable snap |
| **Ctrl** | Add/remove from selection; temporary copy in some transform tools |
| **Ctrl+A** | Select all shapes on visible layers |
| **Delete** | Delete selected shapes (undoable) |
| **Esc** | Cancel operation → clear selection → clear ghost blocks |
| **Ctrl+Z** | Undo |
| **Ctrl+Y** / **Ctrl+Shift+Z** | Redo |
