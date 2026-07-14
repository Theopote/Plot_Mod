# Block Building

Plot's core building workflow: draw a design → configure blocks → preview → project into the world.

## Workflow

```
Draw → Select → Block Config → Line to Block (preview) → Projection Settings → Project Blocks
```

---

## Block Config

Native Minecraft UI for choosing construction blocks.

### Opening
Click **Block Config** in the top control panel.

### Interface

| Area | Function |
|------|----------|
| Category sidebar | Browse by block type |
| Search | Filter by name or namespace |
| Block grid | Paginated block list |
| Palette | Selected blocks (max **14** slots) |

### Actions

| Action | Effect |
|--------|--------|
| Left-click block | Add to palette |
| Right-click block | Remove from palette |
| Left-click palette slot | Reorder |
| Apply | Save configuration |
| Cancel | Discard changes |
| Clear | Empty palette |

---

## Line to Block

Converts selected vector shapes into **ghost block previews** (semi-transparent, non-solid).

### Prerequisites
- **Select** one or more shapes first
- **Block Config** recommended

### Settings (right-click the button)

| Option | Description |
|--------|-------------|
| Full conversion | Faithful detail reproduction |
| Simplified conversion | Fewer blocks for large structures |
| Simplification ratio | 0.1–1.0 (simplified mode only) |
| Fill closed shapes | Fill interior vs outline only |

### Steps
1. Select shapes
2. (Optional) Adjust settings via right-click
3. Left-click **Line to Block**
4. Review ghost preview in the world

> Default height is the player's foot level. Press **Esc** to clear previews.

---

## Block Projection

Places ghost block previews as **real blocks**.

### Settings (right-click the button)

| Mode | Description |
|------|-------------|
| Project to ground | Snap to terrain surface (default) |
| Project to elevation | Fixed Y coordinate (-64 to 320) |

### Steps
1. Ensure ghost blocks exist (run Line to Block first)
2. (Optional) Adjust projection settings
3. Left-click **Project Blocks**

> Projection is **undoable** via `Ctrl+Z`.

---

## Example: Building a Wall

1. **Draw** — Rectangle tool (`R`), draw wall outline
2. **Select** — `Space`, box-select the rectangle
3. **Block Config** — Add "Stone Bricks" to palette, apply
4. **Preview** — Line to Block
5. **Adjust** — If height is wrong, set projection elevation
6. **Place** — Project Blocks
7. **Undo** if needed — `Ctrl+Z`

---

## Ghost Blocks

| Property | Description |
|----------|-------------|
| Appearance | Semi-transparent |
| Physics | Non-solid, no collision |
| Clear | Esc, close Plot, or re-run Line to Block |
| Requirement | Must exist before projection |

---

## Related

- Reuse saved shapes → [Gallery](07-gallery.md)
- Roads, earthwork, buildings → [Extension Plugins](08-plugins.md)
