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
| **Keybinding** | Press **`P`** in-game (configurable under Controls → Plot) |
| **Item** | Use the **Plot** item from the creative **Tools** tab |

> You must be in a world with an active player to open Plot.

## What Happens When Plot Opens

- Switches to an **orthographic top-down camera**
- Hides the HUD (hotbar, player model) — the game **does not pause**
- The Minecraft world remains visible behind a semi-transparent drawing overlay
- The UI initializes on first open (ImGui)

## Closing Plot

Click the **close button (✕)** in the top-right corner.

> **Note:** `Esc` does **not** close Plot — it cancels operations and clears selections. See [FAQ](06-faq.md).

On close, Plot automatically:
- Restores the perspective camera and HUD
- Clears ghost block previews
- Saves tool configurations

## Five-Minute Tutorial

### 1. Draw a Line

1. Press `L` or select the **Line** tool from the left toolbar
2. Click a start point, then an end point
3. Hold **Shift** to constrain to horizontal/vertical

### 2. Select Shapes

1. Press `Space` for the **Select** tool
2. Drag to box-select, or Shift+click to add to selection
3. Ctrl+click to remove from selection

### 3. Move Shapes

1. With shapes selected, switch to the **Move** tool
2. Click a base point, then a destination

### 4. Undo

- Click **Undo** in the top toolbar, or press `Ctrl+Z`

### 5. Convert to Blocks

1. Select your shapes
2. Open **Block Config**, choose blocks, and apply
3. Click **Line to Block** to generate a ghost preview
4. Click **Project Blocks** to place them in the world

See [Block Building](04-block-building.md) for the full workflow.

## Canvas Navigation

| Action | Function |
|--------|----------|
| **Scroll wheel** | Zoom |
| **Middle-mouse drag** | Pan |
| **View range slider** | Adjust visible range (40–310) |
| **Canvas opacity slider** | Adjust overlay transparency (0–100%) |

## Next Steps

- [Interface Guide](02-interface.md) — full layout overview
- [Tools Reference](03-tools-reference.md) — every tool and mode
- [Settings & Shortcuts](05-settings-shortcuts.md) — customization
