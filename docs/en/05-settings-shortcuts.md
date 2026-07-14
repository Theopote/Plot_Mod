# Settings & Shortcuts

## Opening Settings

Click the **Plot logo** in the top control panel to open **Settings & Help**.

| Tab | Contents |
|-----|----------|
| Shortcuts | Customize tool and edit keybindings |
| Snap & Feedback | Snap marker display and colors |
| Help & Tutorials | Built-in guides |

---

## Shortcuts

### Minecraft Keybinding

Under **Settings → Controls → Plot**:

| Action | Default |
|--------|---------|
| Open Plot Interface | `0` |

### In-Plot Shortcuts (Customizable)

Managed in **Settings & Help → Shortcuts**. Saved to:

```
.minecraft/plot/keymap.json
```

#### Defaults

| Action | Key | Category |
|--------|-----|----------|
| Select | `Space` | Drawing |
| Eraser | `D` | Drawing |
| Line | `L` | Drawing |
| Free Draw | `P` | Drawing |
| Circle | `C` | Drawing |
| Rectangle | `R` | Drawing |
| Ellipse | `E` | Drawing |
| Semicircle | `S` | Drawing |
| Arc | `A` | Drawing |
| Undo | `Ctrl+Z` | Edit |
| Redo | `Ctrl+Y` | Edit |

#### Customizing

1. Open **Settings & Help → Shortcuts**
2. Double-click a row or click **Edit**
3. Press the desired key combination
4. Click **Done**

- **Backspace** — clear binding
- **Esc** — cancel recording
- **Reset defaults** — restore all defaults

> **Note:** Opening Plot defaults to **`0`**, separate from Free Draw (`P`) inside Plot. Change it under Minecraft Controls if needed.

> **Scope:** Keymap covers common drawing tools and undo/redo only. Tools like Spline, Polyline, and Move have no default shortcuts — activate from the toolbar.

### Global Shortcuts (Not in Keymap UI)

| Key | Action |
|-----|--------|
| `Ctrl+A` | Select all shapes on visible layers |
| `Delete` | Delete selected shapes |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Esc` | Cancel → clear selection → clear ghost blocks |

---

## Snap Settings

### Quick Toggle
- Left-click **Snap** button — global on/off
- Right-click — detailed settings dialog

### Geometry Snap Types
Endpoint, midpoint, center, centroid, vertex, quadrant, grid, perpendicular, intersection, nearest, control point, tangent.

### Relation Constraints
Horizontal, vertical, parallel, extension.

### Other Options
Snap radius (px/mm, Alt toggles unit), marker size (2–10 px), snap level (global/tool/layer), priority (type vs distance), exclude hidden layers, Shift to temporarily disable, marker animation.

### Snap & Feedback Tab
Toggle marker types, customize colors per snap type, show control points / point indices, reset all colors.

---

## Grid Settings

Right-click the **Grid** button:

| Parameter | Range |
|-----------|-------|
| Grid size | 8–64 |
| Opacity | Slider |
| Line width | Slider |
| Color | Color picker |

Left-click toggles grid visibility.

---

## Ortho Camera Settings

Right-click **Camera Toggle**:

Scale 0.1–10, view distance 0–100, near/far planes, reset to defaults.

---

## Theme

Top-right system panel: **Dark** / **Light** theme.

---

## Persisted Configuration

| File | Contents |
|------|----------|
| `plot/keymap.json` | Keybindings |
| Tool configs | Saved on Plot close |
| `config/plugins/*.json` | Plugin configs |

> **Note:** There is currently **no** project save/load UI. Canvas content is not persisted as a project file when Plot closes. Command history is session-only.
