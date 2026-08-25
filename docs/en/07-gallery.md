# Gallery

The Gallery panel lets you browse preset shapes, save your own design snippets, and place them onto the canvas. When full project save/load is unavailable, the gallery is a practical way to reuse common graphics.

## Opening the Gallery

Switch to the **Gallery** tab in the right sidebar (alongside **Property** and **Extension**).

## Interface

| Area | Function |
|------|----------|
| Search | Filter entries by name or description |
| Categories | All / Building / Landscape / Shape, plus custom categories |
| Item table | Name, description, action buttons (`+` place, `×` delete) |
| Toolbar | Save selection to gallery, add category |

## Built-in Presets

Presets include:

- **Building**: church, castle, villa, courtyard floor plans
- **Landscape**: pond outline
- **Shape**: rectangle block, circle block, triangle, Voronoi patterns, etc.

Built-in presets are read-only.

## Saving Selected Shapes

1. Select one or more shapes with the Select tool
2. Open the **Gallery** panel
3. Click **Save selection to gallery**
4. Enter name, description, and category in the dialog

Saved entries persist locally across sessions.

## Placing on Canvas

1. Find the entry in the gallery list
2. Click the **`+`** button on that row (tooltip: **Place**)
3. In placement mode, click on the canvas to set the position
4. Press **Esc** or click **Cancel** to exit placement mode

> While gallery placement is active, drawing tools do not receive clicks. The status bar shows the active gallery item name.

> **Note:** Placement position is determined by your canvas click — entries are not automatically placed at the canvas center.

## Managing Categories

| Action | Description |
|--------|-------------|
| Add category | Create a custom category |
| Rename | Via context menu (custom categories only) |
| Delete | Requires confirmation; built-in categories cannot be deleted; entries move to **Building** |

## Deleting Entries

User-saved entries can be removed via the **`×`** delete button in the actions column (with confirmation).

## Tips

- Store reusable structures (door openings, standard rooms, road sections) instead of redrawing
- Combine with layers for organization
- Gallery stores shape data only — block config and projection are separate steps
