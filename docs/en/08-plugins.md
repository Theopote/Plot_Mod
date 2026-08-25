# Extension Plugins

Plot includes several built-in plugins accessible from the **Extension** tab in the right sidebar.

## Opening the Extension Panel

1. Switch to the **Extension** tab on the right
2. Click a plugin icon in the top bar (click again to deselect)
3. Check **Enable plugin** to show that plugin's settings and controls

> Plugins may be disabled by default — enable them before use.

## Installed Plugins

| Plugin | Description |
|--------|-------------|
| **Earthwork Balance** | Cut/fill planning and grading optimization |
| **Road System** | Plan and generate roads along paths |
| **Building Generator** | Generate buildings from polygon or rectangle footprints |

All plugin panels use **Overview / Adopt / Edit / Generate** tabs to organize the workflow.

---

## Road System

### Typical Workflow

1. Draw road centerlines with polyline, spline, or similar tools
2. Open **Extension** -> **Road System** -> enable the plugin
3. Use the **Adopt** tab to claim paths as roads
4. Use **Edit** to set width, cross-section, materials, and node elevation
5. Use **Generate** to preview ghost blocks, then project to the world

### Features

- Presets (urban main/secondary roads, rural roads, highways)
- Width, materials, slope and bridge/tunnel thresholds
- Sidewalks, shoulders, drainage, streetlights
- Junction markings (auto / force on / force off)
- Cut/fill estimates and preview building

### Property Panel Integration

When a road node is selected, the **Property** tab shows an extra **Road Node Properties** section for editing node elevation.

### Configuration

- Global config: `.minecraft/config/plugins/road_system.json`
- Project data: `.minecraft/plot/plugins/road_system/networks/`

---

## Earthwork Balance

### Typical Workflow

1. Draw or select a closed region for grading
2. Open **Extension** -> **Earthwork Balance** -> enable
3. Use the **Adopt** tab to pick/claim regions (including three-point pick)
4. Use **Edit** to set grading mode and material factors
5. Use **Generate** to preview cut/fill and build

### Grading Surface Modes

| Mode | Description |
|------|-------------|
| FLAT | Uniform elevation |
| FIXED_SLOPE | Fixed slope |
| THREE_POINT | Plane from three points |
| FIT_SLOPE | Fit slope from region data |

### Features

- Multiple grading surface modes
- Cut/fill materials and factors
- Region list with plugin-local undo/redo
- Ghost preview (cut: red, fill: light blue)

### Data

- Global config: `.minecraft/config/plugins/earthwork_balance.json`
- Project data: `.minecraft/plot/plugins/earthwork_balance/projects/`

---

## Building Generator

### Typical Workflow

1. Draw a building footprint (rectangle or polygon)
2. Open **Extension** -> **Building Generator** -> enable
3. Pick/adopt the footprint in **Adopt**
4. Configure floors, height, foundation fill, and roof in **Edit**
5. Generate preview and project in **Generate**

### Features

- 2D footprint -> 3D building volume
- Multi-building project management
- Ghost preview and block placement

### Data

- Project data: `.minecraft/plot/plugins/building/projects/`

---

## General Plugin Notes

### Pick Modes

Earthwork, building, and road adopt operations temporarily capture canvas clicks. Do not switch tools during pick mode; press **Esc** to cancel. Watch the status bar.

### Conflicts with Drawing Tools

Like gallery placement, plugin pick modes take priority over normal tool input.

### Plugin Data vs Canvas Project

Each plugin saves project data under `.minecraft/plot/plugins/` (JSON). Plugin settings are in `config/plugins/`.

This is separate from the main canvas project file. There is currently no unified save/load UI for canvas layers, but plugin data persists automatically.

---

## FAQ

### Extension panel empty or unresponsive

Ensure **Enable plugin** is checked and you have clicked the plugin icon in the top bar.

### Region pick mode not responding

Pick mode temporarily captures canvas clicks. Press **Esc** to cancel.

### Conflicts with drawing tools

Earthwork/building region pick, gallery placement, and road path adopt all capture canvas input in special modes. Watch the status bar.
