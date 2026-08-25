# Getting Started

Plot is a Minecraft Fabric mod that brings CAD-style 2D planning tools into the game. Draw geometry in a top-down orthographic view, then project your design as blocks directly into the world.

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.10 |
| Fabric Loader | ≥ 0.18.4 |
| Fabric API | Latest compatible release |
| Java | ≥ 21 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.10
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods` folder
3. Place the Plot mod JAR in `.minecraft/mods`
4. Launch the game

## Opening Plot

| Method | Action |
|--------|--------|
| **Keybinding** | Press **`0`** in-game (configurable under **Settings -> Controls -> Plot**) |
| **Item** | Use the **Plot** item from the creative **Tools** tab (right-click) |

> You must be in a world with an active player to open Plot.

## What Happens When Plot Opens

- Switches to an **orthographic top-down camera**
- Hides the HUD (hotbar, player model) — the game **does not pause**
- The Minecraft world remains visible behind a semi-transparent drawing overlay
- The UI initializes on first open (ImGui) and creates the default dock layout

## Closing Plot

Click the **close button** in the top-right corner (system panel, icon button).

> **Note:** `Esc` does **not** close Plot — it cancels operations, clears selection, or clears ghost blocks. See [FAQ](06-faq.md).

On close, Plot automatically:
- Restores the perspective camera and HUD
- Clears ghost block previews
- Saves tool configurations and keybindings

## Five-Minute Tutorial

### 1. Draw a Line

1. Press `L` or select the **Line** tool from the left toolbar
2. Click a start point, then an end point in the central canvas area
3. Hold **Shift** to constrain to horizontal/vertical

### 2. Select Shapes

1. Press `Space` for the **Select** tool
2. Drag to box-select (left->right: window select; right->left: cross select)
3. Hold **Ctrl** while clicking or box-selecting to add or remove from selection

> The Select tool does **not** move shapes. Use the Move tool for that.

### 3. Move Shapes

1. With shapes selected, switch to the **Move** tool
2. Click a base point, then a destination (or drag directly)
3. Hold **Shift** to constrain to horizontal/vertical

### 4. Undo

- Click **Undo** on the top control panel, or press `Ctrl+Z` (redo: `Ctrl+Y` or `Ctrl+Shift+Z`)

### 5. Convert to Blocks

1. Select your shapes
2. Open **Block Config**, choose blocks, and apply
3. Click **Line to Block** to generate a ghost preview
4. Click **Project Blocks** to place them in the world

See [Block Building](04-block-building.md) for the full workflow.

## Canvas Navigation

| Action | Function |
|--------|----------|
| **View range slider** | Adjust visible range (40–600); disabled when view is locked |
| **Middle-mouse drag** | Pan the canvas (disabled when view is locked) |
| **Canvas opacity slider** | Adjust overlay transparency (0–100%) |
| **Lock view** | When locked, pan and view range adjustment are disabled |

> **Note:** The scroll wheel **does not** zoom the view. Use the **View Range** slider on the control panel to zoom.

## Global Shortcuts

| Key | Action |
|-----|--------|
| `Ctrl+A` | Select all shapes on visible layers |
| `Delete` | Delete selected shapes |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Esc` | Cancel operation -> clear selection -> clear ghost blocks |

## Next Steps

- [Interface Guide](02-interface.md) — full layout overview
- [Tools Reference](03-tools-reference.md) — every tool and mode
- [Gallery](07-gallery.md) — save and reuse shapes
- [Settings & Shortcuts](05-settings-shortcuts.md) — customization
