# Settings & Shortcuts

Plot provides extensive customization for shortcuts, snap, grid, and visual feedback.

---

## Opening Settings

Click the **Plot Settings & Help** button on the far left of the top control panel to open **Settings & Help**.

| Tab | Contents |
|-----|----------|
| Shortcuts | Customize tool and edit keybindings |
| Snap & Feedback | Snap marker display and colors |
| Help & Tutorials | 8 in-game chapters aligned with docs/ (getting started, interface, tools, blocks, settings, gallery, plugins, FAQ) |

---

## Shortcuts

### Minecraft Keybinding

Under **Settings -> Controls -> Plot**:

| Action | Default |
|--------|---------|
| Open Plot Interface | `0` |

### In-Plot Shortcuts (Customizable)

Managed in **Settings & Help -> Shortcuts**. Saved to:

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

1. Open **Settings & Help -> Shortcuts**
2. Use the search box to find actions quickly
3. **Double-click** a row or click **Edit** to record
4. Press the desired key combination
5. Click **Done**

| Action | Description |
|--------|-------------|
| **Backspace** | Clear current binding |
| **Esc** | Cancel recording |
| **Reset** | Restore all defaults |

> **Note:** Opening Plot defaults to **`0`**, separate from Free Draw (`P`) inside Plot. Change it under Minecraft Controls if needed.

> **Scope:** Keymap covers common drawing tools and undo/redo only. Tools like spline, polyline, and move have no default shortcuts — activate from the left toolbar.

### Global Shortcuts (Not in Keymap UI)

| Key | Action |
|-----|--------|
| `Ctrl+A` | Select all shapes on visible layers |
| `Delete` | Delete selected shapes |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Esc` | Cancel -> clear selection -> clear ghost blocks |

---

## Snap Settings

### Quick Toggle
- Left-click **Snap** button — global on/off
- Right-click — detailed settings dialog

### Geometry Snap Types

| Type | Description |
|------|-------------|
| Endpoint | Line segment endpoints |
| Midpoint | Line segment midpoints |
| Center | Circle/arc center |
| Centroid | Closed shape centroid |
| Vertex | Polygon vertices |
| Quadrant | 0°/90°/180°/270° on circles/ellipses |
| Grid | Grid intersections |
| Perpendicular | Perpendicular foot |
| Intersection | Shape intersections |
| Nearest | Nearest point on shape |
| Control point | Spline/Bezier control points |
| Tangent | Tangent contact points |

### Relation Constraints

Horizontal, vertical, parallel, extension.

### Other Options

| Option | Description |
|--------|-------------|
| Snap radius | 1–50 px or 0.2–15 mm (Alt toggles unit) |
| Marker size | 2–10 px |
| Snap level | Global / tool / layer |
| Priority | Type-first or distance-first |
| Exclude hidden layers | Do not snap to hidden layers |
| Shift temporary disable | Hold Shift to pause snap |
| Marker animation | Visual feedback on snap hit |

### Snap & Feedback Tab

Toggle marker types, customize colors per snap type, show control points / point indices, reset all colors.

---

## Grid Settings

Right-click the **Grid** button:

| Parameter | Range |
|-----------|-------|
| Grid size | 8–64 |
| Opacity | 0.1–1.0 |
| Line width | 0.5–3.0 |
| Color | Color picker |

Left-click toggles grid visibility (enabled by default).

---

## Ortho Camera Settings

Right-click **Camera Toggle**:

| Parameter | Range |
|-----------|-------|
| Scale | 0.1–10 |
| View distance | 40–600 |
| Near / far planes | Numeric input |
| Reset defaults | One-click restore |

> The **View Range** slider on the control panel controls the same value, range 40–600.

---

## Theme

Top-right system panel: **Dark** / **Light** theme.

---

## Persisted Configuration

| File / location | Contents |
|-----------------|----------|
| `plot/keymap.json` | Keybindings |
| Tool configs | Per-tool modes and parameters (saved on Plot close) |
| `config/plugins/*.json` | Plugin global configs |
| `plot/plugins/` | Plugin project data (road networks, earthwork regions, building projects) |

> **Note:** There is currently **no** canvas layer project save/load UI. Layer content is not persisted as a project file when Plot closes. Command history is session-only. Use the [Gallery](07-gallery.md) to save and reuse shape snippets.
